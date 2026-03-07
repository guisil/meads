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
**Tests:** 369 passing (`mvn test -Dsurefire.useFile=false`)
**TDD workflow:** Two-tier (Full Cycle / Fast Cycle) — see `CLAUDE.md`

---

## Modules Implemented

### identity module (`app.meads.identity`)
- User entity (UUID, email, name, status, role, optional password, optional meaderyName)
- JWT magic link authentication + admin password login + access code login
- UserService (public API), SecurityConfig, UserListView (admin CRUD)
- **Status:** Complete

### competition module (`app.meads.competition`)
- **Depends on:** identity
- **Status:** Complete (fully implemented + code reviewed + scope rework done)

#### Entities (public API)
| Entity | Table | Description |
|--------|-------|-------------|
| `Competition` | `competitions` | Top-level: name, dates, location, logo |
| `Division` | `divisions` | Sub-level: competitionId, name, scoringSystem, status, entry limits |
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
- Events: `DivisionStatusAdvancedEvent`

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
- `MyEntriesView` (`/divisions/:divisionId/my-entries`) — entrant-facing, credits display, entry grid, add/edit dialog, submit all
- `DivisionEntryAdminView` (`/divisions/:divisionId/entry-admin`) — admin tabs: Credits, Entries, Products, Orders

#### REST
- `JumpsellerWebhookController` — `POST /api/webhooks/jumpseller/order-paid` (HMAC-verified)

#### Event Listener
- `RegistrationClosedListener` — skeleton for `DivisionStatusAdvancedEvent` (REGISTRATION_CLOSED)

#### Changes to other modules
- `SecurityConfig` — added `.requestMatchers("/api/webhooks/**").permitAll()`
- `User.java` — added `meaderyName` field (V14)
- `Division.java` — added `maxEntriesPerSubcategory`, `maxEntriesPerMainCategory` (V15)
- `DivisionDetailView` — added "Manage Entries" Anchor link (string-based, no entry module import)
- `application.properties` — added `app.jumpseller.hooks-token`

#### Migrations: V9–V15

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
    └── manual-ui-test.md                  ← Living test plan (must be updated with every UI change)
```

---

## What's Next

1. **Manual UI walkthrough** — Resume from Section 2 (failed login, step 2.7 in walkthrough).
   Sections 2.1–2.6 verified OK.
2. **Code review** of both competition and entry modules (slice by slice)
3. **Test review** (guided, with UI verification) of both modules
4. **Judging module** — design and implementation

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
