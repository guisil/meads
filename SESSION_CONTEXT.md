# Session Context

## How to Resume

1. Read `CLAUDE.md` for project workflow (strict TDD: RED-GREEN-REFACTOR, each step a separate response).
2. Read this file for what's done and what's next.
3. Read `docs/plans/2026-02-27-userservice-refactor-plan.md` for the full implementation plan.
4. Read `doc/examples/VaadinUITestExample.java` for Karibu test patterns.

## Branch

`new_beginning` (based on `main`)

## Tests passing: 106 (96 original + 10 new UserService unit tests)

---

## Current Work: UserService Refactor

**Design doc:** `docs/plans/2026-02-27-userservice-refactor-design.md`
**Implementation plan:** `docs/plans/2026-02-27-userservice-refactor-plan.md`

### Completed Tasks

- **Task 1:** Added `spring-boot-starter-validation` to pom.xml (commit `570b56d`)
- **Tasks 2-6:** Added `createUser`, `updateUser`, `findAll`, `findById`, `isEditingSelf` to `UserService` with `@Validated`/`@Email`/`@NotBlank` annotations. Added 10 unit tests. All 106 tests pass. (commit `e23f368`)

### Code review finding to address

- Add `@NotNull` on `status` and `role` parameters in `createUser()` (from code quality review)

### Remaining Tasks (in order)

- **Task 7:** Bean Validation integration tests — create `UserServiceValidationTest` with `@SpringBootTest`, 4 tests (blank email, invalid email format, blank name on create, blank name on update). Also add `@NotNull` tests for status/role.
- **Task 8:** Refactor `UserListView` — remove `UserRepository` dependency, delegate all CRUD to `UserService`. 52 existing Karibu tests must pass.
- **Task 9:** Remove email regex from `LoginView` — replace custom regex with blank-only check, rely on `EmailField` for format.
- **Task 10:** Update `CLAUDE.md` — add Bean Validation to tech stack, add Validation Pattern to Code Conventions.
- **Task 11:** Full suite verification — `mvn clean test`, `ModulithStructureTest`, update this file.

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
