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
- **Events:** createEvent, findEventById, findAllEvents, updateEvent, updateEventLogo, deleteEvent
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
- `EventListView` — `/events`, `@RolesAllowed("SYSTEM_ADMIN")`, CRUD grid for events with logo upload
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
- `EventTest.java` — MeadEvent domain logic
- `EventParticipantTest.java` — EventParticipant domain logic
- `CompetitionParticipantTest.java` — CompetitionParticipant domain logic
- `CompetitionAccessCodeValidatorTest.java` — access code validation

### Repository tests (`@SpringBootTest` + `@Transactional` + Testcontainers)
- `CompetitionRepositoryTest.java` — save/retrieve, findByEventId
- `EventRepositoryTest.java` — save/retrieve
- `EventParticipantRepositoryTest.java` — various query methods
- `CompetitionParticipantRepositoryTest.java` — 6 tests: save, find, exists queries
- `CategoryRepositoryTest.java` — findByScoringSystem
- `CompetitionCategoryRepositoryTest.java` — 4 tests: save/find, exists, findByParentId

### UI tests (`@SpringBootTest` + Karibu + `@DirtiesContext`)
- `EventListViewTest.java` — event CRUD grid
- `CompetitionDetailViewTest.java` — 14 tests: header, tabs, participants, categories, breadcrumb, auth
- `CompetitionListViewTest.java` — competition list, auth filtering

### Module/structure tests
- `CompetitionModuleTest.java` — `@ApplicationModuleTest` bootstrap
- `ModulithStructureTest.java` — `ApplicationModules.verify()`

---

## Next Session Plan

### BEFORE continuing with new features, the user wants:

#### Step 1: Competition Module Code Review (vertical slices)

Walk through the competition module code in vertical slices. For each feature
area, show the user the relevant view, service method(s), repository, entity,
and migration together so they can understand the full flow and decide on changes.

Suggested slice order:
1. **Events** — MeadEvent entity → MeadEventRepository → CompetitionService event methods → EventListView
2. **Competitions** — Competition entity → CompetitionRepository → CompetitionService competition methods → CompetitionListView
3. **Participants** — EventParticipant + CompetitionParticipant entities → repos → service participant methods → CompetitionDetailView participants tab
4. **Categories** — Category + CompetitionCategory entities → repos → service category methods → CompetitionDetailView categories tab
5. **Authorization** — requireAuthorized/requireSystemAdmin → isAuthorizedForCompetition → beforeEnter checks in views
6. **Access codes** — CompetitionAccessCodeValidator → AccessCodeValidator interface → identity module integration

For each slice, read the files and present them to the user for review. Let them
decide if changes are needed before moving on.

#### Step 2: Test Review

Go through each test file and for each test:
- Explain what the test verifies
- Help the user understand how to manually test the same behavior
- Identify any gaps (behaviors not tested, edge cases missing)

Test files to review (in order):
1. Entity unit tests (CompetitionTest, EventTest, EventParticipantTest, CompetitionParticipantTest, CompetitionStatusTest)
2. CompetitionAccessCodeValidatorTest
3. Repository tests (all 6 files)
4. CompetitionServiceTest (45 tests)
5. UI tests (EventListViewTest, CompetitionListViewTest, CompetitionDetailViewTest)
6. CompetitionModuleTest + ModulithStructureTest

For manual testing guidance:
- App runs with `mvn spring-boot:run` (needs PostgreSQL via docker-compose)
- Admin login: admin@meads.app / admin (seeded by AdminInitializer)
- Dev users seeded by DevUserInitializer (dev profile)
- Access code login available for judges/stewards

#### Step 3: After review is complete

Continue with the next feature — likely the `entry` module (credits, mead
registration). See `memory/mvp-flow.md` for full MVP requirements.

---

## Key Technical Notes

- Karibu TabSheet: content is lazy-loaded. Must call `tabSheet.setSelectedIndex(N)` before finding components in non-default tabs
- Karibu component columns: buttons inside Grid `addComponentColumn` are not found by `_find(Button.class)` — they render lazily per row
- `Category` has only a protected no-arg constructor (read-only catalog entity). In unit tests, use `Mockito.mock(Category.class)` with stubbed `getId()` when distinct IDs are needed
- `Select.setEmptySelectionAllowed(true)` passes `null` to `setItemLabelGenerator` — must handle null in the lambda
- `CompetitionService` constructor is package-private (convention)
- `@DirtiesContext` required on UI tests that modify security context strategy
