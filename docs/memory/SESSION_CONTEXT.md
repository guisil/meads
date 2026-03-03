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
**Tests:** 301 passing (`mvn test -Dsurefire.useFile=false`)
**TDD workflow:** Two-tier (Full Cycle / Fast Cycle) — see `CLAUDE.md`

---

## Modules Implemented

### identity module (`app.meads.identity`)
- User entity (UUID, email, name, status, role, optional password)
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
| `Division` | `divisions` | Sub-level: competitionId, name, scoringSystem, status |
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

### entry module (`app.meads.entry`) — IN PROGRESS

- **Depends on:** competition, identity
- **Status:** Phases 0–4 complete, Phase 5 next

#### Entities implemented (public API)
| Entity | Table | Migration | Description |
|--------|-------|-----------|-------------|
| `ProductMapping` | `product_mappings` | V9 | Jumpseller product → division mapping |
| `JumpsellerOrder` | `jumpseller_orders` | V10 | Webhook order storage, idempotency |
| `JumpsellerOrderLineItem` | `jumpseller_order_line_items` | V11 | Per-product line items |
| `EntryCredit` | `entry_credits` | V12 | Append-only credit ledger |

#### Enums implemented
- `EntryStatus`: DRAFT, SUBMITTED, RECEIVED, WITHDRAWN
- `Sweetness`: DRY, MEDIUM, SWEET
- `Strength`: HYDROMEL, STANDARD, SACK
- `Carbonation`: STILL, PETILLANT, SPARKLING
- `OrderStatus`: PROCESSED, PARTIALLY_PROCESSED, NEEDS_REVIEW, UNPROCESSED
- `LineItemStatus`: PROCESSED, NEEDS_REVIEW, IGNORED, UNPROCESSED

#### Services implemented
- **EntryService** — Product mapping CRUD, credit management (getCreditBalance, addCredits, removeCredits, hasCreditsInOtherDivision)
- **WebhookService** — HMAC signature verification, `processOrderPaid` (JSON parsing, idempotency, mutual exclusivity, credit creation)

#### Events
- `CreditsAwardedEvent(divisionId, userId, amount, source)`

#### Entities NOT yet implemented
- `Entry` (entries table, V13) — Phase 5
- `User.meaderyName` field (V14) — Phase 7

#### Config added
- `app.jumpseller.hooks-token` in `application.properties`

---

## What's Next — Resume at Phase 5

**Design doc:** `docs/plans/2026-03-02-entry-module-design.md`

### Phase 5 — Entry Entity (9 TDD cycles)
1. Unit test: create entry (constructor, DRAFT status)
2. Unit test: submit() — DRAFT → SUBMITTED
3. Unit test: submit() rejects non-DRAFT
4. Unit test: markReceived() — SUBMITTED → RECEIVED
5. Unit test: withdraw() from various statuses
6. Unit test: updateDetails() — only DRAFT
7. Unit test: assignFinalCategory()
8. Unit test: getEffectiveCategoryId()
9. Repository test → drives V13 migration

### Phase 6 — Entry Service (17 cycles)
Entry creation, updates, deletion, submission, limits enforcement, admin operations.

### Phase 7 — User.meaderyName (3 cycles)
Add meaderyName field to User entity + V14 migration.

### Phase 8 — Webhook REST Controller (2 cycles)
MockMvc tests + SecurityConfig change for `/api/webhooks/**`.

### Phase 9 — Module Integration Test (1 cycle)
Full context, real DB, credit → entry workflow.

### Phase 10 — Event Listener (1 cycle)
DivisionStatusAdvancedEvent listener skeleton.

### Phase 11 — Views (4 cycles)
MyEntriesView, DivisionEntryAdminView, navigation links.

---

## All Test Files (entry module — current)

### Unit tests
- `EntryServiceTest.java` — 17 tests: product mapping CRUD + credit methods
- `WebhookServiceTest.java` — 9 tests: HMAC verification + processOrderPaid variants
- `JumpsellerOrderTest.java` — 5 tests: entity domain methods
- `JumpsellerOrderLineItemTest.java` — 4 tests: entity domain methods

### Repository tests
- `ProductMappingRepositoryTest.java` — 4 tests
- `JumpsellerOrderRepositoryTest.java` — 3 tests
- `JumpsellerOrderLineItemRepositoryTest.java` — 3 tests
- `EntryCreditRepositoryTest.java` — 4 tests

### Module test
- `EntryModuleTest.java` — bootstrap test

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
