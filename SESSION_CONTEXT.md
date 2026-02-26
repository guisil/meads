# Session Context — MainLayout with AppLayout

## How to Resume

1. Read `CLAUDE.md` for project workflow (strict TDD: RED-GREEN-REFACTOR, each step a separate response).
2. Read this file for what's done and what's next.
3. Read `doc/examples/VaadinUITestExample.java` for Karibu test patterns.
4. Use the Vaadin MCP tools (`get_vaadin_primer`, `search_vaadin_docs`, etc.) for Vaadin 25 best practices before writing code.

## Branch

`restart_tdd` (based on `main`)

## Completed — MainLayout Cycles

All security fixes from the previous session are done. 86 tests passing.

### Cycle 1: MainLayout exists and wraps RootView
- Created `MainLayout` extending `AppLayout` with `@AnonymousAllowed`
- Updated `RootView` with `@Route(value = "", layout = MainLayout.class)`
- Test: `shouldRenderRootViewInsideAppLayout`

### Cycle 2: App title in navbar
- Added H1 "MEADS" to MainLayout navbar
- Fixed `RootUrlRedirectTest` locators (now two H1s in tree)
- Test: `shouldDisplayAppTitleInNavbar`

### Cycle 3: Logout button in layout
- Added logout button to MainLayout navbar using `AuthenticationContext`
- Removed duplicate logout button from `RootView`
- Test: `shouldDisplayLogoutButtonInNavbarWhenAuthenticated`

### Cycle 4: Users nav link for admins
- Added conditional "Users" button in MainLayout navbar for SYSTEM_ADMIN role
- Removed duplicate Users button from `RootView`
- `RootView` is now a simple welcome page (just shows "Welcome {username}")
- Test: `shouldDisplayUsersLinkInNavbarForAdmin`

## Current State of Key Files

- `src/main/java/app/meads/internal/MainLayout.java` — `AppLayout` with navbar containing: H1 title, conditional Users button (admin only), Logout button
- `src/main/java/app/meads/internal/RootView.java` — Simplified to just welcome message + forward to login for unauthenticated users
- `src/test/java/app/meads/MainLayoutTest.java` — 4 Karibu UI tests
- `src/test/java/app/meads/RootUrlRedirectTest.java` — 7 tests (updated locators for H1)

## Next Steps — Continue MainLayout (#1)

These behaviors still need TDD cycles:

1. **Users link NOT shown for regular users** — Test that a user with role USER does not see the "Users" button in MainLayout. (Note: `RootUrlRedirectTest.shouldNotShowUserListLinkForRegularUsers` already covers this partially, but a MainLayout-scoped test would be cleaner.)

2. **Logout/Users buttons hidden for unauthenticated users** — The layout is `@AnonymousAllowed` so unauthenticated users can reach it (for LoginView forwarding). Verify nav buttons aren't shown when not logged in.

3. **UserListView uses MainLayout** — Update `UserListView` with `@Route(value = "users", layout = MainLayout.class)` so it also renders inside the layout.

4. **Navbar styling** — Consider using `HorizontalLayout` in navbar for proper spacing/alignment of title and buttons (title left, buttons right).

## Remaining Vaadin UI Improvements (after #1)

Per the original plan, after MainLayout is complete:

| # | Description | Priority |
|---|-------------|----------|
| #3 | Use EmailField instead of TextField for email inputs | MEDIUM |
| #4 | Binder, FormLayout, ConfirmDialog, button variants, notification variants | MEDIUM |
| #6 | Grid styling (auto-width, resizable, theme variants) | LOW |
| #5 | Theming with Lumo | LOW |

## Key Technical Notes

- **Vaadin 25** with Java Flow (server-side, NOT React/Hilla)
- **Spring Boot 4.0.2**, **Spring Security 7** (via Boot), **Spring Modulith 2.0.2**
- **Java 25**, **PostgreSQL 18**, **Flyway**
- **Karibu Testing 2.6.2** for Vaadin UI tests (no browser, server-side)
- **`AuthenticationContext`** (Vaadin's Spring Security integration) is used in views — mark field `transient`
- **Module structure:** `app.meads.identity` = public API, `app.meads.identity.internal` = private. Views are in `internal` (except `LoginView` which is in public API).
- **MainLayout** is in `app.meads.internal` — it's module-private to the root module
- **Test setup:** Karibu tests use `resolveAuthentication` helper with `@WithMockUser` fallback for `UserDetails`-based principals. See `UserListViewTest` and `MainLayoutTest` for the pattern.
