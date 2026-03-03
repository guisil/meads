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
- `app.meads.entry` — entry module public API (allowedDependencies = {"competition", "identity"})
- `app.meads.entry.internal` — module-private (repos, views, controller, listener)
- Dependency direction: `entry → competition → identity → root`

## Current state

Branch: `competition-module`
Tests passing: 361
Current highest migration: V15 (`V15__add_entry_limits_to_divisions.sql`).
Migrations V3–V8 are competition module (pre-deployment). V9–V15 are entry module.

### What's done

- **identity module**: complete (users, auth, roles, admin CRUD)
- **competition module**: fully implemented + code reviewed
- **Competition scope rework**: ALL PHASES COMPLETE (R0–R3)
- **Entry module**: ALL 11 PHASES COMPLETE
  - Phase 0: Module skeleton (package-info, EntryModuleTest, empty EntryService)
  - Phase 1: All 6 enums (EntryStatus, Sweetness, Strength, Carbonation, OrderStatus, LineItemStatus)
  - Phase 2: ProductMapping entity + CRUD service methods + V9 migration
  - Phase 3: JumpsellerOrder + JumpsellerOrderLineItem entities + V10-V11 migrations
  - Phase 4: EntryCredit entity + WebhookService (HMAC, processOrderPaid) + credit methods on EntryService + V12 migration
  - Phase 5: Entry entity + domain methods (submit, markReceived, withdraw, updateDetails, assignFinalCategory) + V13 migration
  - Phase 6: Entry service methods (createEntry, updateEntry, deleteEntry, submitAllDrafts, limits enforcement, admin operations) + EntriesSubmittedEvent
  - Phase 7: User.meaderyName field + V14 migration
  - Phase 8: JumpsellerWebhookController (standalone MockMvc) + SecurityConfig webhook permitAll
  - Phase 9: Module integration test (full credit → entry → submit workflow)
  - Phase 10: RegistrationClosedListener (DivisionStatusAdvancedEvent skeleton)
  - Phase 11: Views (MyEntriesView, DivisionEntryAdminView, "Manage Entries" link in DivisionDetailView, authorization)
  - Entry limits on Division: maxEntriesPerSubcategory + maxEntriesPerMainCategory (V15)

### What's next — ORDERED

1. **Code review** of both competition and entry modules (slice by slice)
2. **Test review** (guided, with UI verification) of both modules
3. **Judging module** — design and implementation

### Deferred

- Side nav "My Competitions" item for ADMIN users (currently access via direct URL)
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
- **[Judging module]** Conflict of interest: judges can't evaluate own entries/company
- **[Awards module]** BOS structure: variable number of BOS places per division (CHIP: 3 for Amadora, 1 for Profissional)
- **[Awards module]** Medal withholding: judge discretion, not purely score-threshold-based
- **[Awards module]** Head Judge tie-breaking authority for BOS

### Resolved TODOs

- ~~Entry limits per participant~~ → Implemented: `maxEntriesPerSubcategory` + `maxEntriesPerMainCategory` on Division (V15)

### To Review Later (not needed for CHIP, but may be relevant for other competitions)

- Entry fee structure — currently handled externally via Jumpseller
- Registration deadlines — currently covered by DivisionStatus workflow (REGISTRATION_CLOSED)
- ABV ranges per category — CHIP requires declaration only, no enforcement
- Required fields per category — CHIP uses same fields for all entries
- Mutual exclusivity configurability — currently hardcoded (always enforced)

## Vaadin components over custom JS

Always use Vaadin's built-in components (e.g., `LoginForm`, `Upload`, `Grid`) instead of
custom JavaScript via `executeJs()`. Custom JS bypasses CSRF, theming, accessibility, and i18n.
Use the Vaadin MCP tools (`search_vaadin_docs`, `get_component_java_api`) to check what's available.

## UserRepository visibility

`UserRepository` (in `internal/`) must stay `public` because `UserService` (in module root)
needs the type for constructor injection. Java's package-private is per-package, not per-module.
Spring Modulith's `verify()` enforces the cross-module boundary instead.

## Entity conventions (confirmed across all three modules)

- UUID self-generated in constructor (`this.id = UUID.randomUUID()`), not passed as parameter
- `@Getter` (Lombok) on entities — no manual getters
- `Instant` for timestamps, `TIMESTAMP WITH TIME ZONE` in DB
- `@Getter` + `@RequiredArgsConstructor` on enums with fields (e.g., `DivisionStatus`)
- No `@Data`, `@Builder`, or `@Setter` — state changes via domain methods

## View structure (post entry module)

- `CompetitionListView` (`/competitions`) — SYSTEM_ADMIN only, grid of all competitions
- `CompetitionDetailView` (`/competitions/:competitionId`) — tabs: Divisions, Participants, Settings
- `DivisionDetailView` (`/divisions/:divisionId`) — tabs: Categories, Settings + "Manage Entries" link
- `DivisionEntryAdminView` (`/divisions/:divisionId/entry-admin`) — tabs: Credits, Entries, Products, Orders
- `MyEntriesView` (`/divisions/:divisionId/my-entries`) — entrant-facing, credits + entry grid + submit
- Navigation: CompetitionListView → CompetitionDetailView → DivisionDetailView → DivisionEntryAdminView

## View authorization pattern (@PermitAll + beforeEnter)

For views that need finer-grained auth than role-based (e.g., ADMIN per competition):
- Use `@PermitAll` instead of `@RolesAllowed`
- Check authorization in `beforeEnter()` via service-level boolean helper
- Forward unauthorized users to `""` (root)
- `CompetitionDetailView` uses `isAuthorizedForCompetition(competitionId, userId)`
- `DivisionDetailView` uses `isAuthorizedForDivision(divisionId, userId)`
- `DivisionEntryAdminView` uses `isAuthorizedForDivision(divisionId, userId)`
- `MyEntriesView` uses `SYSTEM_ADMIN || isAuthorizedForDivision || getCreditBalance > 0`

## Karibu testing — TabSheet, component columns, TreeGrid

- **TabSheet lazy-loads tab content.** Non-selected tabs are not in the DOM. Must call
  `tabSheet.setSelectedIndex(N)` before using `_get`/`_find` on components inside that tab.
- **Grid component columns render lazily.** Buttons inside `addComponentColumn` are NOT found
  by `_find(Button.class)`. To test component columns, verify column count and headers instead.
- **Grid ID for lookup.** Set `grid.setId("my-grid")` and find with `_get(Grid.class, spec -> spec.withId("my-grid"))`.
- **Select empty selection.** `setEmptySelectionAllowed(true)` passes `null` to `setItemLabelGenerator` — always null-check.
- **TreeGrid data assertions.** Use `HierarchicalQuery` to fetch root/child items — `getGenericDataView()` is not supported by TreeGrid.

## MockMvc testing in Vaadin projects

`@WebMvcTest` doesn't work in this Vaadin project due to auto-configuration interference
(Vaadin's servlet config conflicts with MockMvc controller discovery — always 404).
Use `MockMvcBuilders.standaloneSetup(controller)` with `@ExtendWith(MockitoExtension.class)` instead.

## Cross-module navigation pattern

Use string-based `Anchor` component for links between modules. This avoids import dependencies
that would violate Spring Modulith module boundaries:
```java
var link = new Anchor("divisions/" + divisionId + "/entry-admin", "Manage Entries");
```

## Detached entity persistence pitfall

Views must never call entity domain methods and assume the change persists.
Always go through a service method that loads, mutates, and saves.

## CHIP Competition Rules Reference

CHIP (Competição de Hidromel Internacional Portuguesa) is the first competition this app
needs to support. Full rules at `docs/reference/chip-competition-rules.md`.

Key rules that drive design:
- 2 divisions: Amadora (amateur) + Profissional (commercial) — mutual exclusivity enforced
- Entry limits: 3 per subcategory, 5 per main category (configurable per division)
- MJP categories (M4B + M4D excluded for CHIP)
- Blind judging, min 2 judges per table, consolidated scoresheet
- Medal round (comparative, medals can be withheld), BOS (Gold winners only)
- Conflict of interest rules for judges

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
