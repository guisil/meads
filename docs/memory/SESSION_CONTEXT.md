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
- **Status:** Fully implemented + code reviewed. PENDING REWORK (see below).

#### Current Entities (PRE-REWORK names — will change)
| Entity | New Name (post-rework) |
|--------|----------------------|
| `MeadEvent` | `Competition` |
| `Competition` | `Division` |
| `EventParticipant` | `Participant` |
| `CompetitionParticipant` | `ParticipantRole` |
| `CompetitionCategory` | `DivisionCategory` |
| `Category` | `Category` (unchanged) |

#### Current Enums (PRE-REWORK)
- `CompetitionRole`: JUDGE, STEWARD, ENTRANT, COMPETITION_ADMIN → ADMIN
- `CompetitionStatus` → `DivisionStatus` (same values)
- `ScoringSystem` (unchanged)

#### Current Service — `CompetitionService` (public API)
Key methods — see `CLAUDE.md` package layout for full list.

#### Migrations (V3–V8)
- V3: mead_events table
- V4: competitions table
- V5: event_participants table
- V6: competition_participants table
- V7: categories table + MJP seed data
- V8: competition_categories table

---

## Next Steps — Ordered

### Step 1: Competition Scope Rework — NEXT

**Design doc:** `docs/plans/2026-03-03-competition-scope-rework.md`

**What:** Rename entities to match domain language + make all participant roles
competition-scoped (not division-scoped). Full DB table rename.

**Key changes:**
- MeadEvent → Competition, Competition → Division
- EventParticipant → Participant, CompetitionParticipant → ParticipantRole
- COMPETITION_ADMIN → ADMIN
- All roles (ADMIN, ENTRANT, JUDGE, STEWARD) are competition-scoped
- ParticipantRole drops division reference
- New CompetitionDetailView (divisions + participants + settings tabs)
- DivisionDetailView (was CompetitionDetailView, loses participants tab)
- "Add Participant to All" removed (no longer needed)

**Implementation phases:**
- **R0:** Atomic rename + V9 migration (Fast Cycle, ~1 batch)
- **R1:** Division-level authorization (Full TDD, ~4 cycles)
- **R2:** View restructure (Full TDD, ~6 cycles)
- **R3:** Update entry module design docs

### Step 2: Entry Module Implementation (TDD)

**Design doc:** `docs/plans/2026-03-02-entry-module-design.md` (revised 2026-03-03)

After the rework, implement the entry module following the 12-phase TDD sequence
in the design doc. Uses the new naming (divisions, not competitions for sub-level).

Key: Migrations V10–V15 (V9 is the rework migration).

### Step 3: Code Review (slice by slice)

After entry module is complete, thorough code review of BOTH competition and
entry modules in vertical slices.

### Step 4: Test Review (guided, with UI verification)

After code review, guided test review with UI verification for BOTH modules.

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
