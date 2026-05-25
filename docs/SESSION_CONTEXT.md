# Session Context — MEADS Project

## What this file is

Standalone context for resuming work on the MEADS project. Contains everything
needed to continue even without memory files or prior conversation history.

---

## Project Overview

**MEADS (Mead Evaluation and Awards Data System)** — Spring Boot 4 + Vaadin 25
(Java Flow) + PostgreSQL 18 web app for managing mead competitions. Uses Spring
Modulith for modular DDD architecture, Flyway for migrations, Testcontainers +
Karibu Testing for tests. Full conventions in `CLAUDE.md` at project root.

**Branch:** `feature/judging-module` at `0.4.0-SNAPSHOT` — **Awards module COMPLETE** (2026-05-12, all 13 tasks done). Judging Phase 6 views also complete. **Judging event listeners complete (2026-05-20)** — `JudgingNotificationListener` emails judges on table start / scoresheet revert / medal-round activation; the judging module is now functionally complete. Architecture: `Publication` audit-trail aggregate + freeze-in-place via `DivisionStatus.isResultsFrozen()` guard on every judging mutator. Decoupled publish/republish/announcement: only `sendAnnouncement` triggers emails. Per the plan's open question, chose option B for entrant-facing scoresheet drill-in (new `MyResultsView` + `MyScoresheetView` in awards module + banner-link from `MyEntriesView`) to keep dependency direction unidirectional (awards → entry). **Merged main 2026-05-16** to pick up the v0.3.0 bug fixes (credits-grid refresh, webhook post-registration guard, judging-category parent-delete guard), MFA email reset flow, codebase-wide inline-FQN cleanup + CLAUDE.md rule, and i18n cleanup (sidebar nav, Final Category, dialog buttons). Post-merge: caught up ES/IT/PL with the 14 new keys that main only added to EN/PT. **Merged main again 2026-05-19** to pick up the v0.3.0 release commits + 6 mid-walkthrough fixes (MFA verify Enter shortcut, Final Category picker disabled until init, primary-category dropdowns filtered to REGISTRATION scope, leaf-only judging categories in Final Category picker, Categories tab + parent select filter to REGISTRATION, entrant updateEntry enforces entry limits on category change). **Merged main again 2026-05-20** to pick up v0.3.1 (country names localized to the current UI language — `app.meads.CountryDisplay`) and then **v0.3.2** (European Portuguese country names — `pt` pinned to `pt-PT`; CI moved off the deprecated Node 20 runtime; Vaadin 25.1.5 / OpenPDF 3.0.4 bumps).
**Tests:** 1190 passing (`mvn test -Dsurefire.useFile=false`) — verified 2026-05-25 after small judging-admin tweak: **Entries-count column on the Rounds grid**, sitting between Judges (count) and Scheduled. Single new key `judging-admin.rounds.column.entries` × 5 locales (Entries / Inscrições / Inscripciones / Iscrizioni / Zgłoszenia). One new UI test (`JudgingAdminViewTest.shouldRenderEntriesCountColumnNextToJudgesOnRoundsGrid`) + headers list updated on `shouldRenderRoundsGridAndTypeFilterOnRoundsTab`. Earlier 2026-05-25 (1189) after **deferred item #3: medal-round redesign**. Three commits: (A) V31 partial unique index `idx_judging_rounds_one_medal_per_category` on `(judging_id, division_category_id) WHERE type = 'MEDAL'` backstops the one-medal-round-per-category rule at the DB; SCORING rounds in the same category still allowed (split-category). (B) Cascade auto-population: when the scoring-round cascade fires and the medal round transitions to READY, populate `medalRound.entries` from the eligible set per mode (COMPARATIVE → advance-flag only; SCORE_BASED → all SUBMITTED). `findMedalRoundEntries` now reads from `round.entries` when populated, falls back to derivation otherwise. (C) `MedalRoundView` gains "Assign Entries" button + dialog mirroring the Rounds-tab equivalent — enabled at PENDING/READY/ACTIVE, locked at COMPLETE. New i18n keys (8 for the dialog + button) × 5 locales. Earlier 2026-05-25 (1185): **deferred item #4: scoresheet improvements**. Three sub-changes: (a) `ScoresheetView` hides `meadName` from judges (anonymity rule — judges judge to style, not to a brand); admins still see it. New `isAdminView` field set in `beforeEnter`. (b) Per-item comment text area per MJP scoresheet field (`score-comment-<fieldName>` TextArea after each NumberField). `score_fields.comment` column already existed; `saveDraft` now passes the comment value to `ScoresheetService.updateScore` (signature already accepted it). New i18n key `scoresheet.scores.comment.placeholder` × 5 locales. (c) Comment language Select falls back to `User.preferredLanguage` after the existing `JudgeProfile.preferredCommentLanguage` lookup. Three new tests + walkthrough §12.11 refresh. Earlier 2026-05-25 (1182): **deferred item #2: cross-division shared tables flag**. New `Competition.sharedTables` boolean (default TRUE for new competitions, V30 migration `competitions.shared_tables BOOLEAN NOT NULL DEFAULT TRUE`); `JudgingService.startRound` gains a cross-division busy-check that matches by physical-table label across all divisions of the competition when the flag is ON (new key `error.round.physical-table-busy-shared` × 5 locales). UI: "Shared tables across divisions" checkbox on CompetitionDetailView Settings tab; banner above + Add Table on JudgingAdminView Physical Tables tab when the flag is ON. Three new tests on `JudgingServicePhysicalTableTest`: cross-division shared busy reject, sharedTables=false same-label allowed, and a regression test for the long-standing cross-division judge active-conflict (which already worked via `findAll()` — now covered). Earlier 2026-05-25 (1179): **deferred item #5: i18n cleanup sweep** (finish Table → Round rename in EN + PT/ES/IT/PL; 15 keys × 5 locales + `judge-table.title` + 2 comment lines; UI now uses "Round" for JudgingRound, reserves "Table" for PhysicalTable; min-judges label and table-ready emails renamed; walkthrough cleaned). Earlier 2026-05-25 (1179): **deferred item #1: judging setup allowed at REGISTRATION_CLOSED**. Three changes: `JudgingAdminView.beforeEnter` gate lowered from `< JUDGING` to `< REGISTRATION_CLOSED`; `DivisionDetailView` "Manage Judging" button now appears starting at REGISTRATION_CLOSED; new service gate on `JudgingService.startRound` requiring `>= JUDGING` (new key `error.round.cannot-start-before-judging` × 5 locales). Setup ops (rounds, judges, entries, medal-round CRUD, physical tables, judging categories) all work at REGISTRATION_CLOSED — `startRound` is the only op gated to JUDGING+. New tests: `JudgingServicePhysicalTableTest.shouldRejectStartRoundWhenDivisionNotYetInJudging` + `JudgingAdminViewTest.shouldRenderViewAtRegistrationClosedSoAdminsCanSetUpBeforeJudgingStarts`. Walkthrough §12.4.2 / §12.5.1 / §12.6 / §12.6.4 updated to set up at REG_CLOSED, then advance to JUDGING right before starting the first round. Earlier 2026-05-25 (1177): removed the late-RECEIVED auto-path: deleted `EntryReceivedScoresheetListener`, `EntryReceivedEvent` record, and the two publication points in `EntryService.markReceived` / `advanceEntryStatus(SUBMITTED→RECEIVED)`. Late-arriving entries now go RECEIVED only (no scoresheet auto-creation); admin assigns final category + uses Rounds → Assign Entries to add the entry to a round of their choice (manual path via `JudgingService.assignEntryToRound` still creates the DRAFT scoresheet when the round is ACTIVE SCORING). Earlier 2026-05-24 (1178): loosening the entry-assignment lock (entries can now be added/removed at PENDING, READY, and ACTIVE; on ACTIVE the service creates the scoresheet on add via `ensureScoresheetForEntry` and deletes the DRAFT scoresheet on remove via `deleteScoresheet`; SUBMITTED scoresheets block the remove path with `error.entry.cannot-unassign-submitted`; COMPLETE rounds block both with `error.entry.cannot-change-on-complete-round`; 4 new tests + 2 new i18n keys × 5 locales + tooltip rewording). Earlier 2026-05-24 (1174): mid-walkthrough scoring-round revert flow (`JudgingService.revertScoringRound` returns an ACTIVE scoring round to READY and wipes its scoresheets; blocked when any scoresheet is SUBMITTED via `error.round.cannot-revert-submitted-scoresheets`; medal rounds keep using `resetMedalRoundById` per `error.round.revert-scoring-only`; new ↶ Revert button on the Rounds tab with confirmation dialog; new `ScoresheetService.deleteAllForRound` helper; 4 new unit tests on `JudgingServiceRoundTest`; 8 new i18n keys × 5 locales). Earlier 2026-05-24 (1170): mid-walkthrough hard-COI assignment fix (`JudgingService.assignJudge` now rejects when the candidate judge owns any entry in the round's category; new key `error.coi.assign-hard-block` in all 5 locales; `JudgingAdminView` assign-judges dialog also reverts hard-COI selections client-side via a selection listener; one new unit test on `JudgingServiceRoundTest`). Earlier 2026-05-24 (1169): cycle 9 (both walkthrough-noted gaps closed — see "Cycle 9" entry below for header mode/PT pickers + ACTIVE-reassignable medal-round judges). Earlier same-day: cycles 6a–c (UI restructure to Physical Tables/Rounds/Results/BOS) + cycle 7 (MedalRoundView migration + V22 contraction deleting legacy CJC.medalRoundStatus + physicalTableId + 5 legacy service methods + MedalRoundStatus enum) + cycle 8 (Start button on MedalRoundView with SCORE_BASED auto-fill, walkthrough §12.6–§12.8 + §12.12 rewrite, Assign Entries dialog on the Rounds tab + Assign Judges button on MedalRoundView, dev seed split-category demo for Profissional M1A + pre-staged medal round with independent judges for M1B). Earlier 2026-05-23 verified at 1161 after the **JudgingTable → JudgingRound rename + new PhysicalTable entity (per-division)** refactor: V21–V27 SQL renamed in-place + V29 adds `physical_tables` + nullable `physical_table_id` FKs; new `PhysicalTable` entity / repo / 6 service methods (create/edit/delete/findByDivision/findById/assignRoundToPhysicalTable + assignMedalRoundToPhysicalTable); validations at `startRound` (round must have a physical table; physical table not busy with another ROUND_1; no assigned judge on another active round) and at `assignJudge` (same judge-conflict check when adding to a ROUND_1 round); new "Physical Tables" tab in `JudgingAdminView` + Add-Round dialog requires picking a physical table; medal-round physical-table assignment surfaced as a per-row Select in JudgingAdminView Medal Rounds tab + MedalRoundView header; dev seed creates 3 physical tables for Amadora + 5 for Profissional. `JudgingServicePhysicalTableTest` (15 tests) covers all CRUD + validation rejection paths; `JudgingAdminViewTest` (+6 UI tests) covers Physical Tables tab dialogs (add happy/duplicate-error, edit happy, delete happy/in-use-error, grid+button rendering). Also earlier in the session: `DivisionAdvanceGuard` family + `EntryFinalCategoryAdvanceGuard`, `AwardsService.republish` refactor, BOS-places lock at JUDGING, comment-languages restriction removed (ISO 639-1), Amadora 11 entries + 6 judges + soft/hard COI seed, `assignFinalCategoriesByCode` bulk button, `EntryReceivedEvent`/listener for late-RECEIVED scoresheet sync, `EntryStatusRevertGuard` + `JudgingScoresheetEntryRevertGuard`, LazyInit fix on `JudgingRound.assignments`.
**TDD workflow:** Two-tier (Full Cycle / Fast Cycle) — see `CLAUDE.md`

---

## Modules Implemented

### identity module (`app.meads.identity`)
- User entity (UUID, email, name, status, role, optional password, optional meaderyName, optional country, optional totpSecret, mfaEnabled)
- JWT magic link authentication + admin password login + access code login
- **TOTP-based MFA for SYSTEM_ADMIN**: `TotpService` (HMAC-SHA1, Base32, ±1 window); `UserService` MFA methods (`setupMfa`, `confirmMfa`, `verifyMfaCode`, `disableMfa`); `MfaAuthenticationSuccessHandler` redirects MFA-enabled admins to `/mfa` after login; `MfaVerifyView` (`/mfa`, `@AnonymousAllowed`); MFA setup/disable section in `ProfileView` (SYSTEM_ADMIN only). V19 migration adds `totp_secret` and `mfa_enabled` columns.
- UserService (public API) — includes `updateProfile()` with ISO 3166-1 alpha-2 country validation
- SecurityConfig, UserListView (admin CRUD with meadery name + country fields)
- ProfileView (`/profile`) — self-edit for name, meadery name, country + MFA section (SYSTEM_ADMIN only)
- Password setup & reset: `SetPasswordView`, `setPasswordByToken()`, `generatePasswordSetupLink()`,
  `hasPassword()`, triggers on admin role assignment, "Forgot password?" on login, admin "Password Reset"
- EmailService (public API) — `SmtpEmailService` (internal) with `JavaMailSender` + Thymeleaf HTML templates.
  Sends magic link, password reset, password setup, credentials reminder, order review alert, submission confirmation, and credit notification emails. SMTP failure logged with fallback link (no crash).
  Per-user rate limiting (5-min cooldown per email type) on user-triggered emails (magic link, password reset, credentials reminder). Daily email counter with WARN at threshold (50).
  Mailpit for dev (port 1025 SMTP, port 8025 web UI). Resend SMTP for prod. 7-day token validity.
- **Status:** Complete

### competition module (`app.meads.competition`)
- **Depends on:** identity
- **Status:** Complete (fully implemented + code reviewed + scope rework done)

#### Entities (public API)
| Entity | Table | Description |
|--------|-------|-------------|
| `Competition` | `competitions` | Top-level: name, shortName (unique), dates, location, logo, contactEmail, shippingAddress, phoneNumber, website |
| `Division` | `divisions` | Sub-level: competitionId, name, shortName (unique per competition), scoringSystem, status, entry limits (per subcategory, per main category, total), entryPrefix, meaderyNameRequired, registrationDeadline, registrationDeadlineTimezone |
| `Participant` | `participants` | Competition-scoped: userId, accessCode |
| `ParticipantRole` | `participant_roles` | Role per participant: JUDGE, STEWARD, ENTRANT, ADMIN |
| `Category` | `categories` | Read-only catalog: code, name, scoringSystem |
| `DivisionCategory` | `division_categories` | Per-division category with optional parent |
| `CompetitionDocument` | `competition_documents` | Competition-scoped document (PDF upload or external link), optional language filter |

#### Key enums
- `DivisionStatus`: DRAFT → REGISTRATION_OPEN → REGISTRATION_CLOSED → JUDGING → DELIBERATION → RESULTS_PUBLISHED
- `CompetitionRole`: JUDGE, STEWARD, ENTRANT, ADMIN
- `ScoringSystem`: MJP
- `DocumentType`: PDF, LINK

#### Service — `CompetitionService` (public API)
- Competition CRUD, Division CRUD, Participant management (add/remove participant, add/remove individual role, role combination validation), Category management
- Document management: `addDocument` (with optional language), `removeDocument`, `updateDocumentName`, `reorderDocuments`, `getDocuments`, `getDocumentsForLocale`, `getDocument`
- Authorization: `isAuthorizedForCompetition()`, `isAuthorizedForDivision()`
- `findCompetitionsByAdmin(userId)` — finds competitions where user has ADMIN participant role
- `findAdminEmailsByCompetitionId(competitionId)` — returns email addresses of all ADMIN participants
- `updateDivisionDeadline()` — updates registration deadline (DRAFT or REGISTRATION_OPEN only)
- `updateCompetitionContactEmail()` — updates competition contact email (shown in participant emails)
- `revertDivisionStatus()` — one-step-back revert with guard interface pattern
- Entry limits (per subcategory, per main category, total) — DRAFT-only, enforced by EntryService
- Events: `DivisionStatusAdvancedEvent`

#### Views
- `CompetitionListView` (`/competitions`) — SYSTEM_ADMIN only, all competitions grid with CRUD
- `CompetitionDetailView` (`/competitions/:shortName`) — tabs: Divisions, Participants, Settings, Documents (add/edit/delete/reorder PDF and link documents)
- `DivisionDetailView` (`/competitions/:compShortName/divisions/:divShortName`) — header: competition logo + "Competition — Division", tabs: Categories, Settings + "Manage Entries" button + "Advance/Revert Status" buttons
- `MyCompetitionsView` (`/my-competitions`) — `@PermitAll`, shows competitions where user is ADMIN

#### Migrations: V3–V8, V14, V17, V18

### entry module (`app.meads.entry`) — COMPLETE

- **Depends on:** competition, identity
- **Status:** All 11 phases complete
- **Design:** `docs/plans/2026-03-02-entry-module-design.md`

#### Entities (public API)
| Entity | Table | Migration | Description |
|--------|-------|-----------|-------------|
| `ProductMapping` | `product_mappings` | V9 | Jumpseller product → division mapping |
| `JumpsellerOrder` | `jumpseller_orders` | V10 | Webhook order storage, idempotency, customerCountry |
| `JumpsellerOrderLineItem` | `jumpseller_order_line_items` | V11 | Per-product line items |
| `EntryCredit` | `entry_credits` | V12 | Append-only credit ledger |
| `Entry` | `entries` | V13 | Mead entry aggregate root |

#### Enums
- `EntryStatus`: DRAFT, SUBMITTED, RECEIVED, WITHDRAWN
- `Sweetness`: DRY, MEDIUM, SWEET
- `Strength`: HYDROMEL, STANDARD, SACK
- `Carbonation`: STILL, PETILLANT, SPARKLING
- `OrderStatus`: PROCESSED, PARTIALLY_PROCESSED, NEEDS_REVIEW, UNPROCESSED
- `LineItemStatus`: PROCESSED, NEEDS_REVIEW, IGNORED, UNPROCESSED

#### Services
- **EntryService** — Product mapping CRUD, credit management, entry CRUD, submission, limits enforcement (total, subcategory, main category). `advanceEntryStatus()` calls `publishSubmissionEventIfComplete()` when the transition is DRAFT→SUBMITTED, keeping admin-triggered submissions consistent with entrant-triggered ones. `assignFinalCategory(entryId, finalCategoryId, userId)` — standalone method to set/clear final category; validates `finalCategoryId` is a JUDGING-scoped category when judging categories exist (falls back to any category when none exist yet).
- **WebhookService** — HMAC signature verification, `processOrderPaid` (JSON parsing, idempotency, mutual exclusivity, credit creation, country enrichment from shipping/billing address, publishes `OrderRequiresReviewEvent` for NEEDS_REVIEW/PARTIALLY_PROCESSED orders)
- **LabelPdfService** — PDF label generation (OpenPDF + ZXing QR codes). Single entry or batch. A4 landscape, 2-line instruction header (line 1: print/attach instructions, line 2: shipping address if set), 3 identical labels per page. Labels include: competition/division name, entry ID, mead name (2-line fixed height), category code, characteristics with field names (Sweetness/Strength/Carbonation), ingredients (Honey/Other/Wood, 2-line fixed height each — text wraps then clips), QR code (left) + notes area (right), "FREE SAMPLES. NOT FOR RESALE." disclaimer. Public API for cross-module access.

#### Events
- `CreditsAwardedEvent(divisionId, userId, amount, source)`
- `EntriesSubmittedEvent(divisionId, userId, List<EntryDetail> entryDetails)`
- `OrderRequiresReviewEvent(orderId, jumpsellerOrderId, customerName, customerEmail, affectedCompetitionIds, affectedDivisionNames, status)`

#### DTOs
- `EntryDetail(entryNumber, meadName, categoryCode, categoryName)` — DTO for submission event payload
- `EntrantCreditSummary(userId, email, name, creditBalance, entryCount)`

#### Views
- `EntrantOverviewView` (`/my-entries`) — cross-competition entrant hub, shows all divisions with credits/entries, auto-redirects to single division
- `MyEntriesView` (`/competitions/:compShortName/divisions/:divShortName/my-entries`) — header: competition logo + "Competition — Division — My Entries", entrant-facing, competition documents list, credits + limits display, process info box, registration deadline display, category guidance hints, entry grid with status badges/Final Category/Actions (view/edit/submit/download label)/filtering/sorting, add/edit dialog (full-width fields, per-field validation, prefixed entry IDs), "Submit All Drafts" button, "Download all labels" batch button (disabled until all entries submitted), meadery name required warning + submit blocking
- `DivisionEntryAdminView` (`/competitions/:compShortName/divisions/:divShortName/entry-admin`) — header: competition logo + "Competition — Division — Entry Admin", admin tabs: Credits, Entries (with Meadery/Country/Final Category columns + view/edit/←/→/withdraw/delete/download-label actions + "Add Entry" button always enabled for admins + batch "Download all labels" with confirmation dialog + summary line showing credits balance and full per-status entry count: "Total entries: N (Draft: X, Submitted: Y, Received: Z, Withdrawn: W)"; credits balance auto-refreshes after add/adjust credits), Products, Orders. View dialog: read-only. Edit: confirmation gate then full dialog (all fields). "Add Entry": two-step — first confirmation dialog warning about bypassing credits, then full entry form with entrant email + all mead fields. `←`/`→` advance/revert status with confirmation. **Credits tab** and **Products tab** buttons disabled with tooltip when past REGISTRATION_OPEN (tooltip via Span wrapper — works on disabled buttons). Credits balance auto-refreshes after add/adjust operations.

#### REST
- `JumpsellerWebhookController` — `POST /api/webhooks/jumpseller/order-paid` (HMAC-verified)

#### Guards
- `EntryDivisionRevertGuard` — blocks REGISTRATION_OPEN → DRAFT revert when entries exist
- `EntryJudgingCategoryDeletionGuard` — blocks deletion of JUDGING categories when any `entry.finalCategoryId` references them

#### Event Listeners
- `RegistrationClosedListener` — skeleton for `DivisionStatusAdvancedEvent` (REGISTRATION_CLOSED)
- `OrderReviewNotificationListener` — sends admin alert emails when `OrderRequiresReviewEvent` is published, includes competition name and affected division(s)
- `SubmissionConfirmationListener` — sends entrant confirmation email with entry summary when `EntriesSubmittedEvent` is published (conditional: only when all credits used and no drafts remain). CTA is a magic link (7-day validity via JwtMagicLinkService). Entry lines passed as `List<String>` to template.
- `CreditNotificationListener` — sends entrant credit notification email when `CreditsAwardedEvent` is published (both webhook and admin grants). CTA is a magic link (7-day validity via JwtMagicLinkService).

#### Changes to other modules
- `SecurityConfig` — separate `SecurityFilterChain` with `@Order(1)` for webhook API (CSRF disabled, permitAll)
- `User.java` — added `meaderyName` and `country` fields (now in V2)
- `Division.java` — added `maxEntriesPerSubcategory`, `maxEntriesPerMainCategory`, `maxEntriesTotal`, `entryPrefix`, `meaderyNameRequired`, `registrationDeadline`, `registrationDeadlineTimezone`
- `DivisionDetailView` — "Manage Entries" button, entry prefix (DRAFT-only) + entry limits in Settings tab (DRAFT-only for limits and prefix), meaderyNameRequired checkbox (DRAFT-only), registration deadline fields (DRAFT/REGISTRATION_OPEN)
- `MainLayout` — "My Profile" as submenu item in user dropdown menu (navigates to `/profile`), app version display at bottom of sidebar drawer (from `BuildProperties`)
- `application.properties` — added `app.jumpseller.hooks-token`

#### Migrations: V9–V13

### awards module (`app.meads.awards`) — COMPLETE

- **Depends on:** judging, competition, entry, identity
- **Status:** All 13 plan tasks complete (2026-05-12). Design + plan: `docs/plans/2026-05-12-awards-module-{design,plan}.md`

#### Entities (public API)
| Entity | Table | Migration | Description |
|--------|-------|-----------|-------------|
| `Publication` | `publications` | V28 | Audit-only aggregate: divisionId, version (unique per division), publishedAt, publishedBy FK, justification (nullable for v1), initial boolean |

#### Service — `AwardsService` (public API)
- `publish(divisionId, adminUserId)` — DELIBERATION → RESULTS_PUBLISHED, creates Publication v1
- `republish(divisionId, justification, adminUserId)` — creates Publication v(n+1); justification 20-1000 chars, required
- `sendAnnouncement(divisionId, customMessage, adminUserId)` — emails all entrants in their preferred locale; picks template by publication version + custom-message presence
- `getLatestPublication / getPublicationHistory` — repo wrappers
- `getResultsForEntrant(userId, divisionId)` — per-entry rows (round-1 total, medal, BOS, scoresheet drill-in); requires RESULTS_PUBLISHED
- `getResultsForAdmin(divisionId, adminUserId)` — full leaderboard + BOS + publication history
- `getPublicResults(competitionShortName, divisionShortName)` — anonymized public view by short names; requires RESULTS_PUBLISHED
- `getAnonymizedScoresheet(scoresheetId, requestingUserId)` — admin OR entry owner auth; "Judge N" anonymization; entrant access requires RESULTS_PUBLISHED
- Revert: admin view calls `competitionService.revertDivisionStatus(...)` directly (asymmetric — no awards-owned data to roll back; publication record kept in audit log)

#### Events
- `ResultsPublishedEvent(divisionId, publicationId, version, publishedAt, publishedBy)`
- `ResultsRepublishedEvent(...same + justification)`
- `AnnouncementSentEvent(divisionId, publicationId, recipientCount, usedCustomMessage)`

#### DTOs (public records)
- `EntrantResultRow`, `AdminResultsView` (+ inner: AdminCategoryLeaderboard, AdminEntryRow, AdminBosRow, PublicationSummary), `PublicResultsView` (+ inner: PublicCategorySection, PublicMedalRow, PublicBosRow), `AnonymizedScoresheetView` (+ inner: AnonymizedScoresheet, FieldScore)

#### Views
- `AwardsPublicResultsView` (`/competitions/:c/divisions/:d/results`, `@AnonymousAllowed`) — anonymized public results
- `AwardsAdminView` (`/competitions/:c/divisions/:d/results-admin`, `@PermitAll` + auth) — publish/republish/announce/revert + publication history grid
- `MyResultsView` (`/competitions/:c/divisions/:d/my-results`, `@PermitAll`) — entrant-facing results grid + "View scoresheet" drill-in
- `MyScoresheetView` (`/competitions/:c/divisions/:d/my-entries/:entryId/scoresheet`) — anonymized scoresheet display + PDF download
- Banner added to `MyEntriesView` when status = RESULTS_PUBLISHED, linking to `MyResultsView`
- "Manage results" button added to `JudgingAdminView` header when status >= DELIBERATION

#### Cross-cutting changes
- `DivisionStatus.isResultsFrozen()` helper — true iff RESULTS_PUBLISHED
- `JudgingServiceImpl` + `ScoresheetServiceImpl` — every mutator (~30) calls `requireNotFrozen(divisionId)` before mutation, throwing `BusinessRuleException("error.judging.results-published-frozen")`
- `JudgingService` gained `findMedalAwardByEntryId`, `findBosPlacementByEntryId`
- `ScoresheetService` gained `findByEntryIdOrderBySubmittedAtAsc` (single derived query — entry_id is UNIQUE on scoresheets so the list is 0 or 1)
- `ScoreField` moved from `judging.internal` to `judging` public API (needed for awards anonymized score-field rendering; Scoresheet.getFields() was already public)
- `EmailService.sendResultsAnnouncement` + new `ResultsAnnouncementType` enum (INITIAL_NO_CUSTOM, REPUBLISH_NO_CUSTOM, CUSTOM_MESSAGE); `SmtpEmailService` reuses the existing `email-base.html` template with type-specific subject/heading/body keys
- `EntryService.findEntrantUserIdsForDivision` (delegates new `EntryRepository.findDistinctUserIdsByDivisionId`)
- `ScoresheetPdfService` in judging public API — A4 portrait PDF generation with `AnonymizationLevel.ANONYMIZED` (Judge N) and `AnonymizationLevel.FULL` (judge name) modes; mirrors `LabelPdfService` pattern
- Validation constraints (`@NotNull`, `@NotBlank`) declared on `AwardsService` interface, not impl (CGLIB proxy + HV000151 LSP — per [project memory](memory/project_validated_interface_constraints.md))

#### Migrations: V28

### Cross-cutting

- **Comprehensive logging** added across all 3 modules (INFO for actions, DEBUG for queries/settings, WARN for blocked operations, ERROR for failures)

---

## Documentation Structure

```
docs/
├── SESSION_CONTEXT.md          ← This file (primary context for resuming work)
├── examples/                   ← Test & domain model examples (referenced by CLAUDE.md)
├── plans/
│   ├── 2026-03-02-entry-module-design.md  ← Retained as reference for future module designs
│   ├── 2026-03-10-profile-meadery-country-design.md  ← Design reference for profile/meadery/country
│   ├── 2026-03-10-email-sending-design.md  ← Email sending design (implemented)
│   ├── 2026-03-10-i18n-design.md          ← i18n design (implementation deferred)
│   ├── 2026-03-10-deployment-design.md    ← Deployment options evaluation (decision: DO App Platform)
│   ├── 2026-05-05-judging-module-design.md ← Judging module design (in progress, multi-session)
│   ├── 2026-05-12-awards-module-design.md  ← Awards module design (complete, ready for impl)
│   ├── 2026-05-12-awards-module-plan.md    ← Awards module implementation plan (13 TDD tasks)
│   └── deployment-checklist.md           ← Deployment reference: setup, release process, redeployment, rollback
├── reference/
│   ├── chip-competition-rules.md          ← CHIP competition rules (active reference)
│   ├── Short-version-of-MJP-scoring-sheet-V3.0.pdf ← Official MJP scoresheet (5 fields, max 100)
│   └── MEAD-GUIDELINES-2023.pdf           ← Full MJP mead guidelines (categories, styles)
├── specs/
│   ├── _template.md                       ← Template for new module specs
│   ├── judging.md                         ← Preliminary spec (post-rework naming)
│   └── awards.md                          ← Preliminary spec (post-rework naming)
└── walkthrough/
    ├── manual-test.md                  ← Dev environment test plan (seeded data, comprehensive)
    ├── post-deployment-test.md         ← Production fresh-deploy test plan (clean DB, Stage 1 + Stage 2; includes judging + awards smoke tests)
    └── post-deployment-v0.4.0.md       ← Version-specific upgrade check for v0.4.0 (assumes prod data exists; verifies V28–V31 migrations + new settings + pre-judging setup against existing competition)
```

---

## What's Next

### CURRENT (2026-05-25): Resume the walkthrough at §12.6.9

All five deferred items that paused the walkthrough are landed (see "Recently completed" below).

**State on disk** (`feature/judging-module`, 1189 tests passing, top commit `760058d`). Branch is pushed.

**Walkthrough position when paused:** Amadora at JUDGING with `M1A Panel A` ACTIVE (2 judges, entries assigned, scoresheets created). Profissional at JUDGING with split-category M1A + pre-staged M1B medal round (per dev seed). Amadora has a pre-staged `Medal — M1B` medal round added by hand in §12.6.8.

**Resume checklist:**

1. App running locally (user manages it — [[feedback_dev_server_user_managed]]). Mailpit at `localhost:8025`.
2. DB state: re-seed fresh — the deferred items reshape §12.5/§12.6 flow (especially #1 REGISTRATION_CLOSED setup and #3 medal-round entries). Walk §12.1 through §12.6.8 again to land at the same position.
3. Then proceed §12.6.9 → §12.6.10 → §12.7 → §12.8 → §12.9 (MyJudgingView — log in as **`judge3@example.com`** or **`judge4@example.com`**) → §12.10 → §12.11 → §12.12 → §12.13 → §12.14 → §12.15 → §12.16 → §12.17 → §12.18. Then §13 Awards.

After the walkthrough completes: code review, merge, v0.4.0 release.

---

### Recently completed (2026-05-25 session): five deferred items + late-RECEIVED tweak

All shipped in one session. Eight commits past `7831ecf`:

- `d5ff5c9` Late RECEIVED is manual-only: deleted `EntryReceivedScoresheetListener` + `EntryReceivedEvent`. Admin assigns final category + Rounds → Assign Entries; no more auto scoresheet creation.
- `945dbcc` **Item #1** Judging setup at REGISTRATION_CLOSED: `JudgingAdminView.beforeEnter` gate lowered; "Manage Judging" button visible from REG_CLOSED; new service gate on `startRound` requiring `>= JUDGING` (key `error.round.cannot-start-before-judging` × 5 locales). Walkthrough §12.4.2 / §12.5.1 / §12.6 / §12.6.4 reordered.
- `001fd95` **Item #5** Table → Round i18n rename across EN + PT/ES/IT/PL. 15 keys × 5 + `judge-table.title` + 2 comment lines. Keys unchanged (`error.judging-table.*`, `email.judging-table-ready.*`) to keep BRE strings stable.
- `6fa4e1c` **Item #2** Cross-division shared tables flag: `Competition.sharedTables` (default TRUE) + V30 migration. `startRound` cross-division busy-check by label when ON. Key `error.round.physical-table-busy-shared` × 5 locales. Settings checkbox + Physical Tables banner. Regression test for cross-division judge conflict.
- `8481a60` **Item #4** Scoresheet improvements: per-item comment TextArea per MJP field; comment language falls back to `User.preferredLanguage`; `meadName` hidden from judges (admins still see).
- `1e2b901` **Item #3a** V31 partial unique index `(judging_id, division_category_id) WHERE type = 'MEDAL'` backstops one-medal-round-per-category.
- `57a6784` **Item #3b** Cascade populates `medalRound.entries` per mode at READY transition. `findMedalRoundEntries` reads from `round.entries`, falls back to derivation when empty (test back-compat).
- `760058d` **Item #3c** `MedalRoundView` Assign Entries dialog mirrors Rounds-tab equivalent. EntryService + ScoresheetRepository injected.

Net: 1178 → 1189 tests, V29 → V31, EntryReceivedEvent removed.

---

<!-- Historical sections (round-model redesign 2026-05-24, completed Priority 1-6) removed 2026-05-25; full audit log in git history. -->

### Priority 7: Auto-close + deadline reminders (deferred)
- **Auto-close** — automatically advance division from REGISTRATION_OPEN → REGISTRATION_CLOSED
  when registration deadline passes (scheduled task)
- **Entrant deadline reminder** — notify entrants who have DRAFT entries when the registration
  deadline is approaching (e.g., 7 days, 3 days, 1 day before deadline)
- Other potential: entry received confirmation (when admin marks entry as RECEIVED), results published notification

### Priority 7b: MFA recovery codes (deferred)
**Status:** deferred at v0.3.0. Email-based MFA reset (the "Lost your device?" flow) ships as
the only recovery path for now. Recovery codes are the industry-standard primary self-service
fallback (Google, GitHub, AWS) — generated once at MFA setup, shown to the user, each redeemable
once. Worth adding when:
- Admins want recovery that does **not** depend on email integrity (defense against email-account
  compromise — currently the security ceiling is capped by email since password reset also goes
  through email)
- The MEADS install adds non-admin MFA-enabled accounts where users may not have reliable email

**Sketch when implementing:**
- Generate 8–10 one-time codes at `setupMfa()` / via "Regenerate Codes" button on ProfileView
- Store as bcrypt hashes (NOT plaintext) on a new `user_recovery_codes` table (V## migration)
- Show codes once in a copy-friendly format; user prints/saves them
- On `/mfa`, add "Use a recovery code" link next to "Lost your device?"
- Service method: `verifyAndConsumeRecoveryCode(userId, code)` — match against hashes, mark used
- Combine: keep email reset as the fallback when codes are also lost
- i18n: ~10 new keys
- Tests: unit for code generation/verification, repository test for persistence, UI test for the redeem flow

Reference: this conversation's MFA recovery discussion when the user picked email reset (2026-05-16).

### Priority 8: Full category constraint system (low priority — future competition)
Full field locking/validation based on category selection. Design doc: `docs/plans/2026-03-11-category-hints-design.md` (appendix).
Includes: sweetness locking (M1A→Dry, M1B→Medium, M1C→Sweet), ingredient restrictions (M1/M4E),
strength locking (M4S→Hydromel), ABV caps (M4S→7.5%), ABV→Strength derivation (universal),
carbonation locking (custom categories), and admin-configurable constraints for custom categories.
Requires: DB migration, admin UI for constraint config, cross-module data flow, server-side validation.

### Priority 9: Bitwarden compatibility on login page (deferred, low priority)
Bitwarden shows "This page is interfering with the Bitwarden experience. The Bitwarden inline
menu has been temporarily disabled as a safety measure."

**Real root cause (re-investigated 2026-05-03):** the previous Shadow-DOM/`elementFromPoint`
theory was wrong. Trigger is `bitwarden/clients` PR #17400 (merged Nov 2025), shipped in
Bitwarden browser extension late-2025/early-2026. The check fires when:
- **Top-layer hijack**: page has other top-layer items (popovers/dialogs) and Bitwarden's own
  inline-menu popover gets bumped, forcing it to call `hidePopover()` + `showPopover()` to
  reclaim position. **5 refreshes in 5 s → warning** (only `window.alert()` in entire browser source).
- **Popover attribute mutation**: page modifies `popover` attribute on Bitwarden's own button/list
  away from `"manual"`. **10 mutations in 5 s → warning.**
- **Page opacity check**: `<html>` or `<body>` computed opacity ≤ 0.6 → inline menu closes.

**Why our login page hits the top-layer threshold:** Vaadin's overlay/notification/tooltip system
moved to the native `popover` API in 24.5+ — well before our 25.1.3 upgrade. Any Vaadin field
with eager validation (we have `ValueChangeMode.EAGER` on email + password) and any tooltip/notification
churn produces a stream of popover open/close events. With Bitwarden's autofill button popover
also competing for the top layer, the 5-in-5s threshold is easy to hit.

**Why now:** not a Vaadin regression. The detection was added on the **Bitwarden side** in
Nov 2025; users started seeing it as their Bitwarden extension auto-updated. Any Vaadin 24.5+ app
shows this on form pages.

**autocomplete attributes** (`email`, `current-password`) already added to fields — kept for
correctness but they have no effect on this warning.

**Mitigations** (none done yet, all deferred):
1. Replace custom `LoginView` with Vaadin's `LoginForm` / `LoginOverlay`. Officially designed for
   password-manager compatibility (light-DOM inputs, attaches to body). Would change the page's
   visual layout though — user previously chose not to pursue.
2. Switch email/password fields to `ValueChangeMode.ON_BLUR` to cut validation-tooltip churn.
   Cheapest experiment; may or may not help depending on whether tooltips are the real source.
3. Drop the `Details` collapsible — render password row directly. Reduces DOM churn on first paint.
4. User-side workaround: add `meads.app` to Bitwarden Settings → Autofill → Blocked Domains.

**Status:** Functionality unaffected (autofill via Bitwarden popup/keyboard shortcut still works;
only the inline button is suppressed). Only affects admins using password login. Revisit if we
move to `LoginForm` for other reasons or if Bitwarden softens the threshold.

**References:**
- bitwarden/clients PR #17400 — `apps/browser/src/autofill/overlay/inline-menu/content/autofill-inline-menu-content.service.ts`
- community.bitwarden.com thread 92519 ("This page is interfering with the Bitwarden experience")

### Priority 11: Translate the MJP catalog categories (deferred, low priority)
The MJP catalog (`Category` rows seeded in `V7__create_categories_table_and_seed_mjp.sql`)
stores category `name` and `description` as English strings. The admin sees those English
names directly when picking from the catalog in `DivisionDetailView`'s Add Category dialog
(Catalog tab), and they propagate to per-division `DivisionCategory.name/description` on
selection. So even with the UI switched to PT/ES/IT/PL, the category strings stay English.

Scope when picked up:
- ~32 MJP categories (M1, M1A–M1F, M2, M2A–M2E, M3, M3A–M3B, M4, M4A, M4C, M4E, M4S, M5, M5A–M5E)
  × (name + description) × 5 locales ≈ 300 strings.
- Cleanest approach: i18n keys keyed by category code (e.g., `category.M1A.name`,
  `category.M1A.description`) rendered via `getTranslation()` at display time. Keeps the DB
  schema unchanged.
- On "Add catalog category", the admin sees the localized name in the picker; the row
  cloned into `division_categories` carries the localized strings at-clone-time. (Future
  refinement: keep `DivisionCategory` codes only and look up names via the same i18n keys.)
- Note: custom (admin-added) division categories stay free-text — i18n only applies to
  catalog rows.

### Priority 10: Statistics / metrics view (deferred, low priority)
A view — or a section within an existing admin view — surfacing aggregate competition
metrics suitable for presentations, reports, and award ceremonies. Candidate metrics:
- Entries per entrant country, per division, per category
- Entrant / meadery counts; distribution of entries per entrant
- Sweetness / strength / carbonation breakdowns
- Medal distribution per category (once judging data exists)
- Submission timeline — entries over the registration window

Open questions to settle when picked up: where it lives (new top-level `statistics`
module vs. a tab on `CompetitionDetailView` or `DivisionEntryAdminView`), per-division
vs. per-competition scope, visibility (admins only vs. a public page), and whether
charts are worth it (Vaadin Charts is a commercial add-on — plain Grids/Spans are
likely enough for v1). CSV/PDF export would make the numbers presentation-ready.

<!-- Completed priorities log removed 2026-05-25; full audit log in git history. -->

## Design decisions
- **Any user can set a password via "Forgot password?"** — even users without a role that
  requires one (e.g., regular entrants who only need magic links). This is allowed by design:
  it's the user's choice, introduces no security issue, and once set, magic links are blocked
  for them (defense in depth). No restriction needed.
- **Entry limits changeable only in DRAFT** — once a division advances past DRAFT,
  entry limits are locked. This prevents unfairness from mid-registration limit changes.
- **Flyway migrations modified in-place** — since the app is pre-deployment, existing
  migrations are edited rather than creating new ones. This keeps migration numbering clean.
- **Country field on User** — ISO 3166-1 alpha-2 code, validated in `UserService.updateProfile()`.
  ComboBox with `Locale.getISOCountries()` in UI. Webhook enrichment from shipping/billing address.
- **Meadery name stays on User profile only** — no per-entry override needed.
- **`meaderyNameRequired` on Division** — boolean flag, changeable only in DRAFT status.
- **Email SMTP failure resilience** — catch and log with fallback link, never crash UI actions.
- **Email rate limiting** — in-memory `ConcurrentHashMap<String, Instant>` keyed by `email:type`, 5-min cooldown (configurable via `app.email.rate-limit-minutes`). Only user-triggered emails are rate-limited. Daily counter logs WARN at threshold (`app.email.daily-warning-threshold=50`). Resets on date change.
- **Token validity (7 days)** — private constant in `SmtpEmailService`, not mentioned in email body.
- **Competition `contactEmail`** — optional field, shown in password setup and credit notification
  emails as visible footer contact. Saved via `CompetitionService.updateCompetitionContactEmail()`.
- **DevUserInitializer uses EmailService** — sends magic link emails via `EmailService.sendMagicLink()`
  at startup. Emails are captured by Mailpit in dev. Password users (admin, compadmin) still log to console.
- **`spring.thymeleaf.check-template-location=false`** — prevents Thymeleaf view resolver conflict
  with Vaadin (Thymeleaf used only for template rendering, not view resolution).
  MyEntriesView shows warning banner and blocks submit (all + individual) when required but missing.
- **Submission email is conditional** — `EntriesSubmittedEvent` only published when `creditBalance - activeEntries == 0`
  AND no DRAFT entries remain. Prevents email spam when entrant submits entries one by one.
- **Email template detail blocks** — `email-base.html` uses Thymeleaf-driven blocks for structured content:
  `orderReviewCompetition`/`orderReviewDivisions` (plain strings via `th:text`) for order alerts, and
  `entryLines` (`List<String>` via `th:each` + `th:text`) for submission summaries. No inline HTML or
  `th:utext` — all content is auto-escaped by Thymeleaf.

- **Role combination restriction** — Only JUDGE + ENTRANT combination is allowed in the same
  competition. All other multi-role combinations are rejected. Enforced at three levels:
  `CompetitionService.validateRoleCombination()` (participant management),
  `CompetitionService.ensureEntrantParticipant()` (webhook auto-assignment),
  `EntryService.addCredits()` and `WebhookService.processOrderPaid()` (credit paths).
- **Password requirement for competition admins** — Non-SYSTEM_ADMIN users with competition ADMIN
  role must have a password set. Admin views check in `beforeEnter()` and block access with a
  notification if no password. RootView skips `/my-competitions` redirect for passwordless comp
  admins to prevent redirect loops.
- **Access code scoping** — Access codes authenticate user identity (full account access), not
  per-competition sessions. Password gate on admin views provides sufficient separation. Per-competition
  scoping deferred to when multiple competitions exist.

### Known UX items (deferred)
- After failed credentials login, page reloads at `/login?error` and shows error notification,
  but password field is cleared (expected browser behavior for form POST). Not blocking.
- Competition dates allow past values — no validation prevents creating or editing a competition
  with start/end dates in the past. Add date validation (start date >= today on create,
  end date >= start date already enforced).
- Withdrawn entries have no way to revert status — consider adding an "undo withdraw" action
  (e.g., revert to SUBMITTED or DRAFT) for admin use.
- Credits can be added beyond the division's total entry limit — no validation prevents granting
  more credits than the entrant could ever use. Low priority since it's an admin action and
  unlikely in practice.

### Configuration
- **Properties reorganized** — `application.properties` contains only non-sensitive,
  environment-agnostic defaults (4 properties). Secrets and env-specific values live in
  profile-specific files (`application-dev.properties`, `application-prod.properties`).
  Test overrides in `src/test/resources/application.properties`.
- **Deployment env vars checklist** in `docs/plans/2026-03-10-deployment-design.md`.

---

## All Test Files (competition module — documents)

### Unit tests
- `CompetitionDocumentTest.java` — entity factory methods, validation (size, content type, name, URL), domain methods
- `CompetitionServiceTest.java` — document CRUD methods (addDocument, removeDocument, updateDocumentName, reorderDocuments, getDocuments, getDocument, deleteCompetition cleanup)

### Repository tests
- `CompetitionDocumentRepositoryTest.java` — save, find ordered, count, exists by name

### UI tests
- `CompetitionDetailViewTest.java` — Documents tab rendering, document grid display
- `MyEntriesViewTest.java` — competition documents section in entrant view

---

## All Test Files (entry module)

### Unit tests
- `EntryServiceTest.java` — product mapping CRUD + credit methods + entry CRUD + submission + limits (subcategory, main category, total)
- `WebhookServiceTest.java` — HMAC verification + processOrderPaid variants + CreditsAwardedEvent publication
- `LabelPdfServiceTest.java` — single/batch PDF generation, missing fields, QR code format, entry prefix handling
- `JumpsellerOrderTest.java` — entity domain methods
- `JumpsellerOrderLineItemTest.java` — entity domain methods
- `EntryTest.java` — entry entity domain methods (constructor, submit, markReceived, withdraw, updateDetails, assignFinalCategory, getEffectiveCategoryId, advanceStatus, revertStatus)
- `RegistrationClosedListenerTest.java` — event listener unit tests
- `OrderReviewNotificationListenerTest.java` — sends admin alert emails on order review event
- `SubmissionConfirmationListenerTest.java` — sends entrant confirmation on submission event
- `CreditNotificationListenerTest.java` — sends entrant credit notification on credits awarded event
- `EntryDivisionRevertGuardTest.java` — blocks revert to DRAFT when entries exist

### Repository tests
- `ProductMappingRepositoryTest.java`
- `JumpsellerOrderRepositoryTest.java`
- `JumpsellerOrderLineItemRepositoryTest.java`
- `EntryCreditRepositoryTest.java`
- `EntryRepositoryTest.java`

### Controller test
- `JumpsellerWebhookControllerTest.java` — standalone MockMvc (valid signature → 200, invalid → 401)

### Module integration test
- `EntryModuleTest.java` — bootstrap + full credit → entry → submit workflow

### UI tests
- `MyEntriesViewTest.java` — credits display, entry grid, authorization redirect, meadery name warning + submit blocking, download all labels button, download label for submitted entries, competition documents display, process info box, "Submit All Drafts" button
- `DivisionEntryAdminViewTest.java` — admin tabs rendering, meadery name + country columns, download all labels button

---

## Key Technical Notes

- Karibu TabSheet: content is lazy-loaded. Must call `tabSheet.setSelectedIndex(N)` before finding components
- Karibu component columns: buttons inside Grid `addComponentColumn` are not found by `_find(Button.class)`
- `Category` has a public constructor (code, name, description, scoringSystem, parentCode) and a protected no-arg for JPA
- `Select.setEmptySelectionAllowed(true)` passes `null` to `setItemLabelGenerator` — must handle null
- Service constructors are package-private (convention)
- `@DirtiesContext` required on UI tests that modify security context strategy
- `EntryCredit` is append-only ledger — balance computed as `SUM(amount)` via JPQL
- `WebhookService` constructor takes `@Value("${app.jumpseller.hooks-token}")` — property must exist
- Mutual exclusivity: user cannot have credits in two different divisions of same competition
- `@WebMvcTest` doesn't work in this Vaadin project — use `MockMvcBuilders.standaloneSetup(controller)` with `@ExtendWith(MockitoExtension.class)` instead
- String-based `Anchor` navigation for cross-module links (avoids Spring Modulith circular dependencies)
- Comprehensive logging: `@Slf4j` on all services, controllers, filters, listeners, guards
