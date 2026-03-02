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

## Current state

Branch: `competition-module`
Tests passing: 264
Design doc: `docs/plans/2026-02-28-competition-module-design.md`
Competition module: fully implemented + code reviewed
Current highest migration: V8 (`V8__create_competition_categories_table.sql`).
Migrations V5–V8 are pre-deployment (V9 was merged back into V5).

### What's done

- **identity module**: complete (users, auth, roles, admin CRUD)
- **competition module**: complete + code reviewed
  - Entities: MeadEvent, Competition, EventParticipant, CompetitionParticipant, Category, CompetitionCategory
  - Views: MeadEventListView, CompetitionListView, CompetitionDetailView (TreeGrid categories, participants with access codes)
  - Auth: requireAuthorized(Competition, UUID) + isAuthorized boolean helper, @PermitAll + beforeEnter pattern
  - Access codes: unique generation with retry, event-scoped, CompetitionAccessCodeValidator
  - Code review completed across 6 vertical slices (events, competitions, participants, categories, auth, access codes)

### What's next

**Entry module** — design complete, ready for TDD implementation. See:
- Design doc: `docs/plans/2026-03-02-entry-module-design.md`
- Session context: `memory/SESSION_CONTEXT.md`

Remaining phases:
1. ~~Design discussion~~ — COMPLETED
2. **Implementation (TDD)** — NEXT (12 phases, ~50+ cycles)
3. Code review (slice by slice) of BOTH competition and entry modules
4. Test review (guided, with UI verification) of BOTH modules

### Entry module design summary

- **Module:** `app.meads.entry` (allowedDependencies = {"competition", "identity"})
- **Entities:** ProductMapping, JumpsellerOrder, JumpsellerOrderLineItem, EntryCredit (ledger), Entry
- **Enums:** EntryStatus (DRAFT→SUBMITTED→RECEIVED, WITHDRAWN), Sweetness, Strength, Carbonation, OrderStatus, LineItemStatus
- **Migrations:** V9–V14 (product_mappings, jumpseller_orders, jumpseller_order_line_items, entry_credits, entries, users.meadery_name)
- **Services:** EntryService (product mappings + credits + entries), WebhookService (HMAC + order processing)
- **REST:** JumpsellerWebhookController (/api/webhooks/jumpseller/order-paid)
- **Views:** MyEntriesView (/competitions/:id/my-entries), CompetitionEntryAdminView (/competitions/:id/entry-admin)
- **Events:** CreditsAwardedEvent, EntriesSubmittedEvent
- **Key decisions:** append-only credit ledger, mutual exclusivity at credit time, webhook always 200 after HMAC, entry admin as separate view (avoids circular dep), entry code (6-char random) vs entry number (sequential)

### Deferred

- **[Important — review after competition + entry registration]** Event vs competition level split: decide if it makes sense to have some operations at the event level and some at the competition level. Currently "Add Participant to All" is event-level but system-admin only. A competition admin can't do this because we'd need to verify access to all competitions. Consider: collapse everything to event level OR competition level, rather than mixing both. Revisit once the entry module clarifies how participants interact across competitions.
- Side nav "My Competitions" item for COMPETITION_ADMIN users (currently access via direct URL)
- Event-level operations for COMPETITION_ADMIN (edit event name, dates, logo)
- Automatic email notification when competition admin is added
- Password setup flow for competition admins (currently use magic link or admin sets password)
- Access code security: review whether admins should have access to judge/steward access codes — currently displayed in participant grid, could enable impersonation (relevant for judging phase)
- Withdraw/deactivation behavior: `CompetitionParticipantStatus` and `EventParticipant.withdraw()` were removed as dead code. If withdraw/deactivation is needed later, decide whether it belongs at event level or competition level (ties into the event vs competition split decision above). If competition-level, access code validation should check whether the participant is still active in any competition of the event before allowing login.
- Access code scope: access codes are event-scoped (one code per EventParticipant across all competitions in an event). Review whether per-competition codes are needed once judging module clarifies how judges interact across competitions.
- Per-competition category prefix: optional prefix per competition for display purposes (e.g., HOME: M1A, PRO: P-M1A). Helps distinguish categories between competitions of the same event.
- Authorization annotations: explore replacing `requireSystemAdmin()`/`requireAuthorized()` calls with method-level annotations (e.g., `@SysAdminOnly`, `@CompetitionAuthorized`) or a unified approach to centralize all authorization checks declaratively
- Category reordering (drag-and-drop or manual sort order)
- Entry conflict checks when removing categories (requires entry module)
- Bulk category operations (remove all, reset to catalog defaults)
- Application-wide logging audit: ensure all relevant actions (CRUD, auth, status changes, participant management) are logged for monitoring

## Vaadin components over custom JS

Always use Vaadin's built-in components (e.g., `LoginForm`, `Upload`, `Grid`) instead of
custom JavaScript via `executeJs()`. Custom JS bypasses CSRF, theming, accessibility, and i18n.
Use the Vaadin MCP tools (`search_vaadin_docs`, `get_component_java_api`) to check what's available.

Lesson learned: LoginView originally used ~20 lines of `executeJs()` to build a form POST
for credential login. This had a CSRF bug (wrong meta tag names). Replaced with `LoginForm`
which handles CSRF, form POST, and error display automatically.

## UserRepository visibility

`UserRepository` (in `internal/`) must stay `public` because `UserService` (in module root)
needs the type for constructor injection. Java's package-private is per-package, not per-module.
Spring Modulith's `verify()` enforces the cross-module boundary instead.

## Entity conventions (confirmed across both modules)

- UUID self-generated in constructor (`this.id = UUID.randomUUID()`), not passed as parameter
- `@Getter` (Lombok) on entities — no manual getters
- `Instant` for timestamps, `TIMESTAMP WITH TIME ZONE` in DB
- `@Getter` + `@RequiredArgsConstructor` on enums with fields (e.g., `CompetitionStatus`)
- No `@Data`, `@Builder`, or `@Setter` — state changes via domain methods

## View authorization pattern (@PermitAll + beforeEnter)

For views that need finer-grained auth than role-based (e.g., COMPETITION_ADMIN per competition):
- Use `@PermitAll` instead of `@RolesAllowed`
- Check authorization in `beforeEnter()` via service-level boolean helper
- Forward unauthorized users to `""` (root)
- Reference: `CompetitionDetailView`, `CompetitionListView`

```java
@PermitAll
public class MyView extends VerticalLayout implements BeforeEnterObserver {
    public void beforeEnter(BeforeEnterEvent event) {
        // ... load entity ...
        if (!service.isAuthorizedForX(entityId, getCurrentUserId())) {
            event.forwardTo("");
            return;
        }
    }
}
```

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
Bug found in CompetitionDetailView settings tab — fixed by adding `CompetitionService.updateCompetition()`.
