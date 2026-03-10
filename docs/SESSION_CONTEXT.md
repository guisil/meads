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

**Branch:** `competition-module`
**Tests:** 431 passing (`mvn test -Dsurefire.useFile=false`)
**TDD workflow:** Two-tier (Full Cycle / Fast Cycle) — see `CLAUDE.md`

---

## Modules Implemented

### identity module (`app.meads.identity`)
- User entity (UUID, email, name, status, role, optional password, optional meaderyName, optional country)
- JWT magic link authentication + admin password login + access code login
- UserService (public API) — includes `updateProfile()` with ISO 3166-1 alpha-2 country validation
- SecurityConfig, UserListView (admin CRUD with meadery name + country fields)
- ProfileView (`/profile`) — self-edit for name, meadery name, country
- Password setup & reset: `SetPasswordView`, `setPasswordByToken()`, `generatePasswordSetupLink()`,
  `hasPassword()`, triggers on admin role assignment, "Forgot password?" on login, admin "Password Reset"
- **Status:** Complete

### competition module (`app.meads.competition`)
- **Depends on:** identity
- **Status:** Complete (fully implemented + code reviewed + scope rework done)

#### Entities (public API)
| Entity | Table | Description |
|--------|-------|-------------|
| `Competition` | `competitions` | Top-level: name, shortName (unique), dates, location, logo |
| `Division` | `divisions` | Sub-level: competitionId, name, shortName (unique per competition), scoringSystem, status, entry limits (per subcategory, per main category, total), entryPrefix, meaderyNameRequired |
| `Participant` | `participants` | Competition-scoped: userId, accessCode |
| `ParticipantRole` | `participant_roles` | Role per participant: JUDGE, STEWARD, ENTRANT, ADMIN |
| `Category` | `categories` | Read-only catalog: code, name, scoringSystem |
| `DivisionCategory` | `division_categories` | Per-division category with optional parent |

#### Key enums
- `DivisionStatus`: DRAFT → REGISTRATION_OPEN → REGISTRATION_CLOSED → JUDGING → DELIBERATION → RESULTS_PUBLISHED
- `CompetitionRole`: JUDGE, STEWARD, ENTRANT, ADMIN
- `ScoringSystem`: MJP

#### Service — `CompetitionService` (public API)
- Competition CRUD, Division CRUD, Participant management, Category management
- Authorization: `isAuthorizedForCompetition()`, `isAuthorizedForDivision()`
- `findCompetitionsByAdmin(userId)` — finds competitions where user has ADMIN participant role
- `revertDivisionStatus()` — one-step-back revert with guard interface pattern
- Entry limits (per subcategory, per main category, total) — DRAFT-only, enforced by EntryService
- Events: `DivisionStatusAdvancedEvent`

#### Views
- `CompetitionListView` (`/competitions`) — SYSTEM_ADMIN only, all competitions grid with CRUD
- `CompetitionDetailView` (`/competitions/:shortName`) — tabs: Divisions, Participants, Settings
- `DivisionDetailView` (`/competitions/:compShortName/divisions/:divShortName`) — tabs: Categories, Settings + "Manage Entries" button + "Advance/Revert Status" buttons
- `MyCompetitionsView` (`/my-competitions`) — `@PermitAll`, shows competitions where user is ADMIN

#### Migrations: V3–V8

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
- **EntryService** — Product mapping CRUD, credit management, entry CRUD, submission, limits enforcement (total, subcategory, main category)
- **WebhookService** — HMAC signature verification, `processOrderPaid` (JSON parsing, idempotency, mutual exclusivity, credit creation, country enrichment from shipping/billing address)

#### Events
- `CreditsAwardedEvent(divisionId, userId, amount, source)`
- `EntriesSubmittedEvent(divisionId, userId, entryCount)`

#### DTOs
- `EntrantCreditSummary(userId, email, name, creditBalance, entryCount)`

#### Views
- `EntrantOverviewView` (`/my-entries`) — cross-competition entrant hub, shows all divisions with credits/entries, auto-redirects to single division
- `MyEntriesView` (`/competitions/:compShortName/divisions/:divShortName/my-entries`) — entrant-facing, credits + limits display, entry grid with status badges/Final Category/Actions (view/edit/submit)/filtering/sorting, add/edit dialog (full-width fields), submit all, meadery name required warning + submit blocking
- `DivisionEntryAdminView` (`/competitions/:compShortName/divisions/:divShortName/entry-admin`) — admin tabs: Credits, Entries (with Meadery/Country columns), Products, Orders

#### REST
- `JumpsellerWebhookController` — `POST /api/webhooks/jumpseller/order-paid` (HMAC-verified)

#### Guards
- `EntryDivisionRevertGuard` — blocks REGISTRATION_OPEN → DRAFT revert when entries exist

#### Event Listener
- `RegistrationClosedListener` — skeleton for `DivisionStatusAdvancedEvent` (REGISTRATION_CLOSED)

#### Changes to other modules
- `SecurityConfig` — separate `SecurityFilterChain` with `@Order(1)` for webhook API (CSRF disabled, permitAll)
- `User.java` — added `meaderyName` and `country` fields (now in V2)
- `Division.java` — added `maxEntriesPerSubcategory`, `maxEntriesPerMainCategory`, `maxEntriesTotal`, `entryPrefix`, `meaderyNameRequired`
- `DivisionDetailView` — "Manage Entries" button, entry prefix + entry limits in Settings tab (DRAFT-only for limits), meaderyNameRequired checkbox (DRAFT-only)
- `MainLayout` — "My Profile" nav item for all authenticated users
- `application.properties` — added `app.jumpseller.hooks-token`

#### Migrations: V9–V13

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
│   └── 2026-03-10-profile-meadery-country-design.md  ← Design reference for profile/meadery/country
├── reference/
│   └── chip-competition-rules.md          ← CHIP competition rules (active reference)
├── specs/
│   ├── _template.md                       ← Template for new module specs
│   ├── judging.md                         ← Preliminary spec (post-rework naming)
│   └── awards.md                          ← Preliminary spec (post-rework naming)
└── walkthrough/
    └── manual-test.md                  ← Living test plan (UI + API, must be updated with every change)
```

---

## What's Next

### Priority 1: Internationalization (i18n)
Investigate how to best approach i18n for the application. Initial target languages are
English and Portuguese, but the design should support adding more languages later.
Key areas to consider:
- **UI labels and messages** — Vaadin's built-in i18n support (`I18NProvider`), resource
  bundles, how to wire it up with Spring Boot
- **Database-stored data** — mead categories (`categories` table with code/name) are the
  most obvious candidate for translated content. Evaluate whether other DB data needs
  translation (e.g., competition names, division names — probably not, as these are
  user-defined). Options: separate translation table, JSON column, or code-based lookup.
- **Enums with display names** — `Sweetness`, `Strength`, `Carbonation`, `EntryStatus`,
  `DivisionStatus`, etc. currently have `getDisplayName()` returning hardcoded English.
  These should use message bundles instead.
- **Language selection** — default based on the user's country (if set and a matching
  translation is available), with the ability to override. Store preference on the user
  entity or rely on browser locale with a manual override.
- **Scope the investigation** — what's the minimum viable i18n that unblocks Portuguese
  support without over-engineering?

### Priority 2: Manual walkthrough (full redo)
Redo the **entire** manual-test walkthrough from Section 1. Previous partial run covered
Sections 2–9; this is a fresh pass through all sections (1–13) to validate the current
state end-to-end, including recent entry limits UI, logging, and all accumulated changes.
May produce bug fixes or UX improvements.

### Priority 3: Deployment planning
Evaluate cloud providers and services for first deployment:
- **Managed PostgreSQL** with automatic backups — **top priority**, data must never be lost
- **Log management** — logs properly stored and rolled, searchable
- **Email sending** — reliable delivery for magic links, password resets, notifications
  (low volume but sometimes bursty)
- **Secrets management** — at minimum `INITIAL_ADMIN_EMAIL` + `INITIAL_ADMIN_PASSWORD`
  for bootstrapping, plus JWT secret, DB credentials, Jumpseller hooks token
- **Cost considerations:** The app only needs to be online for a few months at a time.
  Going for multiple AWS services (RDS, ECS, SES, Secrets Manager, etc.) could be overkill
  and expensive. Finding a good balance between cost and reliability is key. A simpler
  PaaS (Railway, Render, Fly.io) with a managed DB add-on might be more appropriate than
  full AWS infrastructure. Premium high-availability is not required — reliable uptime is
  enough. The non-negotiable is never losing data (hence managed DB with backups).

### Priority 4: Configuration audit
Review `application.properties` and profile-specific files:
- Which properties should be env vars vs. config files vs. secrets?
- Which belong to specific profiles (`dev`, `prod`, `test`)?
- Align with chosen cloud provider's configuration model

### Priority 5: Email sending implementation
Implement actual email delivery (currently magic links and password reset links are
logged to console). Mechanism depends on deployment choice (SES, SMTP, etc.).
Spring Boot has `spring-boot-starter-mail` — evaluate if that's sufficient.
The current console-logging behavior should be kept for the `dev` profile for testing.

### Priority 6: Entry submission labels (PDF)
When a mead entry is submitted, the entrant should be able to download a printable
label PDF:
- **Format:** 1 page containing 3 identical label copies (for 3 bottles)
- **Content:** Mead info (entry number, category, sweetness, strength, carbonation, etc.)
  + QR code (containing at minimum the entry ID, possibly competition/division context)
- **Implementation:** Template-based PDF generation (e.g., iText, OpenPDF, or Apache PDFBox)
- **UX:** Download button/link in MyEntriesView after submission

### Priority 7: Competition documents
Decide how to handle downloadable documents per competition (rules, guidelines, etc.):
- Options: stored in DB (BLOB), external file storage (S3), or just external links
- Where to display: competition detail page, possibly entrant-facing views
- Consider storage cost, upload UX, and simplicity

### After these priorities: Resume planned work
7. **Judging module** — design and implementation
8. **Awards module** — design and implementation

---

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
  MyEntriesView shows warning banner and blocks submit (all + individual) when required but missing.

### Known UX items (deferred)
- After failed credentials login, page reloads at `/login?error` and shows error notification,
  but password field is cleared (expected browser behavior for form POST). Not blocking.

---

## All Test Files (entry module)

### Unit tests
- `EntryServiceTest.java` — product mapping CRUD + credit methods + entry CRUD + submission + limits (subcategory, main category, total)
- `WebhookServiceTest.java` — HMAC verification + processOrderPaid variants
- `JumpsellerOrderTest.java` — entity domain methods
- `JumpsellerOrderLineItemTest.java` — entity domain methods
- `EntryTest.java` — entry entity domain methods (constructor, submit, markReceived, withdraw, updateDetails, assignFinalCategory, getEffectiveCategoryId)
- `RegistrationClosedListenerTest.java` — event listener unit tests
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
- `MyEntriesViewTest.java` — credits display, entry grid, authorization redirect, meadery name warning + submit blocking
- `DivisionEntryAdminViewTest.java` — admin tabs rendering, meadery name + country columns

---

## Key Technical Notes

- Karibu TabSheet: content is lazy-loaded. Must call `tabSheet.setSelectedIndex(N)` before finding components
- Karibu component columns: buttons inside Grid `addComponentColumn` are not found by `_find(Button.class)`
- `Category` has only protected no-arg constructor — use `Mockito.mock()` in unit tests
- `Select.setEmptySelectionAllowed(true)` passes `null` to `setItemLabelGenerator` — must handle null
- Service constructors are package-private (convention)
- `@DirtiesContext` required on UI tests that modify security context strategy
- `EntryCredit` is append-only ledger — balance computed as `SUM(amount)` via JPQL
- `WebhookService` constructor takes `@Value("${app.jumpseller.hooks-token}")` — property must exist
- Mutual exclusivity: user cannot have credits in two different divisions of same competition
- `@WebMvcTest` doesn't work in this Vaadin project — use `MockMvcBuilders.standaloneSetup(controller)` with `@ExtendWith(MockitoExtension.class)` instead
- String-based `Anchor` navigation for cross-module links (avoids Spring Modulith circular dependencies)
- Comprehensive logging: `@Slf4j` on all services, controllers, filters, listeners, guards
