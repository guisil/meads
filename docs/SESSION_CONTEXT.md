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
**Tests:** 408 passing (`mvn test -Dsurefire.useFile=false`)
**TDD workflow:** Two-tier (Full Cycle / Fast Cycle) — see `CLAUDE.md`

---

## Modules Implemented

### identity module (`app.meads.identity`)
- User entity (UUID, email, name, status, role, optional password, optional meaderyName)
- JWT magic link authentication + admin password login + access code login
- UserService (public API), SecurityConfig, UserListView (admin CRUD)
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
| `Division` | `divisions` | Sub-level: competitionId, name, shortName (unique per competition), scoringSystem, status, entry limits, entryPrefix |
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
| `JumpsellerOrder` | `jumpseller_orders` | V10 | Webhook order storage, idempotency |
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
- **EntryService** — Product mapping CRUD, credit management, entry CRUD, submission, limits enforcement
- **WebhookService** — HMAC signature verification, `processOrderPaid` (JSON parsing, idempotency, mutual exclusivity, credit creation)

#### Events
- `CreditsAwardedEvent(divisionId, userId, amount, source)`
- `EntriesSubmittedEvent(divisionId, userId, entryCount)`

#### DTOs
- `EntrantCreditSummary(userId, email, name, creditBalance, entryCount)`

#### Views
- `MyEntriesView` (`/competitions/:compShortName/divisions/:divShortName/my-entries`) — entrant-facing, credits display, entry grid, add/edit dialog, submit all
- `DivisionEntryAdminView` (`/competitions/:compShortName/divisions/:divShortName/entry-admin`) — admin tabs: Credits, Entries, Products, Orders

#### REST
- `JumpsellerWebhookController` — `POST /api/webhooks/jumpseller/order-paid` (HMAC-verified)

#### Guards
- `EntryDivisionRevertGuard` — blocks REGISTRATION_OPEN → DRAFT revert when entries exist

#### Event Listener
- `RegistrationClosedListener` — skeleton for `DivisionStatusAdvancedEvent` (REGISTRATION_CLOSED)

#### Changes to other modules
- `SecurityConfig` — separate `SecurityFilterChain` with `@Order(1)` for webhook API (CSRF disabled, permitAll)
- `User.java` — added `meaderyName` field (V14)
- `Division.java` — added `maxEntriesPerSubcategory`, `maxEntriesPerMainCategory` (V15), `entryPrefix` (V16)
- `DivisionDetailView` — added "Manage Entries" Anchor link (string-based, no entry module import), entry prefix in Settings tab
- `application.properties` — added `app.jumpseller.hooks-token`

#### Migrations: V9–V16

---

## Documentation Structure

```
docs/
├── SESSION_CONTEXT.md          ← This file (primary context for resuming work)
├── examples/                   ← Test & domain model examples (referenced by CLAUDE.md)
├── plans/
│   └── 2026-03-02-entry-module-design.md  ← Retained as reference for future module designs
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

1. **Manual walkthrough** — Continue from Section 9 (Webhook API testing).
   Sections 2–8 are done. Continue through Section 12 (multi-role & cross-competition edge cases).
3. **Code review** of both competition and entry modules (slice by slice)
4. **Test review** (guided, with UI verification) of both modules
5. **Judging module** — design and implementation

### Recent changes (this session)
- **Entry admin enhancements (DivisionEntryAdminView):**
  - All 4 tabs: filtering, sorting, action buttons (edit/delete/withdraw icons)
  - Credits tab: Name first (flexGrow 2) / Email (flexGrow 3), filter, edit/remove credits dialogs
  - Entries tab: entry number with configurable prefix (e.g. "AMA-1"), filter, edit/delete/withdraw dialogs
  - Products tab: edit/delete dialogs for product mappings
  - Orders tab: filter, edit admin note dialog
  - Entry prefix: configurable per division (up to 5 chars), in Division Settings tab
  - `Division.entryPrefix` field + V16 migration
  - `CompetitionService.updateDivision()`: added `entryPrefix` parameter
  - `EntryService.findOrdersByDivision()`: new method to query orders via line items
  - DevDataInitializer: entry prefixes ("AMA"/"PRO"), 2 example webhook orders
  - Fixed FK violation in `WebhookService.processOrderPaid()`: order saved before line items
  - Fixed `JumpsellerOrder`/`JumpsellerOrderLineItem` timestamp: `createdAt` set in constructor (not `@PrePersist`) to avoid null on merge
  - DivisionDetailView: categories auto-expand, Add Category dialog button alignment, breadcrumb refresh after settings save
- **Division status revert with guard pattern:**
  - `DivisionRevertGuard` interface in competition module public API — modules implement to block unsafe reverts
  - `Division.revertStatus()` domain method (mirror of `advanceStatus()`)
  - `DivisionStatus.previous()` method (mirror of `next()`)
  - `CompetitionService.revertDivisionStatus()` — calls all guards before reverting
  - `EntryDivisionRevertGuard` in entry module — blocks REGISTRATION_OPEN → DRAFT when entries exist
  - UI: "Revert Status" button in DivisionDetailView header (hidden when DRAFT)
  - UI: Revert icon (BACKWARDS) in CompetitionDetailView divisions grid (hidden when DRAFT)
  - Both with confirmation dialogs
- **UI polish (Section 7 walkthrough):**
  - Categories TreeGrid: "Remove" text button → X icon with tooltip + confirmation dialog
  - Participants grid: added confirmation dialog before removal
  - Icon semantics: TRASH = permanent deletion (with confirmation), CLOSE (X) = disassociation (now also with confirmation)
  - Categories Description column: increased flex grow (2) + tooltip on hover for long descriptions
  - All 11 grids across 6 views: `setAllRowsVisible(true)` — grids expand to fit content, no fixed 400px height
  - "Manage Entries": changed from Anchor link to Button (matches "Advance Status" style)
  - Division settings: Name and Short Name editable at any status (not just DRAFT); Scoring System still locked to DRAFT
  - `Division.updateDetails()`: relaxed restriction — only scoring system changes require DRAFT
- **Previous session changes:**
  - Login and Set Password views: constrained form width (`setWidth("auto")`, centered)
  - User dialog: Save/Cancel buttons placed side by side (HorizontalLayout)
  - All dialogs: standardized button order (Cancel left, Save right) and label ("Save" everywhere, no "Create")
  - Logo upload: limit raised from 512KB to 2.5MB, added `fileRejectedListener` for error feedback
  - Bug fix: `updateCompetition`/`updateCompetitionLogo` now use `requireAuthorized` (allows competition ADMIN, not just SYSTEM_ADMIN)
  - Bug fix: CompetitionDetailView header/breadcrumb now refresh after settings save
  - Dev data: CHIP 2026 location changed to "Amarante, Portugal", dates to June 11-14
  - Categories TreeGrid: reduced Code column width (100px, no flex grow)
  - MJP categories: fixed V7 migration to match official MJP Guidelines 2023 — removed M1V/M4G/M4P/M4Z, added M4B (Historical Mead)
  - Grid ordering fix, URL slugs (short names), friendly redirects,
    SetPasswordView Enter key, Actions column width, magic link blocked for password users,
    password setup & reset (all 3 phases), compadmin dev user, MyCompetitionsView, MainLayout
    sidebar, create user dialog status field removed

### Design decisions
- **Any user can set a password via "Forgot password?"** — even users without a role that
  requires one (e.g., regular entrants who only need magic links). This is allowed by design:
  it's the user's choice, introduces no security issue, and once set, magic links are blocked
  for them (defense in depth). No restriction needed.

### Known UX items (deferred)
- After failed credentials login, page reloads at `/login?error` and shows error notification,
  but password field is cleared (expected browser behavior for form POST). Not blocking.

---

## All Test Files (entry module)

### Unit tests
- `EntryServiceTest.java` — product mapping CRUD + credit methods + entry CRUD + submission + limits
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
- `MyEntriesViewTest.java` — credits display, entry grid, authorization redirect
- `DivisionEntryAdminViewTest.java` — admin tabs rendering

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
