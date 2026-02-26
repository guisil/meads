# Session Context

## How to Resume

1. Read `CLAUDE.md` for project workflow (strict TDD: RED-GREEN-REFACTOR, each step a separate response).
2. Read this file for what's done and what's next.
3. Read `doc/examples/VaadinUITestExample.java` for Karibu test patterns.
4. Use the Vaadin MCP tools (`get_vaadin_primer`, `search_vaadin_docs`, etc.) for Vaadin 25 best practices before writing code.

## Branch

`restart_tdd` (based on `main`)

## Tests passing: 96

---

## Completed

- MainLayout exists and wraps RootView inside AppLayout
- App title "MEADS" in navbar
- Logout button in navbar
- Users nav link for admins only (SYSTEM_ADMIN role)
- HorizontalLayout wrapper in navbar for layout
- UserListView renders inside MainLayout (required moving MainLayout to `app.meads` public API and SecurityConfig to `app.meads.identity.internal` to break a module cycle)
- EmailField in LoginView
- EmailField in UserListView create dialog
- LUMO_SUCCESS variant on "User saved successfully" notification
- Fix: show "User disabled successfully" when soft-deleting (was always "User deleted successfully")
- LUMO_SUCCESS variant on disable/delete notification
- LUMO_SUCCESS variant on "User created successfully" notification
- LUMO_SUCCESS variant on "Magic link sent successfully" notification

---

## Unresolved bug: Spring Security default OTT page shown at /login

### Symptom
When navigating directly to `/login` in the browser, Spring Security shows its own default
"Request a One-Time Token" page (with a username field and "Send Token" button) instead of
the Vaadin LoginView.

### Current SecurityConfig
```java
http
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/login/magic").permitAll()
    )
    .with(vaadin(), vaadin -> vaadin
        .loginView(LoginView.class)
    )
    .formLogin(form -> form.disable())
    .oneTimeTokenLogin(ott -> ott.showDefaultSubmitPage(false));
```

### Root cause hypothesis
`VaadinSecurityConfigurer.init()` calls `http.formLogin(...)` internally to register `/login`
as the custom login page — this is what normally suppresses `DefaultLoginPageGeneratingFilter`.
Then `formLogin(form -> form.disable())` removes the FormLoginConfigurer, so Spring Security
sees no custom login page set and generates its own OTT page at `/login`.

### Attempted fix (did NOT work)
Removing `formLogin(form -> form.disable())` — all 96 tests still passed, but the Spring
Security page still appeared in the browser. Change was reverted.

This means the hypothesis above is incomplete. Something else is also generating the page,
or the Vaadin `loginView()` configuration is not wiring up Spring Security the way expected.

### Next investigation steps

1. **Run the app with debug logging and inspect the filter chain** for the `/login` URL:
   ```
   mvn spring-boot:run -Dspring-boot.run.arguments="--debug"
   ```
   Look for which filter is intercepting GET /login and generating the OTT form.
   Candidates: `DefaultLoginPageGeneratingFilter`, `GenerateOneTimeTokenFilter`.

2. **Check if `GenerateOneTimeTokenFilter` defaults to `/login`** as its token-generating
   URL. If so, it intercepts GET /login to show the token-request form regardless of
   the custom login page setting. Fix would be to move it to a different URL:
   ```java
   .oneTimeTokenLogin(ott -> ott
       .showDefaultSubmitPage(false)
       .tokenGeneratingUrl("/login/generate-token")
   )
   ```

3. **Try adding `.loginPage("/login")` explicitly on the OTT configurer:**
   ```java
   .oneTimeTokenLogin(ott -> ott
       .showDefaultSubmitPage(false)
       .loginPage("/login")
   )
   ```

4. **Check Vaadin docs/community** for the correct way to combine Vaadin login view
   with Spring Security OTT login (there may be a known configuration pattern).

---

## Next TDD items (after the bug fix)

Remaining UI polish:
- Button variants: LUMO_PRIMARY on Save/Create buttons, LUMO_ERROR on Delete/Disable button
- Error notification variants (LUMO_ERROR) for failure cases
- ConfirmDialog improvements
- FormLayout, Binder

---

## Key Technical Notes

- **Vaadin 25** with Java Flow (server-side, NOT React/Hilla)
- **Spring Boot 4.0.2**, **Spring Security 7.0.2**, **Spring Modulith 2.0.2**
- **Java 25**, **PostgreSQL 18**, **Flyway**
- **Karibu Testing 2.6.2** for Vaadin UI tests (no browser, server-side)
- **`AuthenticationContext`** (Vaadin's Spring Security integration) — mark field `transient`
- **Module structure:** `app.meads` = root public (contains `MainLayout`), `app.meads.identity` = identity public API, `app.meads.identity.internal` = private (views, repos, services, `SecurityConfig`)
- **`Notification.setText()`** stores text under element property `"text"`. Assert via:
  `notification.getElement().getProperty("text")`
- **Karibu test pattern for `@WithMockUser`:** see `resolveAuthentication` + `propagateSecurityContext` helpers in `UserListViewTest` and `MainLayoutTest`
