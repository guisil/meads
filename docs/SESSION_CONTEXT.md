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
**Tests:** 257 passing (`mvn test -Dsurefire.useFile=false`)
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
| `MeadEvent` | name, startDate, endDate, location, logo | `events` |
| `Competition` | eventId, name, scoringSystem, status | `competitions` |
| `EventParticipant` | eventId, userId, accessCode, status | `event_participants` |
| `CompetitionParticipant` | competitionId, eventParticipantId, role | `competition_participants` |
| `Category` | code, name, description, scoringSystem | `categories` (read-only catalog) |
| `CompetitionCategory` | competitionId, catalogCategoryId, code, name, description, parentId, sortOrder | `competition_categories` |

#### Enums (public API)
- `CompetitionStatus`: DRAFT → REGISTRATION_OPEN → REGISTRATION_CLOSED → JUDGING → DELIBERATION → RESULTS_PUBLISHED
  - `allowsCategoryModification()` — true for DRAFT, REGISTRATION_OPEN
- `CompetitionRole`: JUDGE, STEWARD, ENTRANT, COMPETITION_ADMIN
- `CompetitionParticipantStatus`: ACTIVE, WITHDRAWN
- `ScoringSystem`: MJP

#### Service — `CompetitionService` (public API)
Key methods:
- **MeadEvents:** createMeadEvent, findMeadEventById, findAllMeadEvents, updateMeadEvent, updateMeadEventLogo, deleteMeadEvent
- **Competitions:** createCompetition (auto-inits categories), findById, findByEvent, updateCompetition, advanceStatus
- **Participants:** addParticipant, addParticipantByEmail, withdrawParticipant, addParticipantToAllCompetitions, findParticipantsByCompetition, findEventParticipantsByEvent
- **Categories:** findCategoriesByScoringSystem, findCompetitionCategories, addCatalogCategory, addCustomCategory, removeCompetitionCategory (cascades children), findAvailableCatalogCategories, initializeCompetitionCategories
- **Auth:** isAuthorizedForCompetition, findAuthorizedCompetitions
- Private helpers: requireSystemAdmin, requireAuthorized, generateAccessCode, initializeCategories

#### Repositories (internal — `app.meads.competition.internal`)
- `MeadEventRepository`
- `CompetitionRepository` — findByEventId
- `EventParticipantRepository` — findByEventId, findByEventIdAndUserId, findByUserId
- `CompetitionParticipantRepository` — findByCompetitionId, existsByCompetitionIdAndEventParticipantIdAndRole, findByCompetitionIdAndEventParticipantId, findByEventParticipantIdAndRole, existsByEventParticipantIdAndRole
- `CategoryRepository` — findByScoringSystem
- `CompetitionCategoryRepository` — findByCompetitionIdOrderBySortOrder, existsByCompetitionIdAndCode, existsByCompetitionIdAndCatalogCategoryId, findByParentId

#### Views (internal)
- `MeadEventListView` — `/events`, `@RolesAllowed("SYSTEM_ADMIN")`, CRUD grid for events with logo upload
- `CompetitionListView` — `/events/:eventId/competitions`, `@PermitAll` + beforeEnter auth, grid filtered by `findAuthorizedCompetitions()`, "Create Competition" button (SYSTEM_ADMIN only)
- `CompetitionDetailView` — `/competitions/:competitionId`, `@PermitAll` + beforeEnter auth, TabSheet with:
  - **Participants tab:** Grid with Name/Email/Role columns, "Add Participant" button → dialog with email + role
  - **Categories tab:** `Grid<CompetitionCategory>` with Code/Name/Description/Remove columns, "Add Category" button → two-tab dialog (From Catalog / Custom with optional parent)
  - **Settings tab:** Name, Scoring System, Status fields, Save button (DRAFT only)

#### Other internal classes
- `CompetitionAccessCodeValidator` — implements `AccessCodeValidator` (identity module interface), queries EventParticipantRepository

#### Migrations (V3–V8)
- V3: events table
- V4: competitions table
- V5: event_participants table
- V6: competition_participants table
- V7: categories table + MJP seed data (18 categories)
- V8: competition_categories table

---

## All Test Files (competition module)

### Unit tests (`@ExtendWith(MockitoExtension.class)`)
- `CompetitionServiceTest.java` — 45 tests: service methods with mocked repos
- `CompetitionTest.java` — entity domain logic
- `CompetitionStatusTest.java` — enum helpers
- `MeadEventTest.java` — MeadEvent domain logic
- `EventParticipantTest.java` — EventParticipant domain logic
- `CompetitionParticipantTest.java` — CompetitionParticipant domain logic
- `CompetitionAccessCodeValidatorTest.java` — access code validation

### Repository tests (`@SpringBootTest` + `@Transactional` + Testcontainers)
- `CompetitionRepositoryTest.java` — save/retrieve, findByEventId
- `MeadEventRepositoryTest.java` — save/retrieve
- `EventParticipantRepositoryTest.java` — various query methods
- `CompetitionParticipantRepositoryTest.java` — 6 tests: save, find, exists queries
- `CategoryRepositoryTest.java` — findByScoringSystem
- `CompetitionCategoryRepositoryTest.java` — 4 tests: save/find, exists, findByParentId

### UI tests (`@SpringBootTest` + Karibu + `@DirtiesContext`)
- `MeadEventListViewTest.java` — event CRUD grid
- `CompetitionDetailViewTest.java` — 14 tests: header, tabs, participants, categories, breadcrumb, auth
- `CompetitionListViewTest.java` — competition list, auth filtering

### Module/structure tests
- `CompetitionModuleTest.java` — `@ApplicationModuleTest` bootstrap
- `ModulithStructureTest.java` — `ApplicationModules.verify()`

---

## Next Steps — Ordered

### Step 1: Competition Scope Rework — NEXT

**Design doc:** `docs/plans/2026-03-03-competition-scope-rework.md`

Phases: R0 (atomic rename) → R1 (division auth) → R2 (view restructure) → R3 (doc update)

### Step 2: Entry Module Implementation (TDD)

**Design doc:** `docs/plans/2026-03-02-entry-module-design.md` (revised 2026-03-03)

12 phases, ~53 TDD cycles. Migrations V10–V16.
Entry limits (CHIP rules): `maxEntriesPerSubcategory` + `maxEntriesPerMainCategory` per division.

### Step 3: Code Review (slice by slice)

### Step 4: Test Review (guided, with UI verification)

## Reference

**CHIP competition rules:** `docs/reference/chip-competition-rules.md`
First competition to support. Drives entry limits, judging, and awards design decisions.

---

## Key Technical Notes

- Karibu TabSheet: content is lazy-loaded. Must call `tabSheet.setSelectedIndex(N)` before finding components in non-default tabs
- Karibu component columns: buttons inside Grid `addComponentColumn` are not found by `_find(Button.class)` — they render lazily per row
- `Category` has only a protected no-arg constructor (read-only catalog entity). In unit tests, use `Mockito.mock(Category.class)` with stubbed `getId()` when distinct IDs are needed
- `Select.setEmptySelectionAllowed(true)` passes `null` to `setItemLabelGenerator` — must handle null in the lambda
- `CompetitionService` constructor is package-private (convention)
- `@DirtiesContext` required on UI tests that modify security context strategy
