# Project: MEADS

## Notification text assertions (Vaadin 25 / Karibu)

`Notification.setText(String)` stores the text under the element property `"text"`.
Use `getElement().getProperty("text")` to assert it in tests — NOT `getTextRecursively()`.

```java
assertThat(_get(Notification.class).getElement().getProperty("text"))
        .isEqualTo("expected message");
```

## Module structure

- `app.meads` — root (public API, contains `MainLayout`)
- `app.meads.identity` — identity module public API
- `app.meads.identity.internal` — module-private (views, repos, services, `SecurityConfig`)
- `app.meads.competition` — competition module public API (allowedDependencies = {"identity"})
- `app.meads.competition.internal` — module-private (repos, AccessCodeValidator impl, views)
- Dependency direction: `competition → identity → root`
- Entry module (planned): `app.meads.entry` (allowedDependencies = {"competition", "identity"})

## Current state

Branch: `competition-module`
Tests passing: 264
Current highest migration: V8 (`V8__create_competition_categories_table.sql`).
Migrations V5–V8 are pre-deployment (V9 was merged back into V5).

### What's done

- **identity module**: complete (users, auth, roles, admin CRUD)
- **competition module**: fully implemented + code reviewed (PRE-REWORK state)

### What's next — ORDERED

1. **Competition scope rework** — NEXT
   - Design: `docs/plans/2026-03-03-competition-scope-rework.md`
   - Rename: MeadEvent→Competition, Competition→Division, EventParticipant→Participant,
     CompetitionParticipant→ParticipantRole, COMPETITION_ADMIN→ADMIN
   - All roles competition-scoped (not division-scoped)
   - Full DB table rename (V9 migration)
   - New CompetitionDetailView, DivisionDetailView restructure
   - Phases: R0 (atomic rename), R1 (division auth), R2 (view restructure), R3 (doc update)

2. **Entry module implementation (TDD)** — after rework
   - Design: `docs/plans/2026-03-02-entry-module-design.md` (revised 2026-03-03)
   - 12 phases, ~50+ TDD cycles
   - Migrations V10–V15 (V9 is the rework migration)

3. **Code review** of both competition and entry modules (slice by slice)

4. **Test review** (guided, with UI verification) of both modules

### Deferred

- Side nav "My Competitions" item for ADMIN users (currently access via direct URL)
- Competition-level operations for ADMIN (edit competition name, dates, logo) — will be in
  CompetitionDetailView Settings tab after rework
- Automatic email notification when admin is added
- Password setup flow for competition admins (currently use magic link or admin sets password)
- Access code security: review whether admins should have access to judge/steward access codes
- Withdraw/deactivation behavior: decide if needed at competition or division level
- Access code scope: review whether per-division codes are needed once judging module clarifies
- Per-division category prefix: optional prefix per division for display
- Authorization annotations: explore replacing manual auth checks with method-level annotations
- Category reordering (drag-and-drop or manual sort order)
- Entry conflict checks when removing categories (requires entry module)
- Bulk category operations (remove all, reset to catalog defaults)
- Application-wide logging audit
- **[Post entry module]** Competition rules enforcement: review which rules should be configurable
  in the app (max entries per participant, max per category, entry fee structure, registration
  deadlines, ABV ranges, required fields per category, mutual exclusivity configurability).
  Design discussion before judging module.

## Vaadin components over custom JS

Always use Vaadin's built-in components (e.g., `LoginForm`, `Upload`, `Grid`) instead of
custom JavaScript via `executeJs()`. Custom JS bypasses CSRF, theming, accessibility, and i18n.
Use the Vaadin MCP tools (`search_vaadin_docs`, `get_component_java_api`) to check what's available.

## UserRepository visibility

`UserRepository` (in `internal/`) must stay `public` because `UserService` (in module root)
needs the type for constructor injection. Java's package-private is per-package, not per-module.
Spring Modulith's `verify()` enforces the cross-module boundary instead.

## Entity conventions (confirmed across both modules)

- UUID self-generated in constructor (`this.id = UUID.randomUUID()`), not passed as parameter
- `@Getter` (Lombok) on entities — no manual getters
- `Instant` for timestamps, `TIMESTAMP WITH TIME ZONE` in DB
- `@Getter` + `@RequiredArgsConstructor` on enums with fields (e.g., `DivisionStatus`)
- No `@Data`, `@Builder`, or `@Setter` — state changes via domain methods

## View authorization pattern (@PermitAll + beforeEnter)

For views that need finer-grained auth than role-based (e.g., ADMIN per competition):
- Use `@PermitAll` instead of `@RolesAllowed`
- Check authorization in `beforeEnter()` via service-level boolean helper
- Forward unauthorized users to `""` (root)
- Reference (post-rework): `CompetitionDetailView`, `DivisionDetailView`

## Karibu testing — TabSheet, component columns, TreeGrid

- **TabSheet lazy-loads tab content.** Non-selected tabs are not in the DOM. Must call
  `tabSheet.setSelectedIndex(N)` before using `_get`/`_find` on components inside that tab.
- **Grid component columns render lazily.** Buttons inside `addComponentColumn` are NOT found
  by `_find(Button.class)`. To test component columns, verify column count and headers instead.
- **Grid ID for lookup.** Set `grid.setId("my-grid")` and find with `_get(Grid.class, spec -> spec.withId("my-grid"))`.
- **Select empty selection.** `setEmptySelectionAllowed(true)` passes `null` to `setItemLabelGenerator` — always null-check.
- **TreeGrid data assertions.** Use `HierarchicalQuery` to fetch root/child items — `getGenericDataView()` is not supported by TreeGrid.

## Detached entity persistence pitfall

Views must never call entity domain methods and assume the change persists.
Always go through a service method that loads, mutates, and saves.

## Competition scope rework naming map (quick reference)

| Before | After | DB Table Before | DB Table After |
|--------|-------|-----------------|----------------|
| MeadEvent | Competition | mead_events | competitions |
| Competition | Division | competitions | divisions |
| EventParticipant | Participant | event_participants | participants |
| CompetitionParticipant | ParticipantRole | competition_participants | participant_roles |
| CompetitionCategory | DivisionCategory | competition_categories | division_categories |
| CompetitionStatus | DivisionStatus | — | — |
| COMPETITION_ADMIN | ADMIN | — | — |

# currentDate
Today's date is 2026-03-03.
