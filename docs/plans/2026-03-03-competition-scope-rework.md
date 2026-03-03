# Competition Scope Rework — Design Document

**Date:** 2026-03-03
**Branch:** `competition-module`
**Status:** Design complete, to be done before entry module implementation
**Prerequisite for:** Entry module (design doc: `docs/plans/2026-03-02-entry-module-design.md`)

---

## Problem

COMPETITION_ADMIN is assigned per Competition (division-level), but administrative work often
spans the entire MeadEvent (competition-level). Examples:

- Resolving cross-competition entry credit conflicts (mutual exclusivity violations)
- Managing participants across all divisions of a competition
- Overseeing competition settings and logistics holistically

Additionally, the domain naming doesn't match industry terminology:
- In the mead competition world, "Competition" means the top-level entity (e.g., "NZ Mead Awards")
- Sub-entities are "Divisions" (e.g., "Home", "Pro")
- Our naming is inverted: MeadEvent is top-level, Competition is sub-level

---

## Decisions

1. **Full rename** to match domain language: MeadEvent → Competition, Competition → Division
2. **All participant roles are competition-scoped** (not division-scoped): ADMIN, ENTRANT, JUDGE,
   STEWARD all apply to the entire competition (all divisions)
3. **Admin role on Participant entity** (the renamed EventParticipant), not on a per-division entity
4. **Full DB table rename** (not just Java classes) — we are pre-deployment
5. **Admin management UI** moves to a new CompetitionDetailView (competition-level)
6. **No per-division admins** — admin always manages the entire competition
7. **"Add Participant to All" removed** — adding at competition level already covers all divisions

---

## Naming Map

### Entities

| Current | New | Package |
|---------|-----|---------|
| `MeadEvent` | `Competition` | `app.meads.competition` |
| `Competition` | `Division` | `app.meads.competition` |
| `EventParticipant` | `Participant` | `app.meads.competition` |
| `CompetitionParticipant` | `ParticipantRole` | `app.meads.competition` |
| `CompetitionCategory` | `DivisionCategory` | `app.meads.competition` |
| `Category` | `Category` (unchanged) | `app.meads.competition` |

### Enums

| Current | New | Change |
|---------|-----|--------|
| `CompetitionRole` | `CompetitionRole` (unchanged) | Value: `COMPETITION_ADMIN` → `ADMIN` |
| `CompetitionStatus` | `DivisionStatus` | Rename only, same values |
| `ScoringSystem` | `ScoringSystem` (unchanged) | No change |

### Events

| Current | New |
|---------|-----|
| `CompetitionStatusAdvancedEvent` | `DivisionStatusAdvancedEvent` |

### Repositories

| Current | New |
|---------|-----|
| `MeadEventRepository` | `CompetitionRepository` |
| `CompetitionRepository` | `DivisionRepository` |
| `EventParticipantRepository` | `ParticipantRepository` |
| `CompetitionParticipantRepository` | `ParticipantRoleRepository` |
| `CompetitionCategoryRepository` | `DivisionCategoryRepository` |
| `CategoryRepository` | `CategoryRepository` (unchanged) |

### Views

| Current | New | Route |
|---------|-----|-------|
| `MeadEventListView` | `CompetitionListView` | `/competitions` |
| `CompetitionListView` | Absorbed into `CompetitionDetailView` | — |
| `CompetitionDetailView` | `DivisionDetailView` | `/divisions/:divisionId` |
| (new) | `CompetitionDetailView` | `/competitions/:competitionId` |

### Service

| Current | New |
|---------|-----|
| `CompetitionService` | `CompetitionService` (unchanged name) |
| `CompetitionAccessCodeValidator` | `CompetitionAccessCodeValidator` (unchanged) |

### Database Tables

| Current | New |
|---------|-----|
| `mead_events` | `competitions` |
| `competitions` | `divisions` |
| `event_participants` | `participants` |
| `competition_participants` | `participant_roles` |
| `competition_categories` | `division_categories` |
| `categories` | `categories` (unchanged) |

---

## Database Migration (V9)

### V9__rename_tables_and_restructure_participants.sql

```sql
-- Phase 1: Rename competitions → divisions (frees up "competitions" name)
ALTER TABLE competitions RENAME TO divisions;
ALTER TABLE divisions RENAME COLUMN event_id TO competition_id;
ALTER INDEX idx_competitions_event_id RENAME TO idx_divisions_competition_id;

-- Phase 2: Rename mead_events → competitions
ALTER TABLE mead_events RENAME TO competitions;

-- Phase 3: Rename event_participants → participants
ALTER TABLE event_participants RENAME TO participants;
ALTER TABLE participants RENAME COLUMN event_id TO competition_id;
ALTER INDEX idx_event_participants_event_id RENAME TO idx_participants_competition_id;
ALTER INDEX idx_event_participants_access_code RENAME TO idx_participants_access_code;
ALTER TABLE participants RENAME CONSTRAINT uq_event_participant TO uq_participant;

-- Phase 4: Restructure competition_participants → participant_roles
-- Roles are now competition-scoped (via participant → competition), so drop the
-- division-level competition_id column
ALTER TABLE competition_participants
    DROP CONSTRAINT uq_competition_participant_role;
DROP INDEX idx_competition_participants_competition_id;
ALTER TABLE competition_participants DROP COLUMN competition_id;
ALTER TABLE competition_participants RENAME TO participant_roles;
ALTER TABLE participant_roles RENAME COLUMN event_participant_id TO participant_id;
ALTER TABLE participant_roles
    ADD CONSTRAINT uq_participant_role UNIQUE (participant_id, role);
CREATE INDEX idx_participant_roles_participant_id ON participant_roles(participant_id);

-- Update COMPETITION_ADMIN → ADMIN
UPDATE participant_roles SET role = 'ADMIN' WHERE role = 'COMPETITION_ADMIN';

-- Phase 5: Rename competition_categories → division_categories
ALTER TABLE competition_categories RENAME TO division_categories;
ALTER TABLE division_categories RENAME COLUMN competition_id TO division_id;
-- Recreate unique constraint with new column name
ALTER TABLE division_categories
    DROP CONSTRAINT competition_categories_competition_id_code_key;
ALTER TABLE division_categories
    ADD CONSTRAINT uq_division_categories_division_code UNIQUE (division_id, code);
```

**Note:** The exact auto-generated constraint name for `competition_categories(competition_id, code)`
may differ. Verify with `\d+ competition_categories` in psql during implementation.

---

## Entity Changes

### Competition (was MeadEvent)

Rename only. Same fields: id, name, startDate, endDate, location, logo, logoContentType,
createdAt, updatedAt. Same methods: `updateDetails()`, `updateLogo()`, `hasLogo()`.

```java
@Entity
@Table(name = "competitions")
```

### Division (was Competition)

Rename + field rename:

```java
@Entity
@Table(name = "divisions")
public class Division {
    private UUID competitionId; // was eventId
    private String name;
    private DivisionStatus status; // was CompetitionStatus
    private ScoringSystem scoringSystem;
    // Same methods: advanceStatus(), updateDetails()
}
```

### Participant (was EventParticipant)

Rename + field rename:

```java
@Entity
@Table(name = "participants")
public class Participant {
    private UUID competitionId; // was eventId
    private UUID userId;
    private String accessCode;
    // Same methods: assignAccessCode()
}
```

### ParticipantRole (was CompetitionParticipant)

**Structural change:** Remove division reference (`competitionId`). Rename field.

```java
@Entity
@Table(name = "participant_roles")
public class ParticipantRole {
    // REMOVED: private UUID competitionId (was FK to old competitions, now divisions)
    private UUID participantId; // was eventParticipantId
    private CompetitionRole role;
}
```

**Unique constraint:** `(participantId, role)` — a participant can have each role at most once.

### DivisionCategory (was CompetitionCategory)

Rename + field rename:

```java
@Entity
@Table(name = "division_categories")
public class DivisionCategory {
    private UUID divisionId; // was competitionId
    // All other fields unchanged: catalogCategoryId, code, name, description, parentId, sortOrder
}
```

---

## Enum Changes

### CompetitionRole

```java
@Getter
@RequiredArgsConstructor
public enum CompetitionRole {
    JUDGE("Judge"),
    STEWARD("Steward"),
    ENTRANT("Entrant"),
    ADMIN("Admin"); // was COMPETITION_ADMIN("Competition Admin")

    private final String displayName;

    public boolean requiresAccessCode() {
        return this == JUDGE || this == STEWARD;
    }
}
```

### DivisionStatus (was CompetitionStatus)

Rename only. Same values: DRAFT, REGISTRATION_OPEN, REGISTRATION_CLOSED, JUDGING,
DELIBERATION, RESULTS_PUBLISHED. Same methods: `allowsCategoryModification()`, `next()`,
`getDisplayName()`, `getBadgeCssClass()`.

---

## Service Changes

### CompetitionService Method Renames

**Competition (was MeadEvent) operations:**

| Current | New |
|---------|-----|
| `createMeadEvent(name, startDate, endDate, location)` | `createCompetition(...)` |
| `findMeadEventById(id)` | `findCompetitionById(id)` |
| `findAllMeadEvents()` | `findAllCompetitions()` |
| `updateMeadEvent(id, ...)` | `updateCompetition(id, ...)` |
| `updateMeadEventLogo(id, logo, contentType)` | `updateCompetitionLogo(id, ...)` |
| `deleteMeadEvent(id)` | `deleteCompetition(id)` |

**Division (was Competition) operations:**

| Current | New |
|---------|-----|
| `createCompetition(eventId, name, scoringSystem)` | `createDivision(competitionId, name, scoringSystem)` |
| `findById(competitionId)` | `findDivisionById(divisionId)` |
| `updateCompetition(competitionId, ...)` | `updateDivision(divisionId, ...)` |
| `advanceStatus(competitionId, userId)` | `advanceDivisionStatus(divisionId, userId)` |
| `deleteCompetition(competitionId)` | `deleteDivision(divisionId, userId)` |

**Participant operations:**

| Current | New | Notes |
|---------|-----|-------|
| `addParticipantByEmail(email, role, competitionId, userId)` | `addParticipant(email, role, competitionId, userId)` | competitionId is now top-level |
| `removeParticipant(competitionParticipantId, userId)` | `removeParticipantRole(participantRoleId, userId)` | |
| `addParticipantToAllCompetitions(...)` | **REMOVED** | No longer needed |
| `findParticipantsByCompetition(competitionId)` | `findParticipantRoles(competitionId)` | Returns roles for competition |
| `findEventParticipantsByEvent(eventId)` | `findParticipants(competitionId)` | |

**New methods:**

| Method | Description |
|--------|-------------|
| `findDivisionsByCompetition(competitionId)` | List divisions for a competition |
| `isAuthorizedForDivision(divisionId, userId)` | Loads division → gets competitionId → checks competition-level auth |

**Category operations (rename only):**

| Current | New |
|---------|-----|
| `findCompetitionCategories(competitionId)` | `findDivisionCategories(divisionId)` |
| `addCatalogCategory(competitionId, ...)` | `addCatalogCategory(divisionId, ...)` |
| `addCustomCategory(competitionId, ...)` | `addCustomCategory(divisionId, ...)` |
| `updateCompetitionCategory(...)` | `updateDivisionCategory(...)` |
| `removeCompetitionCategory(...)` | `removeDivisionCategory(...)` |
| `findAvailableCatalogCategories(competitionId)` | `findAvailableCatalogCategories(divisionId)` |

**Authorization:**

| Current | New | Notes |
|---------|-----|-------|
| `isAuthorizedForCompetition(competitionId, userId)` | `isAuthorizedForCompetition(competitionId, userId)` | Same name, now checks top-level |
| `findAuthorizedCompetitions(userId)` | `findAuthorizedCompetitions(userId)` | Returns top-level competitions |
| `requireAuthorized(Competition, userId)` | `requireAuthorized(Competition, userId)` | Takes Competition (top-level) |
| — | `isAuthorizedForDivision(divisionId, userId)` | **NEW** |

### Authorization Logic Change

**Current:** Check if user has COMPETITION_ADMIN for a specific competition (division-level).

**New:** Check if user has ADMIN role for the competition (top-level). For division-level
operations, load the division to get its competitionId, then check competition-level auth.

```java
boolean isAuthorizedForCompetition(UUID competitionId, UUID userId) {
    if (isSystemAdmin(userId)) return true;
    return participantRepository.findByCompetitionIdAndUserId(competitionId, userId)
        .map(participant -> participantRoleRepository
            .existsByParticipantIdAndRole(participant.getId(), ADMIN))
        .orElse(false);
}

boolean isAuthorizedForDivision(UUID divisionId, UUID userId) {
    var division = divisionRepository.findById(divisionId)
        .orElseThrow(() -> new IllegalArgumentException("Division not found"));
    return isAuthorizedForCompetition(division.getCompetitionId(), userId);
}
```

### Helper Changes

**`findOrCreateParticipant` (was `findOrCreateEventParticipant`):**
- Takes `competitionId` (top-level) directly — no indirection through division
- Creates Participant at competition level
- Assigns access code if role requires it (same logic as before)

---

## View Changes

### CompetitionListView (was MeadEventListView)

**Route:** `/competitions` (was `/events`)
**Access:** `@RolesAllowed("SYSTEM_ADMIN")` (unchanged)

Minimal change: rename class, update route, update references. Same functionality — grid of
all competitions with Create/Delete actions. Clicking a row navigates to CompetitionDetailView
(`/competitions/:competitionId`).

### CompetitionDetailView (NEW)

**Route:** `/competitions/:competitionId`
**Access:** `@PermitAll` + `beforeEnter` auth (ADMIN for this competition or SYSTEM_ADMIN)

**Header:** Competition name, date range, location, logo (if present)

**Tabs:**

1. **Divisions tab:**
   - Grid: Name, Status (badge), Scoring System, Actions (Advance, Delete)
   - "Create Division" button
   - Click row → navigate to DivisionDetailView (`/divisions/:divisionId`)

2. **Participants tab:**
   - Grid: Name, Email, Role, Access Code, Actions (Remove)
   - "Add Participant" button → dialog (email + role Select)
   - Moved from old CompetitionDetailView

3. **Settings tab:**
   - Name (TextField), Start Date / End Date (DatePicker), Location (TextField)
   - Logo upload/remove
   - Save button

**Absorbs:**
- Division list functionality from old CompetitionListView (including create/delete/advance)
- Participant management from old CompetitionDetailView
- Event header display from old CompetitionListView

### DivisionDetailView (was CompetitionDetailView)

**Route:** `/divisions/:divisionId` (was `/competitions/:competitionId`)
**Access:** `@PermitAll` + `beforeEnter` — loads division, gets competitionId, checks
`isAuthorizedForDivision(divisionId, userId)`

**Header:** Division name, breadcrumb: Competition Name > Division Name (link back to
CompetitionDetailView)

**Tabs:**

1. **Categories tab** (unchanged):
   - TreeGrid with hierarchy
   - Add Category (From Catalog / Custom)
   - Remove Category
   - Disabled if status doesn't allow modification

2. **Settings tab:**
   - Name (TextField), Scoring System (Select) — enabled if DRAFT
   - Status (read-only)
   - Save button — DRAFT only
   - "Manage Entries" link (string-based navigation to `/divisions/:divisionId/entry-admin`)

**Change:** Participants tab **REMOVED** (moved to CompetitionDetailView).

### Old CompetitionListView (REMOVED)

Absorbed into CompetitionDetailView's Divisions tab. The route
`/events/:eventId/competitions` is replaced by `/competitions/:competitionId`.

### MainLayout Change

```java
// Current:
nav.addItem(new SideNavItem("Events", "events", VaadinIcon.CALENDAR.create()));

// New:
nav.addItem(new SideNavItem("Competitions", "competitions", VaadinIcon.CALENDAR.create()));
```

---

## Test Impact

### Tests to Rename/Update

| Current Test Class | New Test Class |
|-------------------|----------------|
| `CompetitionServiceTest` | `CompetitionServiceTest` (update method names) |
| `MeadEventListViewTest` | `CompetitionListViewTest` |
| `CompetitionListViewTest` | Absorbed / removed |
| `CompetitionDetailViewTest` | `DivisionDetailViewTest` |
| `CompetitionModuleTest` | `CompetitionModuleTest` (update references) |
| `ModulithStructureTest` | No change (auto-verifies) |

### New Tests

| Test | Type | Phase |
|------|------|-------|
| `isAuthorizedForDivision` — admin can access any division | Unit | R1 |
| `isAuthorizedForDivision` — non-admin denied | Unit | R1 |
| `CompetitionDetailViewTest` — divisions tab | Karibu | R2 |
| `CompetitionDetailViewTest` — participants tab | Karibu | R2 |
| `CompetitionDetailViewTest` — settings tab | Karibu | R2 |
| `DivisionDetailViewTest` — no participants tab | Karibu | R2 |

---

## Entry Module Design Updates

After this rework, the entry module design (`docs/plans/2026-03-02-entry-module-design.md`)
needs these adjustments:

### Entity field renames

All `competitionId` fields that reference the sub-level entity become `divisionId`:

| Entity | Field Change |
|--------|-------------|
| `ProductMapping` | `competitionId` → `divisionId` |
| `JumpsellerOrderLineItem` | `competitionId` → `divisionId` |
| `EntryCredit` | `competitionId` → `divisionId` |
| `Entry` | `competitionId` → `divisionId` |

### Migration number shift

V9 is now the rework migration. Entry module migrations shift +1:

| Original | New | Description |
|----------|-----|-------------|
| V9 | V10 | create_product_mappings_table |
| V10 | V11 | create_jumpseller_orders_table |
| V11 | V12 | create_jumpseller_order_line_items_table |
| V12 | V13 | create_entry_credits_table |
| V13 | V14 | create_entries_table |
| V14 | V15 | add_meadery_name_to_users |

DB column changes: `competition_id` → `division_id` in all entry module tables.
FK targets: `divisions(id)` instead of `competitions(id)`.

### Route changes

| Original | New |
|----------|-----|
| `/competitions/:competitionId/my-entries` | `/divisions/:divisionId/my-entries` |
| `/competitions/:competitionId/entry-admin` | `/divisions/:divisionId/entry-admin` |

### Service references

- `CompetitionService.findById()` → `CompetitionService.findDivisionById()`
- `CompetitionService.findCompetitionsByEvent()` → `CompetitionService.findDivisionsByCompetition()`
- Webhook creates Participant at competition level (not division level)
- Mutual exclusivity: "has credits in other division of same competition"

### Event references

| Original | New |
|----------|-----|
| `CreditsAwardedEvent(competitionId, ...)` | `CreditsAwardedEvent(divisionId, ...)` |
| `EntriesSubmittedEvent(competitionId, ...)` | `EntriesSubmittedEvent(divisionId, ...)` |
| Listens for `CompetitionStatusAdvancedEvent` | Listens for `DivisionStatusAdvancedEvent` |

### "Manage Entries" link

In DivisionDetailView (was CompetitionDetailView), the string-based navigation link becomes:
`UI.getCurrent().navigate("divisions/" + divisionId + "/entry-admin")`

---

## Implementation Phases

### Phase R0: Atomic Rename + V9 Migration (Fast Cycle)

One batch of mechanical changes. Existing tests cover all behavior — we update tests alongside
production code.

1. Create `V9__rename_tables_and_restructure_participants.sql`
2. Rename all entity classes + update `@Table` / `@Column` annotations
3. Rename `CompetitionStatus` → `DivisionStatus`
4. Update `CompetitionRole.COMPETITION_ADMIN` → `CompetitionRole.ADMIN`
5. Rename all repository interfaces + update queries
6. Rename/update all `CompetitionService` methods (mechanical renames only)
7. Rename/update all view classes + routes
8. Update `MainLayout` side nav ("Events" → "Competitions", route `events` → `competitions`)
9. Rename/update all test classes + assertions
10. Run full test suite — must pass
11. Commit

**What this phase does NOT change:**
- ParticipantRole still has the division reference (column dropped in migration, but Java entity
  still compiled — we just remove the field)
- Authorization logic still works at the same conceptual level (just renamed)
- View structure stays the same (participants tab still on DivisionDetailView)

**Correction:** Actually, the V9 migration drops the `competition_id` column from
`participant_roles`. So the ParticipantRole entity MUST lose its `competitionId` field in this
phase. This is a structural change but it's part of the atomic rename — you can't have the
entity reference a column that doesn't exist.

Similarly, the repository queries that used `competitionId` on CompetitionParticipant must be
updated to use `participantId` on ParticipantRole.

This means Phase R0 includes the structural change to ParticipantRole. The existing tests
will be updated to use the new query patterns. Since authorization still conceptually works
the same way (check if user has admin role → they can access), the tests should pass.

### Phase R1: Division-Level Authorization (Full TDD Cycles)

Add the new `isAuthorizedForDivision` method and update DivisionDetailView to use it.

**Cycle 1 (Full):**
- RED: Test `shouldReturnTrueWhenUserIsAdminForDivisionCompetition`
- GREEN: Implement `isAuthorizedForDivision(divisionId, userId)`
- REFACTOR: Review

**Cycle 2 (Full):**
- RED: Test `shouldReturnFalseWhenUserIsNotAdminForDivisionCompetition`
- GREEN: Already passes (same implementation)
- REFACTOR: Review

**Cycle 3 (Fast):**
- Update DivisionDetailView `beforeEnter` to use `isAuthorizedForDivision`
- Existing DivisionDetailView auth tests still pass (renamed)

**Cycle 4 (Fast):**
- Remove `addParticipantToAllCompetitions` method + UI button + tests

### Phase R2: View Restructure (Full TDD Cycles)

**Cycle 1 (Full):** CompetitionDetailView — Divisions tab
- RED: Test renders grid with divisions, create/delete
- GREEN: Implement tab (absorb from old CompetitionListView)
- REFACTOR

**Cycle 2 (Full):** CompetitionDetailView — Participants tab
- RED: Test renders grid with participants, add/remove
- GREEN: Implement tab (move from DivisionDetailView)
- REFACTOR

**Cycle 3 (Full):** CompetitionDetailView — Settings tab
- RED: Test renders competition settings form
- GREEN: Implement tab (name, dates, location, logo)
- REFACTOR

**Cycle 4 (Fast):** DivisionDetailView — Remove participants tab
- Existing test assertions for participants tab are removed
- Categories and Settings tabs remain
- Run full suite

**Cycle 5 (Fast):** Clean up old CompetitionListView
- Remove the old class (if not already absorbed)
- Update navigation links and tests
- Run full suite

**Cycle 6 (Fast):** Route + breadcrumb updates
- DivisionDetailView breadcrumb: Competition Name > Division Name
- Navigation from CompetitionDetailView to DivisionDetailView
- Run full suite

### Phase R3: Update Entry Module Design (Documentation only)

- Update `docs/plans/2026-03-02-entry-module-design.md` in place with new naming
- Update `MEMORY.md` and `SESSION_CONTEXT.md`
- Commit documentation changes

---

## Post-Rework TODO

After the rework and entry module implementation, review which competition rules should be
configurable and enforced by the application. Examples:
- Maximum entries per participant (per division)
- Maximum entries per category (per participant or total)
- Entry fee structure (credits per entry, variable by category)
- Registration deadlines (auto-close registration)
- Minimum/maximum ABV ranges per category
- Required fields per category (e.g., "malt used" for braggot)
- Mutual exclusivity rules (currently hardcoded: one division per competition per entrant —
  could be configurable)

This should be a design discussion before the judging module, once the entry module is complete
and real usage patterns are clearer.

---

## Summary of Removed Features

| Feature | Reason |
|---------|--------|
| "Add Participant to All" button/method | Participants are competition-scoped; adding once covers all divisions |
| Per-division participant assignment | Roles are competition-scoped |
| `CompetitionListView` (old, `/events/:eventId/competitions`) | Absorbed into CompetitionDetailView Divisions tab |

## Summary of New Features

| Feature | Phase |
|---------|-------|
| `isAuthorizedForDivision(divisionId, userId)` | R1 |
| `CompetitionDetailView` (new, `/competitions/:competitionId`) | R2 |
| Divisions tab (was a separate view) | R2 |
| Participants tab at competition level (was at division level) | R2 |
| Competition settings tab (was dialog in MeadEventListView) | R2 |
