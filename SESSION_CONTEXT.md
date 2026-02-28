# Session Context

## How to Resume

1. Read `CLAUDE.md` for project workflow (strict TDD: RED-GREEN-REFACTOR, each step a separate response).
2. Read this file for what's done and what's next.
3. Read the design doc referenced below before starting implementation.

## Branch

`competition-module` (branched from main)

## Tests passing: 122

---

## Next Work: Competition Module — Implementation

**Design doc:** `docs/plans/2026-02-28-competition-module-design.md`
**Status:** Design complete. Ready for implementation using `/build` workflow.

### Implementation Sequence (from design doc)

Start with Phase 1. Each item is a full RED-GREEN-REFACTOR TDD cycle.

**Phase 1 — Module Skeleton and Event Entity**
1. Create `package-info.java` with `allowedDependencies = {"identity"}`.
2. Run `ModulithStructureTest` — must pass.
3. Unit test: `EventTest` — `updateDetails()` validates date ordering.
4. Repository test: `EventRepositoryTest` — save/find. Drives V5 migration.
5. Unit test: `EventServiceTest` — `createEvent`, `deleteEvent` with mocked repository.
6. Module integration test: `CompetitionModuleTest` — context boots.

**Phase 2 — Competition Entity and Lifecycle**
7–11. Competition entity, status machine, service, authorization.

**Phase 3 — Participants and Access Codes**
12–16. CompetitionParticipant entity, service methods, copy participants.

**Phase 4 — AccessCodeValidator**
17. Integration test for cross-module access code validation.

**Phase 5 — Auth Flow Fix (Identity Module Change)**
18–19. Make AccessCodeAuthenticationProvider support UsernamePasswordAuthenticationToken fallback.

**Phase 6 — Categories**
20–21. Category entity, MJP catalog seed (V8 migration).

**Phase 7 — Views**
22–25. EventListView, CompetitionListView, CompetitionDetailView, MainLayout nav.

### Key Design Decisions

- **Event → Competition hierarchy.** Event is a stateless container. Competition has 6-state lifecycle.
- **Participants per competition** (not per event). Copy mechanism for convenience.
- **CompetitionParticipant in public API** (like `User.java`). Future modules need it.
- **No @OneToMany** on Competition → participants. Plain UUID references, service loads separately.
- **Access code auth fallback:** Modify `AccessCodeAuthenticationProvider` to also support
  `UsernamePasswordAuthenticationToken`. No LoginView UI changes needed.
- **Access codes carried over** during `copyParticipants` (same judge, same code within event).
- **MJP categories seeded by Flyway** (V8 migration). PRO-restricted categories deferred.
- **Two services:** `EventService` + `CompetitionService`. No separate `CategoryService`.
- **Authorization:** `requestingUserId` on mutating methods. SYSTEM_ADMIN bypasses;
  COMPETITION_ADMIN checked per competition. Service-layer enforcement, not `@RolesAllowed`.

---

## Previously Completed

### Competition Module Design (current session)

**Design doc:** `docs/plans/2026-02-28-competition-module-design.md`
**Status:** Complete. Covers entities, services, enums, migrations (V5–V8),
AccessCodeValidator implementation, auth flow fix, MJP category catalog,
and implementation sequence.

### Auth Redesign (merged to main via PR #3)

**Design doc:** `docs/plans/2026-02-27-auth-redesign-design.md`
**Status:** Merged. All 14 tasks + LoginView refactor + pre-merge fixes complete.

Replaced Spring Security OTT with three auth mechanisms:

1. **JWT magic links** — `JwtMagicLinkService` (jjwt 0.13.0), `MagicLinkAuthenticationFilter`,
   reusable stateless tokens. Fixes the `/login` bug.
2. **Access codes** — `AccessCodeValidator` interface + `AccessCodeAuthenticationProvider` +
   `AccessCodeAuthenticationToken`. Dormant until competition module provides implementation.
3. **Admin passwords** — `formLogin` + `BCryptPasswordEncoder` + `DatabaseUserDetailsService`
   returns password hash. `AdminInitializer` creates admin with password from env vars.

**Login UX:** TabSheet with two tabs — "Magic Link" (email + send button) and
"Login with Credentials" (Vaadin `LoginForm` with CSRF, form POST, error display).

**Pre-merge fixes:** Replaced custom JS form POST with Vaadin `LoginForm` (fixed CSRF bug),
consistent `IllegalArgumentException` in `UserService.deleteUser`, `@Transactional` on
`UserActivationListener`, removed dead `User.setUpdatedAt`, fixed `LogoutFlowTest`
cross-module boundary violation, static logger in `UserListView`.

**Dev users (dev profile):** admin@localhost (password: "admin"), user@localhost (magic link),
pending@localhost (magic link).

**Production bootstrap:** Set `INITIAL_ADMIN_EMAIL` and `INITIAL_ADMIN_PASSWORD` env vars.

### UserService Refactor (merged to main)

**Design doc:** `docs/plans/2026-02-27-userservice-refactor-design.md`
**Implementation plan:** `docs/plans/2026-02-27-userservice-refactor-plan.md`

All 11 tasks completed and merged via PR #2.

### Earlier sessions

- MainLayout with AppLayout, "MEADS" title, logout button, admin-only Users nav link
- UserListView with CRUD dialogs, EmailField, LUMO_SUCCESS notifications
- LoginView with EmailField
- Fix: "User disabled successfully" message on soft-delete

---

## Key Technical Notes

- **Java 25** (set as system default via `sdk default java 25.0.2-tem`)
- **Vaadin 25** with Java Flow (server-side, NOT React/Hilla)
- **Spring Boot 4.0.2**, **Spring Security 7.0.2**, **Spring Modulith 2.0.2**
- **PostgreSQL 18**, **Flyway** (highest migration: V4)
- **jjwt 0.13.0** for JWT magic link tokens
- **Karibu Testing 2.6.2** for Vaadin UI tests (no browser, server-side)
- **`AuthenticationContext`** fields must be `transient` in Vaadin views
- **Karibu test pattern for `@WithMockUser`:** see `resolveAuthentication` + `propagateSecurityContext` helpers in `UserListViewTest` and `MainLayoutTest`
- **Notification text** asserted via `notification.getElement().getProperty("text")`
