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
**Tests:** 264 passing (`mvn test -Dsurefire.useFile=false`)
**TDD workflow:** Two-tier (Full Cycle / Fast Cycle) — see `CLAUDE.md` and
`.claude/skills/tdd-cycle.md`

---

## Modules Implemented

### identity module (`app.meads.identity`)
- User entity (UUID, email, name, status, role, optional password)
- JWT magic link authentication + admin password login + access code login
- UserService (public API), SecurityConfig, UserListView (admin CRUD)

### competition module (`app.meads.competition`)
- **Depends on:** identity

#### Entities (public API — `app.meads.competition`)
| Entity | Key fields | Table |
|--------|-----------|-------|
| `MeadEvent` | name, startDate, endDate, location, logo | `mead_events` |
| `Competition` | eventId, name, scoringSystem, status | `competitions` |
| `EventParticipant` | eventId, userId, accessCode | `event_participants` |
| `CompetitionParticipant` | competitionId, eventParticipantId, role | `competition_participants` |
| `Category` | code, name, description, scoringSystem | `categories` (read-only catalog) |
| `CompetitionCategory` | competitionId, catalogCategoryId, code, name, description, parentId, sortOrder | `competition_categories` |

#### Enums (public API)
- `CompetitionStatus`: DRAFT → REGISTRATION_OPEN → REGISTRATION_CLOSED → JUDGING → DELIBERATION → RESULTS_PUBLISHED
  - `allowsCategoryModification()` — true for DRAFT, REGISTRATION_OPEN
- `CompetitionRole`: JUDGE, STEWARD, ENTRANT, COMPETITION_ADMIN
- `ScoringSystem`: MJP

#### Service — `CompetitionService` (public API)
Key methods:
- **MeadEvents:** createMeadEvent, findMeadEventById, findAllMeadEvents, updateMeadEvent, updateMeadEventLogo, deleteMeadEvent
- **Competitions:** createCompetition (auto-inits categories), findById, updateCompetition, advanceStatus, deleteCompetition
- **Participants:** addParticipant (package-private), addParticipantByEmail, removeParticipant, addParticipantToAllCompetitions, findParticipantsByCompetition, findEventParticipantsByEvent
- **Categories:** findCompetitionCategories, addCatalogCategory, addCustomCategory, updateCompetitionCategory, removeCompetitionCategory (cascades children), findAvailableCatalogCategories
- **Auth:** isAuthorizedForCompetition, findAuthorizedCompetitions
- Private helpers: requireSystemAdmin, requireAuthorized(Competition, UUID), isAuthorized(Competition, UUID), generateUniqueAccessCode, findOrCreateEventParticipant, initializeCategories

#### Repositories (internal — `app.meads.competition.internal`)
- `MeadEventRepository`
- `CompetitionRepository` — findByEventId
- `EventParticipantRepository` — findByEventId, findByEventIdAndUserId, findByAccessCode, existsByAccessCode
- `CompetitionParticipantRepository` — findByCompetitionId, existsByCompetitionIdAndEventParticipantIdAndRole, findByCompetitionIdAndEventParticipantId, findByEventParticipantIdAndRole
- `CategoryRepository` — findByScoringSystem
- `CompetitionCategoryRepository` — findByCompetitionIdOrderByCode, existsByCompetitionIdAndCode, existsByCompetitionIdAndCatalogCategoryId, findByParentId

#### Views (internal)
- `MeadEventListView` — `/events`, `@RolesAllowed("SYSTEM_ADMIN")`, CRUD grid for events with logo upload
- `CompetitionListView` — `/events/:eventId/competitions`, `@PermitAll` + beforeEnter auth, grid filtered by `findAuthorizedCompetitions()`, "Create Competition"/"Add Participant to All" buttons (SYSTEM_ADMIN only), Delete/Advance actions per competition
- `CompetitionDetailView` — `/competitions/:competitionId`, `@PermitAll` + beforeEnter auth, TabSheet with:
  - **Participants tab:** Grid with Name/Email/Role/Access Code/Remove columns, "Add Participant" button → dialog with email + role
  - **Categories tab:** `TreeGrid<CompetitionCategory>` with Code (hierarchy)/Name/Description/Remove columns, "Add Category" button → two-tab dialog (From Catalog / Custom with optional parent)
  - **Settings tab:** Name, Scoring System, Status fields, Save button (DRAFT only)

#### Migrations (V3–V8)
- V3: mead_events table
- V4: competitions table
- V5: event_participants table (V9 merged back in — status column removed pre-deployment)
- V6: competition_participants table
- V7: categories table + MJP seed data (18 categories)
- V8: competition_categories table

---

## Code Review — Completed

The competition module code review was completed across 6 vertical slices:

1. **MeadEvents** — Renamed Event→MeadEvent, added logo upload/remove, added logo display in CompetitionListView
2. **Competitions** — Added deleteCompetition with cascade cleanup (participants, categories)
3. **Participants** — Made addParticipant package-private, extracted findOrCreateEventParticipant helper, replaced event-level withdraw with competition-level removeParticipant, added Access Code column, added "Add Participant to All" UI
4. **Categories** — Removed dead code, changed sort by code, added updateCompetitionCategory (catalog→custom conversion), converted Grid to TreeGrid for hierarchy
5. **Authorization** — Extracted isAuthorized boolean helper, requireAuthorized now accepts Competition object (eliminates redundant findById), removed exception-based control flow
6. **Access codes** — Removed dead withdraw/CompetitionParticipantStatus, added access code uniqueness check with retry, merged V9 into V5

---

## Next Session Plan

### Phase 1: Entry Module Design Discussion — COMPLETED

Design doc written: `docs/plans/2026-03-02-entry-module-design.md`

Key decisions made:
- 5 entities: ProductMapping, JumpsellerOrder, JumpsellerOrderLineItem, EntryCredit, Entry
- 6 enums: EntryStatus, Sweetness, Strength, Carbonation, OrderStatus, LineItemStatus
- 6 migrations: V9–V14
- 2 services: EntryService (credits + entries), WebhookService (Jumpseller webhook)
- 2 views: MyEntriesView (entrant), CompetitionEntryAdminView (admin, separate route)
- Append-only credit ledger (balance = SUM of amounts)
- Mutual exclusivity enforced at credit time (per event)
- Webhook returns 200 after signature verification, errors in DB statuses
- Entry admin is a separate view (not CompetitionDetailView tab) to avoid circular module dependency
- User.meaderyName added to identity module (V14)
- SecurityConfig updated for /api/webhooks/** permitAll

### Phase 2: Implementation (TDD) — NEXT

After design discussion, implement following the multi-layer TDD sequence from CLAUDE.md:
1. Unit tests for domain logic
2. Repository tests for persistence
3. Module integration tests
4. UI tests (Karibu)

### Phase 3: Code Review (slice by slice)

After entry module is complete, do another thorough code review:
- Walk through the entry module in vertical slices (same approach as competition review)
- Cover: webhook endpoint, credits, entries, admin views, authorization
- Fix issues found during review

### Phase 4: Test Review (guided, with UI verification)

After code review, do a guided test review:
- For each test: explain what behavior it verifies
- User checks the test code
- User verifies the behavior in the running UI
- Identify gaps (behaviors not tested, edge cases missing)
- Cover BOTH competition and entry modules

---

## Key Technical Notes

- Karibu TabSheet: content is lazy-loaded. Must call `tabSheet.setSelectedIndex(N)` before finding components in non-default tabs
- Karibu component columns: buttons inside Grid `addComponentColumn` are not found by `_find(Button.class)` — they render lazily per row
- `Category` has only a protected no-arg constructor (read-only catalog entity). In unit tests, use `Mockito.mock(Category.class)` with stubbed `getId()` when distinct IDs are needed
- `Select.setEmptySelectionAllowed(true)` passes `null` to `setItemLabelGenerator` — must handle null in the lambda
- `CompetitionService` constructor is package-private (convention)
- `@DirtiesContext` required on UI tests that modify security context strategy
- TreeGrid: use `addHierarchyColumn()` for expand/collapse column, `setItems(roots, childrenProvider)` for data
- `HierarchicalQuery` for asserting TreeGrid data in tests (not `getGenericDataView()`)
