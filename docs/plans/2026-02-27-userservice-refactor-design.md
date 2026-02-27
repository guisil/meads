# Design: Move Business Logic from UserListView to UserService

**Date:** 2026-02-27
**Status:** Approved
**Approach:** Thin service methods (Approach A)

---

## Problem

`UserListView` directly accesses `UserRepository` for create, update, and list operations,
bypassing the service layer. Business validation (email format, uniqueness, blank checks)
lives in the view. This violates the pattern where services are the public API and
repositories are internal. Since `identity` is the reference module, this must be fixed
before other modules copy the pattern.

## Changes

### 1. Add `spring-boot-starter-validation` dependency

Add Jakarta Bean Validation to `pom.xml`. Enables `@Email`, `@NotBlank`, and `@Validated`
for service-level input validation. Replaces custom email regex patterns.

### 2. Expand `UserService` API

Add `@Validated` to the class. New methods:

| Method | Responsibility |
|--------|---------------|
| `createUser(@Email @NotBlank email, @NotBlank name, status, role)` | Validate inputs, check email uniqueness, create and save User |
| `updateUser(userId, @NotBlank name, role, status, currentUserEmail)` | Load user, reject self role/status change, validate, update and save |
| `findAll()` | Delegate to repository |
| `findById(UUID)` | Delegate to repository, throw if not found |
| `isEditingSelf(UUID userId, String currentUserEmail)` | Load user, compare email |

Existing `deleteUser(UUID, String)` unchanged.

Validation failures throw `IllegalArgumentException` (for business rules like uniqueness
and self-edit) or `ConstraintViolationException` (for `@Email`/`@NotBlank` via Bean Validation).

### 3. Slim down `UserListView`

- Remove `UserRepository` constructor parameter entirely
- Replace all `userRepository.findAll()` calls (4 occurrences) with `userService.findAll()`
- Replace `userRepository.findById()` with `userService.findById()`
- Replace `userRepository.findByEmail()` uniqueness check with service-level check
- Replace `userRepository.save(new User(...))` with `userService.createUser(...)`
- Replace `userRepository.save(managedUser)` with `userService.updateUser(...)`
- Remove email regex validation from view (service handles it via `@Email`)
- Keep: dialog lifecycle, field error display, notifications, grid setup, `isEditingSelf()` call for disabling UI fields

### 4. Unify email validation

- Remove regex `^[^@]+@[^@]+\\.[^@]+$` from `UserListView`
- Remove regex `^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$` from `LoginView`
- Both replaced by `@Email` annotation on `UserService.createUser()` parameter
- `LoginView` keeps Vaadin `EmailField` for client-side UX feedback (not enforcement)

### 5. Update project docs

- Add `spring-boot-starter-validation` to CLAUDE.md tech stack
- Note `@Validated` + Bean Validation as the canonical validation pattern

## Testing Strategy

### New unit tests (UserServiceTest) — 14 tests, each a full TDD cycle

1. `shouldCreateUserSuccessfully`
2. `shouldRejectCreateWhenEmailIsBlank`
3. `shouldRejectCreateWhenEmailFormatIsInvalid`
4. `shouldRejectCreateWhenEmailAlreadyExists`
5. `shouldRejectCreateWhenNameIsBlank`
6. `shouldUpdateUserSuccessfully`
7. `shouldRejectUpdateWhenNameIsBlank`
8. `shouldRejectSelfRoleChange`
9. `shouldRejectSelfStatusChange`
10. `shouldFindAllUsers`
11. `shouldFindUserById`
12. `shouldThrowWhenUserNotFoundById`
13. `shouldReturnTrueWhenEditingSelf`
14. `shouldReturnFalseWhenNotEditingSelf`

### Existing tests as safety net

52 `UserListViewTest` tests must continue passing after the view refactor.
3 existing `UserServiceTest` tests for `deleteUser()` remain unchanged.

### No new integration/repository tests needed

Repository methods are already exercised by existing integration tests.
New service methods are orchestration logic testable with mocks.

## Sequencing

Per CLAUDE.md multi-layer sequencing: unit tests for service first, then view refactor.
Each unit test is a full RED-GREEN-REFACTOR cycle (3 responses with confirmation gates).
