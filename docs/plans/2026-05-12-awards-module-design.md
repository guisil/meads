# Awards Module — Design

**Status:** Design (not yet implemented)
**Date:** 2026-05-12
**Depends on:** judging, competition, entry, identity
**Reference:** `docs/reference/chip-competition-rules.md`, `docs/plans/2026-05-05-judging-module-design.md`
**Supersedes:** `docs/specs/awards.md` (which was written before the judging module was fully
specified and is now obsolete — judging owns medals, BOS, and Round-1 totals)

---

## 1. Overview

The awards module owns the **publication lifecycle** for division results: making
results visible to entrants and the public, sending announcement emails, and providing
read-only views of the judging data.

It does **not** own medals, BOS placements, or scores — those live in the judging
module's `MedalAward`, `BosPlacement`, and `Scoresheet` aggregates. Awards is purely a
presentation + visibility-gate + audit layer on top of judging data.

The module follows the "freeze in place" snapshot pattern: when results are
published, the underlying judging data is *frozen* (judging mutators reject edits
based on `division.status = RESULTS_PUBLISHED`). Awards owns a small `Publication`
aggregate that records the audit trail (when, by whom, with what justification).

### 1.1 Design decisions (from brainstorming session 2026-05-12)

- **Snapshot via freeze-in-place + audit aggregate** (not snapshot via data copy).
  Judging data is locked once `division.status = RESULTS_PUBLISHED`; awards owns
  only `Publication` records (publish/republish audit).
- **Entrant view content:** per entry — Round 1 total, advanced-to-medal-round flag,
  medal, BOS place; drill-in to full anonymized scoresheet (per-field scores,
  comments).
- **Withheld medals** (judging `medal=null`) render as `—` to entrants (same as
  no-medal). Admin view distinguishes withheld vs unset.
- **Judge anonymity:** entrant and public views fully anonymize judges (`Judge 1`,
  `Judge 2`, …) with stable per-entry ordering by `submittedAt`. Admin view shows
  judge names + certifications.
- **Publish, Republish, and Send Announcement are decoupled actions.** Neither
  publish nor republish sends emails automatically. The Send Announcement button is
  the only path that emails entrants. This gives admin full control over timing and
  message content. After republish, the admin view shows a non-blocking reminder
  banner suggesting to send an announcement.
- **Republish requires a justification** (non-blank, persisted on the new
  `Publication` row). The justification is the default email body when the admin
  sends an announcement after republishing.
- **Republish reach:** announcement goes to **all entrants in the division** (not
  only those whose results changed).
- **v1 scope includes:** public anonymous results page, per-entry anonymized
  scoresheet PDF download (entrant + admin), admin results view with publish
  controls.
- **v1 scope excludes:** medal-winner certificate PDFs (deferred — first competition
  prints these manually); per-entrant tie-breaking analytics.
- **Public results layout:** per-category sections listing medal winners (mead name
  + meadery name only; no entry IDs). BOS leaderboard at top (mead name + meadery,
  no category column). Withheld medals not listed.
- **DELIBERATION status:** silent to entrants (no heads-up banner). Only the
  `RESULTS_PUBLISHED` transition signals to entrants that anything has happened.
- **Versioning:** `Publication.version` is internal metadata. Initial-publish email
  copy does not mention any version number. Republish email copy uses the
  justification text as the body without leading with a version number.

---

## 2. Module Structure

**Package:** `app.meads.awards`

```java
@ApplicationModule(allowedDependencies = {"judging", "competition", "entry", "identity"})
package app.meads.awards;
```

**Layout:**

```
app.meads.awards/
├── package-info.java
├── Publication.java                       ← JPA entity, aggregate root (public API)
├── AwardsService.java                     ← Service interface (public API)
├── ResultsPublishedEvent.java             ← record (public API)
├── ResultsRepublishedEvent.java           ← record (public API)
├── AnnouncementSentEvent.java             ← record (public API)
├── EntrantResultRow.java                  ← DTO record (public API)
├── AdminResultsView.java                  ← DTO record (public API)
├── PublicResultsView.java                 ← DTO record (public API)
├── AnonymizedScoresheetView.java          ← DTO record (public API)
└── internal/
    ├── PublicationRepository.java         ← JPA repository
    ├── AwardsServiceImpl.java             ← Service implementation
    ├── AwardsAdminView.java               ← Admin view (Vaadin)
    ├── AwardsPublicResultsView.java       ← Public view (Vaadin)
    └── MyScoresheetView.java              ← Entrant scoresheet drill-in (Vaadin)
```

Cross-module touches:
- `judging` module: new `ScoresheetPdfService` (public API), guard lines added to
  ~10–15 mutating service methods.
- `competition` module: new `DivisionStatus.isResultsFrozen()` enum helper.
- `entry` module: extend `MyEntriesView` with results columns + scoresheet link.
- `entry` module: add `EntryRepository.findEntrantIdsByDivisionId()` derived query
  (already used by submission/credit listeners; verify existence or extract).

---

## 3. Entities

### 3.1 Publication (aggregate root)

| Field | Type | Constraints |
|-------|------|-------------|
| `id` | `UUID` | PK, self-generated in constructor |
| `divisionId` | `UUID` | NOT NULL (references `divisions.id`) |
| `version` | `int` | NOT NULL, `>= 1`, sequential per division |
| `publishedAt` | `Instant` | NOT NULL, `@PrePersist` |
| `publishedBy` | `UUID` | NOT NULL (references `users.id`) |
| `justification` | `String` (`TEXT`) | nullable when `version = 1`; required (non-blank) when `version > 1` |
| `isInitial` | `boolean` | NOT NULL — denormalized convenience flag, true iff `version = 1` |

**Constraints:**
- `UNIQUE(divisionId, version)`
- `version >= 1`

**Domain methods:**
- Constructor for initial publication: `Publication(UUID divisionId, UUID publishedBy)` —
  sets `version=1`, `isInitial=true`, `justification=null`.
- Static factory for republish: `Publication.republish(UUID divisionId, int previousVersion,
  String justification, UUID publishedBy)` — sets `version = previousVersion + 1`,
  `isInitial=false`, validates `justification` non-blank, otherwise throws
  `IllegalArgumentException`.
- No mutators — `Publication` rows are append-only (audit log).

**Schema migration: V28**

```sql
-- V28__create_publications.sql
CREATE TABLE publications (
    id UUID PRIMARY KEY,
    division_id UUID NOT NULL REFERENCES divisions(id),
    version INT NOT NULL CHECK (version >= 1),
    published_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_by UUID NOT NULL REFERENCES users(id),
    justification TEXT,
    is_initial BOOLEAN NOT NULL,
    UNIQUE (division_id, version)
);

CREATE INDEX idx_publications_division_id ON publications(division_id);
```

(V28 is the next available version after the judging module's V20–V27 on
`feature/judging-module`.)

---

## 4. Service API

### 4.1 AwardsService (public interface)

**State-changing operations:**

```java
Publication publish(UUID divisionId, UUID adminUserId);
Publication republish(UUID divisionId, String justification, UUID adminUserId);
void sendAnnouncement(UUID divisionId, String customMessage, UUID adminUserId);
```

**Read operations:**

```java
Optional<Publication> getLatestPublication(UUID divisionId);
List<Publication> getPublicationHistory(UUID divisionId);
List<EntrantResultRow> getResultsForEntrant(UUID userId, UUID divisionId);
AdminResultsView getResultsForAdmin(UUID divisionId, UUID adminUserId);
PublicResultsView getPublicResults(String competitionShortName, String divisionShortName);
AnonymizedScoresheetView getAnonymizedScoresheet(UUID scoresheetId, UUID requestingUserId);
```

**`publish` semantics:**
- Authorization: `competitionService.isAuthorizedForDivision(adminUserId, divisionId)`
  (SYSTEM_ADMIN or competition ADMIN).
- Precondition: `division.status = DELIBERATION` (medal rounds complete, BOS complete).
- Inserts `Publication(version=1, isInitial=true, justification=null)`.
- Advances `division.status` to `RESULTS_PUBLISHED` via existing
  `competitionService.advanceDivisionStatus(divisionId, adminUserId)`.
- Publishes `ResultsPublishedEvent`.
- Does **not** send any email.

**`republish` semantics:**
- Authorization: same as `publish`.
- Precondition: `division.status = RESULTS_PUBLISHED`.
- Validates `justification` non-blank (min length 20, max 1000 — enforced in service
  layer; entity only checks non-blank).
- Inserts `Publication(version=N+1, isInitial=false, justification=...)`.
- Does **not** change division status (status stays `RESULTS_PUBLISHED`).
- Publishes `ResultsRepublishedEvent`.
- Does **not** send any email.

**`sendAnnouncement` semantics:**
- Authorization: same as `publish`.
- Precondition: `division.status = RESULTS_PUBLISHED` and at least one `Publication`
  row exists for the division.
- Resolves all distinct entrant `userId`s for the division
  (via `entryRepository.findEntrantIdsByDivisionId`).
- For each entrant, sends an email in their `preferredLanguage` locale via
  `EmailService`. Template selection:
  - `customMessage` non-blank → `custom-announcement.html`
  - `customMessage` blank + latest publication `version = 1` → `results-published.html`
  - `customMessage` blank + latest publication `version > 1` →
    `results-republished.html` (renders `latestPublication.justification` as body)
- Admin-triggered, so bypasses per-user email rate limit (same convention as
  `OrderReviewNotificationListener`). Daily counter still applies.
- Publishes `AnnouncementSentEvent`.

**Revert flow:** No method on `AwardsService`. Admin reverts via the awards-admin
view, which calls `competitionService.revertDivisionStatus(divisionId, adminUserId)`
directly. Existing `DivisionRevertGuard` chain runs (no new guard needed —
allowing the revert is exactly what enables corrections). Prior `Publication` rows
are preserved (audit history).

### 4.2 Constructor signature

```java
AwardsServiceImpl(PublicationRepository publicationRepository,
                  CompetitionService competitionService,
                  EntryService entryService,
                  EntryRepository entryRepository,        // for entrant list lookup
                  JudgingService judgingService,           // for MedalAward + BosPlacement reads
                  ScoresheetService scoresheetService,     // for scoresheet reads
                  UserService userService,
                  EmailService emailService,
                  ApplicationEventPublisher eventPublisher)
```

(Package-private per project convention; exact constructor visibility decided at
implementation time.)

---

## 5. Events

All in `app.meads.awards` public API as records:

```java
public record ResultsPublishedEvent(
    UUID divisionId, UUID publicationId, int version,
    Instant publishedAt, UUID publishedBy) {}

public record ResultsRepublishedEvent(
    UUID divisionId, UUID publicationId, int version,
    Instant publishedAt, UUID publishedBy, String justification) {}

public record AnnouncementSentEvent(
    UUID divisionId, UUID publicationId, int recipientCount,
    boolean usedCustomMessage) {}
```

No event listeners are required for v1. Events exist for observability and future
consumers (e.g., a future analytics dashboard, integrations).

---

## 6. Cross-Module Editing Freeze (Freeze-2)

**Enum helper** (competition module):

```java
public enum DivisionStatus {
    // ... existing values ...

    public boolean isResultsFrozen() {
        return this == RESULTS_PUBLISHED;
    }

    // ... existing helpers (allowsRegistrationActions, allowsJudgingCategoryManagement) ...
}
```

**Guard usage** (judging module, ~10–15 mutating methods):

Every mutating method in `JudgingService` and `ScoresheetService` that accepts a
`divisionId` (or can resolve one from its arguments) adds:

```java
Division division = competitionService.findDivisionById(divisionId);
if (division.getStatus().isResultsFrozen()) {
    throw new BusinessRuleException("error.judging.results-published-frozen");
}
```

Methods that need this guard (audit during implementation):
- `JudgingService`: `createTable`, `updateTableName`, `updateTableScheduledDate`,
  `deleteTable`, `assignJudge`, `removeJudge`, `startTable`,
  `configureCategoryMedalRound`, `startMedalRound`, `completeMedalRound`,
  `reopenMedalRound`, `resetMedalRound`, `recordMedal`, `updateMedal`,
  `deleteMedalAward`, `startBos`, `completeBos`, `reopenBos`, `resetBos`,
  `recordBosPlacement`, `updateBosPlacement`, `deleteBosPlacement`
- `ScoresheetService`: `createScoresheetsForTable`, `ensureScoresheetForEntry`,
  `updateScore`, `updateOverallComments`, `setAdvancedToMedalRound`,
  `setCommentLanguage`, `submit`, `revertToDraft`, `moveToTable`

i18n key `error.judging.results-published-frozen` added in EN + PT + ES + IT + PL.

**Un-freezing:** The awards-admin view's "Revert Publication" button calls
`competitionService.revertDivisionStatus(divisionId, adminUserId)`, which rolls
`division.status` back to `DELIBERATION`. At that point, judging mutators stop
throwing the frozen error.

---

## 7. Views and Routes

### 7.1 AwardsPublicResultsView (`@AnonymousAllowed`)

**Route:** `/competitions/:compShortName/divisions/:divShortName/results`

**Auth:** anonymous. `beforeEnter()` forwards to root when
`division.status != RESULTS_PUBLISHED` (treats as not-found rather than 403 — no
information leak).

**Layout:**
- Header: competition logo + "Competition — Division — Results".
- BOS section (top): ordered placements 1..N (where N = `Division.bosPlaces`). Each
  row: mead name, meadery / mead maker name. No entry IDs. No category column.
- Per-category sections (one per JUDGING-scope `DivisionCategory`, ordered by code):
  GOLD list → SILVER list → BRONZE list. Each medal entry: mead name + meadery name
  only. Withheld medals not listed. Categories with no medals — empty-state rendering
  decided at implementation (see §13).
- Footer: "Last updated {date}" when `Publication.version > 1`. No version number.

**DTO:** `PublicResultsView(competitionName, divisionName, bosPlacements, categories)`
with nested `PublicMedalRow(meadName, meaderyName)` and `PublicBosRow(place,
meadName, meaderyName)`.

### 7.2 AwardsAdminView

**Route:** `/competitions/:compShortName/divisions/:divShortName/results-admin`

**Auth:** `@PermitAll` + `beforeEnter()` (SYSTEM_ADMIN OR
`isAuthorizedForDivision`; password gate for non-SYSTEM_ADMIN users).

**Entry point:** "Manage Results" button on `JudgingAdminView` header, visible when
`division.status >= DELIBERATION`.

**Layout:**
- Header: division status badge + action buttons (state-dependent):
  - **▶ Publish Results** — `status = DELIBERATION`. Simple confirmation dialog.
    Calls `awardsService.publish(...)`.
  - **↺ Republish** — `status = RESULTS_PUBLISHED`. Dialog with mandatory `TextArea`
    (min 20 chars, max 1000). Calls `awardsService.republish(...)`. After save,
    shows a non-blocking reminder banner: "Republished. Don't forget to send an
    announcement to entrants."
  - **↶ Revert Publication** — `status = RESULTS_PUBLISHED`. Type-confirm dialog
    (user types `REVERT`). Calls `competitionService.revertDivisionStatus(...)`.
  - **✉ Send Announcement** — `status = RESULTS_PUBLISHED`. Dialog with optional
    `TextArea` (custom message). Dialog body shows a small preview of which
    template will be used + the latest justification excerpt (if version > 1) so
    the admin knows the default body if they leave the TextArea blank. Calls
    `awardsService.sendAnnouncement(...)`.
- Below header: per-category leaderboard tables sorted by total score desc. Columns:
  rank, entry id, entrant name, meadery, mead name, Round 1 total, advanced (yes/no),
  medal (or "Withheld" or "—"), BOS place (or "—"), "View Scoresheet" link.
- BOS section: full leaderboard with entrant names + mead names + originating
  categories.
- Publication history (expandable): list of all `Publication` rows for this
  division — version, published-at, published-by display name, justification
  excerpt (if any).

### 7.3 MyEntriesView extension

**Existing route stays:** `/competitions/:compShortName/divisions/:divShortName/my-entries`

When `division.status = RESULTS_PUBLISHED`:
- Page header gains a "Results" banner with the announce date.
- Entry grid gains 4 columns:
  - `Round 1 Total` (e.g., "82 / 100")
  - `Advanced to Medal Round` (yes / no)
  - `Medal` ("GOLD" / "SILVER" / "BRONZE" / "—" — withheld also renders as "—")
  - `BOS Place` ("1st" / "2nd" / "—")
- Actions column extends with `🔍 View Scoresheet` button per entry → opens
  `MyScoresheetView`.
- Other behaviors unchanged. Withdrawn entries display normally (with their existing
  status); their results columns show "—".

### 7.4 MyScoresheetView

**Route:** `/competitions/:compShortName/divisions/:divShortName/my-entries/:entryId/scoresheet`

**Auth:** `@PermitAll` + `beforeEnter()` (entrant must own the entry; admin always
allowed). Status must be `RESULTS_PUBLISHED` (forward otherwise).

**Layout:**
- Entry header card (read-only): entry id, mead name, category, characteristics.
- One card per scoresheet for the entry, labeled `Judge 1`, `Judge 2`, … (stable
  ordinals by `submittedAt ASC`). Each card shows:
  - 5 per-field scores with field name, value, max, and tier label (e.g.,
    "Aroma/Bouquet: 27 / 30 — Excellent").
  - Comment block with comment-language subheader ("Comments — written in {Language}").
  - Total at the bottom ("Total: 82 / 100").
- "Download PDF" button at top → calls `scoresheetPdfService.generatePdf(scoresheetId,
  currentUserId, ANONYMIZED, currentLocale)`. (Note: this generates one PDF per
  scoresheet; if multiple scoresheets exist, the button could be per-card. Final UX
  detail decided at implementation.)
- Read-only — no edit affordances.

### 7.5 Admin scoresheet drill-in

Reuses `MyScoresheetView` for admin too? **No** — admin needs full judge identity
(names + certifications). Cleanest is a separate admin-only view, but for v1 we
add a query-param flag or a parallel route. Decision deferred to implementation:
either reuse `MyScoresheetView` with a request-time anonymization-level decision
based on caller authorization, or split into a dedicated admin route. **Default
recommendation:** dedicated admin route `/competitions/:c/divisions/:d/results-admin/scoresheets/:scoresheetId`
to keep authorization simple.

### 7.6 Stable anonymization

Judge anonymization uses **per-entry stable ordering** by `submittedAt ASC` with
nulls (unsubmitted scoresheets aren't visible to entrants anyway). The
`AnonymizedScoresheetView` DTO carries the ordinal (`Judge 1`, `Judge 2`, …); the
original `filledByJudgeUserId` is dropped before the DTO reaches the view layer.

---

## 8. Email / Announcement Flow

### 8.1 Templates

Three new Thymeleaf templates in `src/main/resources/email-templates/`, all
extending `email-base.html`:

| Template | Trigger | Subject key | Body content |
|----------|---------|-------------|--------------|
| `results-published.html` | `sendAnnouncement(divisionId, null, ...)` after initial publish | `email.results-published.subject` = "Your {competitionName} — {divisionName} results are available" | Generic congratulations + CTA |
| `results-republished.html` | `sendAnnouncement(divisionId, null, ...)` after republish | `email.results-republished.subject` = "{competitionName} — {divisionName} results have been updated" | Justification text from `latestPublication.justification`, rendered via `th:text` (auto-escaped) + CTA |
| `custom-announcement.html` | `sendAnnouncement(divisionId, customMessage, ...)` (any version) | `email.custom-announcement.subject` = "Update from {competitionName} — {divisionName}" | Admin's custom message via `th:text` (auto-escaped) + CTA |

All three include a magic-link CTA button to
`/competitions/:c/divisions/:d/my-entries` (per-entrant link, 7-day validity via
`JwtMagicLinkService.generateLink(email, Duration.ofDays(7))` — same convention as
existing email notifications: login magic link, password setup/reset, submission
confirmation, credit notification).

### 8.2 Rate limiting

- Per-user 5-min email rate limit: **bypassed** (admin-triggered email, same
  convention as `OrderReviewNotificationListener` and other system-driven emails).
- Daily counter: still applies; logs WARN at threshold (50).

### 8.3 Localization

Each entrant receives the email in their `User.preferredLanguage` locale. Subject
keys + template body keys added in EN + PT + ES + IT + PL (initial work in EN + PT,
ES/IT/PL catch-up in the final cycle).

---

## 9. PDF Generation

### 9.1 ScoresheetPdfService (judging module)

**Confirmed during brainstorming:** no `ScoresheetPdfService` exists in the codebase
yet. Path P1 chosen — implementation lives in the judging module (public API class
mirroring `LabelPdfService` in entry module). Awards consumes it.

**Public API:**

```java
package app.meads.judging;

public class ScoresheetPdfService {
    public byte[] generatePdf(UUID scoresheetId, UUID requestingUserId,
                              AnonymizationLevel level, Locale locale);
}

public enum AnonymizationLevel {
    ANONYMIZED,  // strip judge name/cert; use "Judge N" ordinal
    FULL         // show judge name + certifications
}
```

**Authorization (in service):**
- SYSTEM_ADMIN or competition ADMIN → both levels allowed.
- Entrant (entry owner) → only `ANONYMIZED` allowed; `FULL` rejected with
  `BusinessRuleException`.
- Other users → both levels rejected.

**Layout:** A4 portrait, locale-aware (locale parameter is the *printer's* UI
locale, same mechanism as `LabelPdfService`).
- Competition logo + competition + division header.
- Entry header card: entry id (or anonymized id for ANONYMIZED), mead name,
  category, characteristics.
- One scoresheet block per scoresheet (typically 1, since the input is a single
  scoresheet id — but the rendering format supports multiple as a future
  extension):
  - Judge label: `Judge N` (ANONYMIZED) or `Judge: {name} ({certifications})` (FULL).
  - 5 per-field score rows.
  - Comments block with "Comments — written in {Language}" subheader.
  - Total row.
- Embedded Liberation Sans font (same as `LabelPdfService`, for Unicode support
  including Polish diacritics).

### 9.2 Download endpoints

- Entrant `MyScoresheetView`: "Download PDF" → `scoresheetPdfService.generatePdf(
  scoresheetId, currentUserId, ANONYMIZED, currentLocale)` → served as
  `application/pdf` via `StreamResource` (same pattern as `LabelPdfService` in
  `MyEntriesView`).
- Admin scoresheet drill-in: "Download PDF (anonymized)" + "Download PDF (full)"
  buttons, calling the service with the appropriate level.

---

## 10. Security Rules

| Action | SYSTEM_ADMIN | Competition ADMIN | Entrant (own entries) | Anonymous |
|--------|--------------|-------------------|----------------------|-----------|
| Publish | ✓ | ✓ | ✗ | ✗ |
| Republish | ✓ | ✓ | ✗ | ✗ |
| Revert publication | ✓ | ✓ | ✗ | ✗ |
| Send announcement | ✓ | ✓ | ✗ | ✗ |
| View admin results page | ✓ | ✓ | ✗ | ✗ |
| View public results page | ✓ | ✓ | ✓ | ✓ (when published) |
| View own entries' results | ✓ | ✓ | ✓ (own only) | ✗ |
| Drill into own scoresheet | ✓ | ✓ | ✓ (own only) | ✗ |
| Download anonymized scoresheet PDF | ✓ | ✓ | ✓ (own only) | ✗ |
| Download full scoresheet PDF | ✓ | ✓ | ✗ | ✗ |

---

## 11. TDD Sequencing

Each cycle is a full RED-GREEN-REFACTOR per `CLAUDE.md`'s two-tier TDD workflow.

### Cycle 1 — `Publication` entity (unit test)
- `PublicationTest`: constructor self-generates UUID + sets fields; `version=1`
  allows null `justification`; `republish` factory throws on blank justification;
  `isInitial` correctly tracks `version=1`.
- Implements: `Publication.java`.

### Cycle 2 — Module skeleton + `PublicationRepository` (repository test)
- Create `app.meads.awards` package + `package-info.java`.
- `PublicationRepositoryTest`: save, `findTopByDivisionIdOrderByVersionDesc`,
  `findByDivisionIdOrderByVersionAsc`, `existsByDivisionId`.
- Implements: `PublicationRepository`, V28 migration.

### Cycle 3 — `DivisionStatus.isResultsFrozen()` + judging mutator guards
- Add the enum helper.
- Add guard line + tests to every mutating method in `JudgingService` and
  `ScoresheetService` (audit list in §6).
- i18n key `error.judging.results-published-frozen` in EN + PT (ES/IT/PL deferred
  to final cycle).

### Cycle 4 — `AwardsService.publish` (unit + integration)
- `AwardsServiceImplTest`: auth, status precondition, creates `Publication`,
  advances status, publishes event.
- `AwardsModuleTest` integration: full publish flow + subsequent judging mutation
  rejected.
- Implements: `AwardsService` + impl + `ResultsPublishedEvent`.

### Cycle 5 — `AwardsService.republish` (unit test)
- Auth, status precondition, required justification, creates Publication, no
  status change, publishes event.

### Cycle 6 — Read methods + DTOs (unit test)
- `getLatestPublication`, `getPublicationHistory`, `getResultsForEntrant`,
  `getResultsForAdmin`, `getPublicResults`, `getAnonymizedScoresheet`.
- DTOs: `EntrantResultRow`, `AdminResultsView`, `PublicResultsView`,
  `AnonymizedScoresheetView`.
- Stable anonymization ordering tested explicitly.

### Cycle 7 — `AwardsService.sendAnnouncement` (unit test)
- Three template branches (initial, republish, custom). Mock `EmailService`.
  Locale resolution per entrant.
- Templates: `results-published.html`, `results-republished.html`,
  `custom-announcement.html`. Subject keys.
- Magic-link CTA generation.
- `AnnouncementSentEvent` published.

### Cycle 8 — `ScoresheetPdfService` (unit test, in judging module)
- ANONYMIZED level strips judge identity; FULL level shows it.
- Locale-aware comment subheader + tier labels.
- Authorization rules per `AnonymizationLevel`.
- Implements: `ScoresheetPdfService` (judging public API), `AnonymizationLevel`
  enum.

### Cycle 9 — `AwardsPublicResultsView` (Vaadin UI test)
- Anonymous access; forward when status ≠ `RESULTS_PUBLISHED`.
- Layout: category sections + BOS leaderboard; mead + meadery only.
- Withheld medals not listed.
- i18n keys `awards.public.*`.

### Cycle 10 — `AwardsAdminView` (Vaadin UI test)
- Auth check; password gate for competition admin.
- All four action buttons visible only when expected.
- Publish, Republish (justification validation), Revert (type-confirm), Send
  Announcement (template preview) dialogs.
- Publication history section.
- "Manage Results" button added to `JudgingAdminView`.
- i18n keys `awards.admin.*`.

### Cycle 11 — `MyEntriesView` extension + `MyScoresheetView` (Vaadin UI test)
- `MyEntriesView`: 4 new columns + "View Scoresheet" button when
  `status = RESULTS_PUBLISHED`. Withheld renders `—`.
- `MyScoresheetViewTest`: read-only rendering with `Judge N` anonymization;
  per-field scores with tier labels; comment language subheader; total; Download
  PDF.
- i18n keys `my-entries.results.*` + `my-scoresheet.*`.

### Cycle 12 — Cross-module integration + modulith verify
- `AwardsModuleTest`: bootstrap + full lifecycle (publish → judging frozen →
  revert → judging unfrozen → edit → republish → send announcement → entrants get
  email → public view shows updated results).
- `ModulithStructureTest` passes with the new module + dependencies.

### Final cycle — ES/IT/PL i18n catch-up + walkthrough
- Translate all new keys (~100–150 estimated) to ES, IT, PL.
- Add Section 13 to `docs/walkthrough/manual-test.md` covering: publish, send
  announcement, entrant view, scoresheet drill-in, public view, revert, edit,
  republish, send announcement again with custom message.

---

## 12. Deliverables Summary

- **1 new module:** `app.meads.awards`
- **1 entity:** `Publication`
- **1 service interface + impl:** `AwardsService`
- **3 events:** `ResultsPublishedEvent`, `ResultsRepublishedEvent`,
  `AnnouncementSentEvent`
- **4 DTOs:** `EntrantResultRow`, `AdminResultsView`, `PublicResultsView`,
  `AnonymizedScoresheetView`
- **1 enum helper:** `DivisionStatus.isResultsFrozen()`
- **~25 guard lines** added to existing judging mutators (audit in §6)
- **3 new views:** `AwardsPublicResultsView`, `AwardsAdminView`, `MyScoresheetView`
- **1 extended view:** `MyEntriesView` (4 new columns + scoresheet link)
- **1 button added** to `JudgingAdminView` ("Manage Results")
- **1 PDF service:** `ScoresheetPdfService` (judging module public API) +
  `AnonymizationLevel` enum
- **3 email templates** + subject keys in 5 languages
- **1 migration:** V28
- **Walkthrough Section 13** in `manual-test.md`
- **~80–120 new tests** (rough estimate based on judging-module Phase 5 deltas)

---

## 13. Open Questions / Deferred

- **Admin scoresheet drill-in route:** reuse `MyScoresheetView` with caller-based
  anonymization or split into dedicated admin route (default recommendation:
  dedicated admin route at
  `/competitions/:c/divisions/:d/results-admin/scoresheets/:scoresheetId`).
  Final call at implementation.
- **Empty-category rendering on public view:** show "No medals awarded in this
  category" vs omit the section entirely. Decided at implementation.
- **Certificates** (PDF for medal winners) — deferred to v2.
- **Public results discoverability** — direct URL only for v1. Future: link
  from competition home page, search engine indexing meta tags.
- **Multi-scoresheet "Download all PDFs" button** for entrants — deferred.
  v1: per-scoresheet download.
- **Republish to "affected only"** — admin-side override option (option B from
  the brainstorming announcement-reach question) — deferred. v1: always all
  entrants.
- **Public anonymous results during DELIBERATION** — currently forwarded when
  status ≠ `RESULTS_PUBLISHED`. Future: an "admin preview" link that lets the
  admin view the public layout before publishing.
