# Identity Module Review

## Overview

The identity module (`app.meads.identity`) is the first and currently only module in MEADS.
It handles user management, authentication via magic links (Spring Security OTT), role-based
access control, and admin CRUD operations. This review covers code quality, test coverage,
architectural compliance, and the authentication mechanism.

---

## Authentication Architecture

### Current Approach: Magic Link / One-Time Token (OTT)

**How it works:**
1. User enters email on `LoginView` (Vaadin form)
2. `MagicLinkService.requestMagicLink()` generates a one-time token via `OneTimeTokenService`
3. Magic link is **logged to console** (no email sending implemented)
4. User clicks link → `MagicLinkLandingController` serves auto-submit HTML form
5. Form POSTs token to `/login/ott` (Spring Security's OTT filter)
6. `DatabaseUserDetailsService` loads user, Spring Security creates session
7. `UserActivationListener` activates PENDING users on first successful auth

**Spring Security components involved:**
- `SecurityConfig.java` — filter chain with `oneTimeTokenLogin()`, `vaadin()` integration
- `InMemoryOneTimeTokenService` — development-only token storage (lost on restart)
- `MagicLinkSuccessHandler` — `OneTimeTokenGenerationSuccessHandler` that logs + redirects
- `DatabaseUserDetailsService` — `UserDetailsService` mapping User → UserDetails
- `MagicLinkLandingController` — `@Controller` serving `/login/magic` GET endpoint

**Trade-offs:**
| Pro | Con |
|-----|-----|
| No passwords to store/hash/leak | No email delivery implemented yet |
| Simple user experience | Token storage is in-memory (dev only) |
| Session-based (Vaadin-compatible) | Depends on email infrastructure in production |
| Stateless user entity | Magic links can be intercepted if email is insecure |

### Known Bug: Spring Security Default OTT Page at /login

**Status:** Unresolved (documented in `SESSION_CONTEXT.md`)

The default Spring Security "Request a One-Time Token" page appears at `/login` instead
of the custom Vaadin `LoginView`. The root cause appears to be an interaction between
`VaadinSecurityConfigurer`, `formLogin(disable)`, and `oneTimeTokenLogin()` — all three
compete to control the `/login` URL.

**Investigation steps remaining:**
1. Debug logging to identify which filter generates the OTT form
2. Try `oneTimeTokenLogin(ott -> ott.tokenGeneratingUrl("/generate-token"))` to move OTT off `/login`
3. Try explicit `.loginPage("/login")` on the OTT configurer
4. Check Vaadin community/docs for OTT + Vaadin integration patterns

### Alternative Auth Approaches (Comparison)

#### Password-Based Login
**What changes:**
- `LoginView` → add password field, use standard Vaadin `LoginForm` or custom form
- `SecurityConfig` → replace `oneTimeTokenLogin()` with `formLogin()` pointing to `/login`
- `User` entity → add `password` field (hashed)
- Add `PasswordEncoder` bean (`BCryptPasswordEncoder`)
- `DatabaseUserDetailsService` → return actual password hash
- **Remove:** `MagicLinkService`, `MagicLinkLandingController`, `MagicLinkSuccessHandler`, `OneTimeTokenService`
- **Keep:** `UserService`, `UserListView`, `AdminInitializer`, `UserActivationListener`, all entities/enums

**Effort:** Moderate. The Vaadin + formLogin pattern is well-documented and avoids the current OTT/login page conflict.

#### JWT with Expiration/Refresh
**What changes:**
- `SecurityConfig` → stateless session, JWT filter instead of form/OTT login
- Add JWT token generation/validation service
- Add refresh token endpoint (`/api/auth/refresh`)
- `LoginView` → POST to REST endpoint, store JWT client-side
- **Problem:** Vaadin Flow is inherently session-based (server-side state). JWT is designed
  for stateless APIs. Combining them is awkward and not recommended by Vaadin.

**Effort:** High and architecturally mismatched. Not recommended for Vaadin Flow apps.

#### OAuth2 / OIDC (e.g., Google, GitHub, Keycloak)
**What changes:**
- Add `spring-boot-starter-oauth2-client` dependency
- `SecurityConfig` → replace `oneTimeTokenLogin()` with `oauth2Login()`
- Add `application.properties` entries for provider (client-id, client-secret, etc.)
- `LoginView` → redirect buttons to OAuth providers
- `DatabaseUserDetailsService` → implement `OAuth2UserService` or map OIDC claims to local User
- **Keep:** `User`, `UserService`, `UserListView`, role management, all tests not touching auth
- **Remove:** `MagicLinkService`, `MagicLinkLandingController`, `MagicLinkSuccessHandler`

**Effort:** Moderate. Well-supported by Spring Security. Offloads password management entirely.

### Auth-Agnostic vs Auth-Coupled Components

| Component | Auth-Agnostic? | Notes |
|-----------|:-:|-------|
| `User.java` | Yes | Entity has no auth fields beyond email |
| `UserStatus.java` | Yes | State machine is independent of auth mechanism |
| `Role.java` | Yes | Standard role enum |
| `UserService.java` | Yes | CRUD operations, no auth logic |
| `UserListView.java` | Yes | Admin CRUD, uses `@RolesAllowed` (framework-level) |
| `UserActivationListener.java` | Yes | Listens to generic `AuthenticationSuccessEvent` |
| `AdminInitializer.java` | Mostly | Creates user + calls `MagicLinkService` (minor coupling) |
| `LoginView.java` | **No** | Magic-link-specific UI |
| `SecurityConfig.java` | **No** | OTT-specific filter chain |
| `MagicLinkService.java` | **No** | OTT token generation |
| `MagicLinkLandingController.java` | **No** | OTT landing page |
| `MagicLinkSuccessHandler.java` | **No** | OTT success handler |
| `DatabaseUserDetailsService.java` | **No** | Empty password, OTT-specific |

---

## Test Coverage Assessment

### What's Well Covered (strengths)
- **User entity logic:** 8 tests covering construction, activation, status transitions, authorities
- **UserService:** 3 unit tests for soft/hard delete and self-deletion prevention
- **UserListView:** 52 Karibu tests — comprehensive CRUD coverage including edge cases
- **MagicLink flow:** 4 HTTP tests (authentication, invalid token, GET rejection, auto-activation)
- **MagicLinkLandingController:** 4 tests (form content, CSRF, XSS, HTML structure)
- **AdminInitializer:** 3 unit + 1 integration test
- **DevUserInitializer:** 4 unit tests covering all profile/email/existence combinations
- **DatabaseUserDetailsService:** 4 tests covering all user states
- **Modulith structure:** 2 tests (verification + documentation generation)
- **Security:** CSRF rejection test, logout flow test

### Test Gaps (recommended additions)

**High Priority:**
1. **UserService.createUser()** — no tests exist for user creation via service (only via view dialog).
   The create logic lives entirely in `UserListView`, bypassing the service layer. This should be
   refactored: move creation to `UserService.create()` and test it independently.
2. **UserService.updateUser()** — same issue. Update logic is in the view dialog, not in the service.
3. **User entity edge cases:**
   - What happens when `updateDetails()` receives null name? (Currently no validation)
   - What happens when constructor receives null/blank email? (No validation)
4. **SecurityConfig integration test** — no test verifies that unauthenticated users are actually
   redirected to `/login` at the HTTP level (only tested via Karibu route navigation).

**Medium Priority:**
5. **Rate limiting on magic link requests** — not implemented, not tested.
6. **UserListView error states** — no tests for failure notifications (e.g., save failure).
7. **Concurrent access** — no tests for race conditions on user activation or soft/hard delete.
8. **UserRepository custom queries** — `existsByRole()` and `existsByEmail()` have only indirect
   test coverage through initializer and view tests. Direct repository tests would be stronger.

**Low Priority:**
9. **MagicLinkSuccessHandler** — no direct unit test (only tested indirectly via auth flow).
10. **Email regex consistency** — `LoginView` uses `^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$` (no TLD required),
    `UserListView` uses `^[^@]+@[^@]+\\.[^@]+$` (TLD required). Should be unified.

---

## Architecture & Code Quality

### Modulith Compliance
- Module boundaries are correct — `identity.internal/` is properly encapsulated
- `package-info.java` declares `allowedDependencies = {}` (self-contained)
- `ModulithStructureTest` exists and verifies boundaries
- `MainLayout` correctly lives in root `app.meads` package (shared by all modules)

### Structural Observations

1. **Missing `@Modulithic` annotation** — `MeadsApplication.java` uses only `@SpringBootApplication`.
   The `@Modulithic` annotation is optional but enables additional features like module documentation
   and customization. Not a problem, just an option.

2. **LoginView is in module root (public)** — This is correct because `SecurityConfig` references it
   via `.loginView(LoginView.class)`, requiring it to be accessible. However, it's auth-coupled and
   may move if the auth mechanism changes.

3. **Business logic in views** — `UserListView` contains create/edit/delete logic that should live in
   `UserService`. The view directly calls `userRepository.save()` and `userRepository.findAll()`.
   This violates the pattern where services are the public API and repositories are internal.
   **Recommendation:** Add `UserService.create()`, `UserService.update()`, `UserService.findAll()`
   methods and have the view delegate to the service.

4. **`UserListView` accesses `UserRepository` directly** — The view constructor takes
   `UserRepository` as a dependency, but the repository is in `internal/`. This works because
   `UserListView` is also in `internal/`, but it bypasses the service layer.

5. **`UserListView` accesses `MagicLinkService` directly** — Same package-private access pattern.
   Acceptable architecturally, but couples the view to the auth mechanism.

### Code Smells

1. **Hardcoded URL in `MagicLinkSuccessHandler`** — Uses `"http://localhost:8080"` instead of
   injecting `app.base-url` property like `MagicLinkService` does.

2. **Duplicate magic link logging** — Both `MagicLinkService` and `MagicLinkSuccessHandler` log
   the magic link URL. Only one should do this.

3. **Inconsistent email validation** — Two different regex patterns for email validation
   (see Test Gaps section above).

4. **No input validation on `User` constructor** — Accepts null/blank name and email without
   throwing. Validation only happens in the view layer.

5. **`deleteUser` method name is misleading** — It sometimes soft-deletes (disables) and sometimes
   hard-deletes, depending on current status. Consider renaming to `disableOrDeleteUser()` or
   splitting into two methods.

---

## Unfinished Work from SESSION_CONTEXT.md

| Item | Status | Notes |
|------|--------|-------|
| Spring Security OTT page at /login | **Unresolved** | Root cause unclear, multiple hypotheses documented |
| Button variants (LUMO_PRIMARY, LUMO_ERROR) | **Not started** | Pending TDD items |
| Error notification variants | **Not started** | No LUMO_ERROR notifications exist |
| ConfirmDialog improvements | **Not started** | Current confirm dialog is basic |
| FormLayout + Binder integration | **Not started** | Views use raw components, no Binder |

---

## Prioritized Recommendations

### P0 — Fix Before Building Next Module
1. **Resolve the /login page bug** — blocks production use of the identity module
2. **Move create/update logic from UserListView to UserService** — establishes correct
   service-layer-as-API pattern before other modules copy it

### P1 — Should Do Soon
3. **Unify email validation** — single regex or validation method shared across views
4. **Add input validation to User constructor** — enforce non-null/non-blank invariants at entity level
5. **Fix hardcoded URL in MagicLinkSuccessHandler** — use `@Value("${app.base-url}")`
6. **Remove duplicate magic link logging** — pick one place to log it

### P2 — Nice to Have
7. **Add Binder-based form validation** — replace manual validation in view dialogs
8. **Add LUMO_ERROR notification variants** — for error feedback
9. **Add LUMO_PRIMARY/LUMO_ERROR button variants** — visual hierarchy
10. **Add pagination to UserListView grid** — prepare for scale
