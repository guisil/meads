# Session Context

## How to Resume

1. Read `CLAUDE.md` for project workflow (strict TDD: RED-GREEN-REFACTOR, each step a separate response).
2. Read this file for what's done and what's next.
3. Read the design doc referenced below before starting implementation.

## Branch

`auth-mechanism-decision` (based on `main`)

## Tests passing: 111

---

## Current Work: Auth Redesign

**Design doc:** `docs/plans/2026-02-27-auth-redesign-design.md`
**Status:** Design approved. Implementation plan not yet written.

### Next step: Write implementation plan

Use the `writing-plans` skill to create a step-by-step implementation plan from the
design doc. Then implement in TDD cycles per CLAUDE.md.

### Design summary

Three auth mechanisms replacing the current OTT-based magic links:

1. **JWT magic links** — for all users. Reusable, stateless, expiry passed by caller.
   Replaces Spring Security OTT (fixes the `/login` bug). Uses `jjwt` library.
2. **Access codes** — fallback for judges/stewards. 8-char alphanumeric code,
   competition-scoped. `AccessCodeValidator` interface in identity module,
   implementation deferred to competition module.
3. **Password** — for admins only. Standard `formLogin` + `BCryptPasswordEncoder`.
   TOTP deferred to later phase.

### Key decisions made

- JWT `sub` claim uses email (not UUID)
- Single `/login` page with three sections (magic link, access code, admin password)
- `AccessCodeValidator` interface in identity module, implementation in competition module
- Competition-scoped roles (JUDGE, STEWARD, ENTRANT, COMPETITION_ADMIN) live on
  competition participant records, not on User entity
- Global roles (USER, SYSTEM_ADMIN) remain on User entity unchanged
- ENTRANT + JUDGE can coexist in same competition; "judge can't judge own entries"
  enforced in judging module
- Magic link implementation is identical for all roles; only expiry differs
- Access code is a completely separate auth mechanism from magic links

### Files to delete

- `MagicLinkService.java`, `MagicLinkLandingController.java`, `MagicLinkSuccessHandler.java`
- `templates/magic-link-landing.html`

### Files to create

- `JwtMagicLinkService.java` (public API), `AccessCodeValidator.java` (public API interface)
- `MagicLinkAuthenticationFilter.java`, `AccessCodeAuthenticationProvider.java`,
  `AccessCodeAuthenticationToken.java` (all `internal/`)
- `V4__add_password_hash_to_users.sql`

### Files to modify

- `User.java` (add `passwordHash`), `SecurityConfig.java` (rewrite),
  `DatabaseUserDetailsService.java`, `LoginView.java` (rewrite),
  `AdminInitializer.java`, `UserService.java`, `pom.xml` (add jjwt)

---

## Previously Completed

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

## Resolved: Spring Security default OTT page shown at /login

The auth redesign eliminates the OTT mechanism entirely, which fixes this bug.
JWT magic links use a custom filter that doesn't conflict with Vaadin's login page.

---

## Key Technical Notes

- **Java 25** (set as system default via `sdk default java 25.0.2-tem`)
- **Vaadin 25** with Java Flow (server-side, NOT React/Hilla)
- **Spring Boot 4.0.2**, **Spring Security 7.0.2**, **Spring Modulith 2.0.2**
- **PostgreSQL 18**, **Flyway** (highest migration: V3, next is V4)
- **Karibu Testing 2.6.2** for Vaadin UI tests (no browser, server-side)
- **`AuthenticationContext`** fields must be `transient` in Vaadin views
- **Karibu test pattern for `@WithMockUser`:** see `resolveAuthentication` + `propagateSecurityContext` helpers in `UserListViewTest` and `MainLayoutTest`
- **Notification text** asserted via `notification.getElement().getProperty("text")`
