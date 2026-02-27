# Session Context

## How to Resume

1. Read `CLAUDE.md` for project workflow (strict TDD: RED-GREEN-REFACTOR, each step a separate response).
2. Read this file for what's done and what's next.

## Branch

`new_beginning` (based on `main`)

## Tests passing: 111

---

## Completed Work: UserService Refactor

**Design doc:** `docs/plans/2026-02-27-userservice-refactor-design.md`
**Implementation plan:** `docs/plans/2026-02-27-userservice-refactor-plan.md`

All 11 tasks completed:

- **Task 1:** Added `spring-boot-starter-validation` to pom.xml
- **Tasks 2-6:** Added `createUser`, `updateUser`, `findAll`, `findById`, `isEditingSelf` to `UserService` with `@Validated`/`@Email`/`@NotBlank` annotations. Added 10 unit tests.
- **Task 7:** Bean Validation integration tests — `UserServiceValidationTest` with 6 tests (blank email, invalid email, blank name create/update, null status, null role). Added `@NotNull` on `status` and `role` params.
- **Task 8:** Refactored `UserListView` — removed `UserRepository` dependency, all CRUD delegates to `UserService`. Handles `ConstraintViolationException` for validation errors. All 32 Karibu tests pass.
- **Task 9:** Removed custom email regex from `LoginView` — blank-only check remains, `EmailField` handles format validation client-side. Removed untestable server-side format test.
- **Task 10:** Updated `CLAUDE.md` — added Bean Validation to tech stack, added Validation Pattern to Code Conventions.
- **Task 11:** Full suite verification — 111 tests pass, ModulithStructureTest passes.

---

## Previously Completed (from earlier sessions)

- MainLayout with AppLayout, "MEADS" title, logout button, admin-only Users nav link
- UserListView with CRUD dialogs, EmailField, LUMO_SUCCESS notifications
- LoginView with EmailField
- Fix: "User disabled successfully" message on soft-delete

---

## Unresolved bug: Spring Security default OTT page shown at /login

(See `docs/identity-review.md` for full analysis. Will be addressed when auth mechanism decision is made — may be fixed by switching to password-based auth.)

---

## Key Technical Notes

- **Java 25** (set as system default via `sdk default java 25.0.2-tem`)
- **Vaadin 25** with Java Flow (server-side, NOT React/Hilla)
- **Spring Boot 4.0.2**, **Spring Security 7.0.2**, **Spring Modulith 2.0.2**
- **PostgreSQL 18**, **Flyway** (highest migration: V3)
- **Karibu Testing 2.6.2** for Vaadin UI tests (no browser, server-side)
- **`AuthenticationContext`** fields must be `transient` in Vaadin views
- **Karibu test pattern for `@WithMockUser`:** see `resolveAuthentication` + `propagateSecurityContext` helpers in `UserListViewTest` and `MainLayoutTest`
- **Notification text** asserted via `notification.getElement().getProperty("text")`
