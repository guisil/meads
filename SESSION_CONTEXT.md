# Session Context

## How to Resume

1. Read `CLAUDE.md` for project workflow (strict TDD: RED-GREEN-REFACTOR, each step a separate response).
2. Read this file for what's done and what's next.
3. Read the design doc referenced below before starting implementation.

## Branch

`main` (auth redesign merged)

## Tests passing: 123

---

## Next Work: Competition Module

The competition module is the next module to build. It will cover events, competitions,
scoring systems, categories, and competition admins. It will also provide the
`AccessCodeValidator` implementation that plugs into the identity module's dormant
access code authentication provider.

**No design doc yet.** Start by creating one using the `/architect` workflow.

### Key integration points with identity module

- `AccessCodeValidator` interface (in `app.meads.identity`) — competition module provides implementation
- `UserService` — for looking up users during participant creation
- Competition-scoped roles (JUDGE, STEWARD, ENTRANT, COMPETITION_ADMIN) live on
  `CompetitionParticipant` records, not on `User.role`
- `JwtMagicLinkService` — for generating magic links with competition-scoped expiry

---

## Previously Completed

### Auth Redesign (on branch `auth-mechanism-decision`)

**Design doc:** `docs/plans/2026-02-27-auth-redesign-design.md`
**Status:** Implemented. All 14 tasks + LoginView TabSheet UX improvement complete.

Replaced Spring Security OTT with three auth mechanisms:

1. **JWT magic links** — `JwtMagicLinkService` (jjwt 0.13.0), `MagicLinkAuthenticationFilter`,
   reusable stateless tokens. Fixes the `/login` bug.
2. **Access codes** — `AccessCodeValidator` interface + `AccessCodeAuthenticationProvider` +
   `AccessCodeAuthenticationToken`. Dormant until competition module provides implementation.
3. **Admin passwords** — `formLogin` + `BCryptPasswordEncoder` + `DatabaseUserDetailsService`
   returns password hash. `AdminInitializer` creates admin with password from env vars.

**Files created:** `JwtMagicLinkService.java`, `AccessCodeValidator.java`,
`MagicLinkAuthenticationFilter.java`, `AccessCodeAuthenticationProvider.java`,
`AccessCodeAuthenticationToken.java`, `V4__add_password_hash_to_users.sql`

**Files deleted:** `MagicLinkService.java`, `MagicLinkLandingController.java`,
`MagicLinkSuccessHandler.java`, `templates/magic-link-landing.html`

**Login UX:** TabSheet with two tabs — "Magic Link" (email + send button) and
"Login with Credentials" (email + code/password + login button).

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
