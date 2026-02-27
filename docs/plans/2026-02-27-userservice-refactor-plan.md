# UserService Refactor Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Move business logic from `UserListView` to `UserService`, add Bean Validation, and establish the canonical service-layer pattern for future modules.

**Architecture:** `UserService` becomes the single public API for all user operations. It uses `@Validated` with `@Email`/`@NotBlank` for input format validation and manual checks for business rules (uniqueness, self-edit). `UserListView` delegates to the service for all persistence and validation; it keeps only UI wiring and dialog lifecycle.

**Tech Stack:** Spring Boot 4.0.2, Jakarta Bean Validation (`spring-boot-starter-validation`), Mockito + AssertJ (unit tests), `@SpringBootTest` + Testcontainers (validation integration tests), Karibu Testing (existing UI tests as safety net).

**Design doc:** `docs/plans/2026-02-27-userservice-refactor-design.md`

---

### Task 1: Add `spring-boot-starter-validation` dependency

**Type:** Fast cycle (no new behavior, config change only)

**Files:**
- Modify: `pom.xml:66` (after `spring-boot-starter-security`)

**Step 1: Add dependency to pom.xml**

Add after the `spring-boot-starter-security` dependency (line 67):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

**Step 2: Verify build compiles**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Run full test suite**

Run: `mvn test -Dsurefire.useFile=false`
Expected: All 96 tests pass, no regressions.

**Step 4: Commit**

```
Add: spring-boot-starter-validation dependency
```

---

### Task 2: `createUser` — happy path (Full TDD cycle)

**Files:**
- Test: `src/test/java/app/meads/identity/UserServiceTest.java`
- Modify: `src/main/java/app/meads/identity/UserService.java`

**Step 1: RED — Write the failing test**

Add to `UserServiceTest.java`:

```java
@Test
void shouldCreateUserSuccessfully() {
    // Arrange
    String email = "new@example.com";
    String name = "New User";
    given(userRepository.existsByEmail(email)).willReturn(false);
    given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

    // Act
    User result = userService.createUser(email, name, UserStatus.PENDING, Role.USER);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getEmail()).isEqualTo(email);
    assertThat(result.getName()).isEqualTo(name);
    assertThat(result.getStatus()).isEqualTo(UserStatus.PENDING);
    assertThat(result.getRole()).isEqualTo(Role.USER);
    assertThat(result.getId()).isNotNull();
    then(userRepository).should().existsByEmail(email);
    then(userRepository).should().save(any(User.class));
}
```

Run: `mvn test -Dtest=UserServiceTest#shouldCreateUserSuccessfully -Dsurefire.useFile=false`
Expected: FAIL — `createUser` method does not exist.

**Step 2: GREEN — Implement `createUser`**

Add to `UserService.java`:

```java
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import java.util.List;
```

Add `@Validated` to class declaration (alongside existing `@Service` and `@Transactional`).

Add method:

```java
public User createUser(@Email @NotBlank String email, @NotBlank String name, UserStatus status, Role role) {
    if (userRepository.existsByEmail(email)) {
        throw new IllegalArgumentException("Email already exists");
    }
    var user = new User(UUID.randomUUID(), email, name, status, role);
    return userRepository.save(user);
}
```

Run: `mvn test -Dtest=UserServiceTest#shouldCreateUserSuccessfully -Dsurefire.useFile=false`
Expected: PASS

**Step 3: REFACTOR — Run full class**

Run: `mvn test -Dtest=UserServiceTest -Dsurefire.useFile=false`
Expected: All 4 tests pass (3 existing + 1 new).

---

### Task 3: `createUser` — email uniqueness rejection (Full TDD cycle)

**Files:**
- Test: `src/test/java/app/meads/identity/UserServiceTest.java`

**Step 1: RED — Write the failing test**

```java
@Test
void shouldRejectCreateWhenEmailAlreadyExists() {
    // Arrange
    String email = "existing@example.com";
    given(userRepository.existsByEmail(email)).willReturn(true);

    // Act & Assert
    assertThatThrownBy(() -> userService.createUser(email, "Name", UserStatus.PENDING, Role.USER))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Email already exists");

    then(userRepository).should(never()).save(any());
}
```

Add import: `import static org.mockito.Mockito.never;`

Run: `mvn test -Dtest=UserServiceTest#shouldRejectCreateWhenEmailAlreadyExists -Dsurefire.useFile=false`
Expected: PASS (implementation already handles this from Task 2).

Note: This test passes immediately because `createUser` already checks uniqueness. This is expected — the test documents the behavior. Proceed to refactor step.

**Step 2: REFACTOR**

Run: `mvn test -Dtest=UserServiceTest -Dsurefire.useFile=false`
Expected: All 5 tests pass.

---

### Task 4: `updateUser` — happy path (Full TDD cycle)

**Files:**
- Test: `src/test/java/app/meads/identity/UserServiceTest.java`
- Modify: `src/main/java/app/meads/identity/UserService.java`

**Step 1: RED — Write the failing test**

```java
@Test
void shouldUpdateUserSuccessfully() {
    // Arrange
    UUID userId = UUID.randomUUID();
    User user = new User(userId, "user@example.com", "Old Name", UserStatus.ACTIVE, Role.USER);
    String currentUserEmail = "admin@example.com";

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

    // Act
    User result = userService.updateUser(userId, "New Name", Role.SYSTEM_ADMIN, UserStatus.ACTIVE, currentUserEmail);

    // Assert
    assertThat(result.getName()).isEqualTo("New Name");
    assertThat(result.getRole()).isEqualTo(Role.SYSTEM_ADMIN);
    then(userRepository).should().save(user);
}
```

Run: `mvn test -Dtest=UserServiceTest#shouldUpdateUserSuccessfully -Dsurefire.useFile=false`
Expected: FAIL — `updateUser` method does not exist.

**Step 2: GREEN — Implement `updateUser`**

Add to `UserService.java`:

```java
public User updateUser(UUID userId, @NotBlank String name, Role role, UserStatus status, String currentUserEmail) {
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    if (user.getEmail().equals(currentUserEmail)) {
        if (!role.equals(user.getRole())) {
            throw new IllegalArgumentException("Cannot change your own role");
        }
        if (!status.equals(user.getStatus())) {
            throw new IllegalArgumentException("Cannot change your own status");
        }
    }

    user.updateDetails(name, role, status);
    return userRepository.save(user);
}
```

Run: `mvn test -Dtest=UserServiceTest#shouldUpdateUserSuccessfully -Dsurefire.useFile=false`
Expected: PASS

**Step 3: REFACTOR**

Run: `mvn test -Dtest=UserServiceTest -Dsurefire.useFile=false`
Expected: All 6 tests pass.

---

### Task 5: `updateUser` — self-role and self-status rejection (Full TDD cycles)

**Files:**
- Test: `src/test/java/app/meads/identity/UserServiceTest.java`

**Step 1: RED — Self-role change test**

```java
@Test
void shouldRejectSelfRoleChange() {
    // Arrange
    UUID userId = UUID.randomUUID();
    User user = new User(userId, "admin@example.com", "Admin", UserStatus.ACTIVE, Role.USER);
    String currentUserEmail = "admin@example.com";

    given(userRepository.findById(userId)).willReturn(Optional.of(user));

    // Act & Assert
    assertThatThrownBy(() -> userService.updateUser(userId, "Admin", Role.SYSTEM_ADMIN, UserStatus.ACTIVE, currentUserEmail))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot change your own role");
}
```

Run: `mvn test -Dtest=UserServiceTest#shouldRejectSelfRoleChange -Dsurefire.useFile=false`
Expected: PASS (implementation from Task 4 already handles this).

**Step 2: RED — Self-status change test**

```java
@Test
void shouldRejectSelfStatusChange() {
    // Arrange
    UUID userId = UUID.randomUUID();
    User user = new User(userId, "admin@example.com", "Admin", UserStatus.ACTIVE, Role.USER);
    String currentUserEmail = "admin@example.com";

    given(userRepository.findById(userId)).willReturn(Optional.of(user));

    // Act & Assert
    assertThatThrownBy(() -> userService.updateUser(userId, "Admin", Role.USER, UserStatus.DISABLED, currentUserEmail))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot change your own status");
}
```

Run: `mvn test -Dtest=UserServiceTest#shouldRejectSelfStatusChange -Dsurefire.useFile=false`
Expected: PASS (implementation from Task 4 already handles this).

**Step 3: REFACTOR**

Run: `mvn test -Dtest=UserServiceTest -Dsurefire.useFile=false`
Expected: All 8 tests pass.

---

### Task 6: Query methods — `findAll`, `findById`, `isEditingSelf` (Full TDD cycles)

**Files:**
- Test: `src/test/java/app/meads/identity/UserServiceTest.java`
- Modify: `src/main/java/app/meads/identity/UserService.java`

**Step 1: RED — `shouldFindAllUsers`**

```java
@Test
void shouldFindAllUsers() {
    // Arrange
    var users = List.of(
            new User(UUID.randomUUID(), "a@example.com", "A", UserStatus.ACTIVE, Role.USER),
            new User(UUID.randomUUID(), "b@example.com", "B", UserStatus.PENDING, Role.SYSTEM_ADMIN)
    );
    given(userRepository.findAll()).willReturn(users);

    // Act
    List<User> result = userService.findAll();

    // Assert
    assertThat(result).hasSize(2);
    then(userRepository).should().findAll();
}
```

Add import: `import java.util.List;`

Run: `mvn test -Dtest=UserServiceTest#shouldFindAllUsers -Dsurefire.useFile=false`
Expected: FAIL — `findAll` method does not exist.

**Step 2: GREEN — Implement `findAll`**

Add to `UserService.java`:

```java
public List<User> findAll() {
    return userRepository.findAll();
}
```

Add import: `import java.util.List;`

Run: `mvn test -Dtest=UserServiceTest#shouldFindAllUsers -Dsurefire.useFile=false`
Expected: PASS

**Step 3: RED — `shouldFindUserById`**

```java
@Test
void shouldFindUserById() {
    // Arrange
    UUID userId = UUID.randomUUID();
    User user = new User(userId, "user@example.com", "User", UserStatus.ACTIVE, Role.USER);
    given(userRepository.findById(userId)).willReturn(Optional.of(user));

    // Act
    User result = userService.findById(userId);

    // Assert
    assertThat(result).isEqualTo(user);
}
```

Run: `mvn test -Dtest=UserServiceTest#shouldFindUserById -Dsurefire.useFile=false`
Expected: FAIL — `findById` method does not exist.

**Step 4: GREEN — Implement `findById`**

Add to `UserService.java`:

```java
public User findById(UUID userId) {
    return userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
}
```

Run: `mvn test -Dtest=UserServiceTest#shouldFindUserById -Dsurefire.useFile=false`
Expected: PASS

**Step 5: RED — `shouldThrowWhenUserNotFoundById`**

```java
@Test
void shouldThrowWhenUserNotFoundById() {
    // Arrange
    UUID userId = UUID.randomUUID();
    given(userRepository.findById(userId)).willReturn(Optional.empty());

    // Act & Assert
    assertThatThrownBy(() -> userService.findById(userId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("User not found");
}
```

Run: `mvn test -Dtest=UserServiceTest#shouldThrowWhenUserNotFoundById -Dsurefire.useFile=false`
Expected: PASS (already handled by `findById` implementation).

**Step 6: RED — `shouldReturnTrueWhenEditingSelf`**

```java
@Test
void shouldReturnTrueWhenEditingSelf() {
    // Arrange
    UUID userId = UUID.randomUUID();
    User user = new User(userId, "admin@example.com", "Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
    given(userRepository.findById(userId)).willReturn(Optional.of(user));

    // Act
    boolean result = userService.isEditingSelf(userId, "admin@example.com");

    // Assert
    assertThat(result).isTrue();
}
```

Run: `mvn test -Dtest=UserServiceTest#shouldReturnTrueWhenEditingSelf -Dsurefire.useFile=false`
Expected: FAIL — `isEditingSelf` method does not exist.

**Step 7: GREEN — Implement `isEditingSelf`**

Add to `UserService.java`:

```java
public boolean isEditingSelf(UUID userId, String currentUserEmail) {
    return userRepository.findById(userId)
            .map(user -> user.getEmail().equals(currentUserEmail))
            .orElse(false);
}
```

Run: `mvn test -Dtest=UserServiceTest#shouldReturnTrueWhenEditingSelf -Dsurefire.useFile=false`
Expected: PASS

**Step 8: RED — `shouldReturnFalseWhenNotEditingSelf`**

```java
@Test
void shouldReturnFalseWhenNotEditingSelf() {
    // Arrange
    UUID userId = UUID.randomUUID();
    User user = new User(userId, "user@example.com", "User", UserStatus.ACTIVE, Role.USER);
    given(userRepository.findById(userId)).willReturn(Optional.of(user));

    // Act
    boolean result = userService.isEditingSelf(userId, "other@example.com");

    // Assert
    assertThat(result).isFalse();
}
```

Run: `mvn test -Dtest=UserServiceTest#shouldReturnFalseWhenNotEditingSelf -Dsurefire.useFile=false`
Expected: PASS (already handled).

**Step 9: REFACTOR — Full class**

Run: `mvn test -Dtest=UserServiceTest -Dsurefire.useFile=false`
Expected: All 13 tests pass (3 original + 10 new).

**Step 10: Commit**

```
Add: createUser, updateUser, findAll, findById, isEditingSelf to UserService

Service now owns all user CRUD logic with Bean Validation annotations.
Unit tests cover business rules (uniqueness, self-edit prevention, not-found).
```

---

### Task 7: Bean Validation integration tests (Full TDD cycle)

**Files:**
- Create: `src/test/java/app/meads/identity/UserServiceValidationTest.java`

These tests verify that `@Validated` + `@Email` + `@NotBlank` fire through Spring's proxy.
They require `@SpringBootTest` because Bean Validation on method parameters needs the
Spring `MethodValidationInterceptor`.

**Step 1: RED — Write all 4 validation tests**

```java
package app.meads.identity;

import app.meads.TestcontainersConfiguration;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class UserServiceValidationTest {

    @Autowired
    UserService userService;

    @Test
    void shouldRejectCreateWhenEmailIsBlank() {
        assertThatThrownBy(() -> userService.createUser("", "Name", UserStatus.PENDING, Role.USER))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void shouldRejectCreateWhenEmailFormatIsInvalid() {
        assertThatThrownBy(() -> userService.createUser("not-an-email", "Name", UserStatus.PENDING, Role.USER))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void shouldRejectCreateWhenNameIsBlank() {
        assertThatThrownBy(() -> userService.createUser("valid@example.com", "", UserStatus.PENDING, Role.USER))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void shouldRejectUpdateWhenNameIsBlank() {
        // Create a user first to update
        User user = userService.createUser("update-test@example.com", "Original", UserStatus.PENDING, Role.USER);

        assertThatThrownBy(() -> userService.updateUser(user.getId(), "", Role.USER, UserStatus.PENDING, "admin@example.com"))
                .isInstanceOf(ConstraintViolationException.class);
    }
}
```

Run: `mvn test -Dtest=UserServiceValidationTest -Dsurefire.useFile=false`
Expected: All 4 PASS (implementation from Tasks 2-4 already has annotations).

**Step 2: REFACTOR**

Run: `mvn test -Dsurefire.useFile=false`
Expected: Full suite passes.

**Step 3: Commit**

```
Add: Bean Validation integration tests for UserService
```

---

### Task 8: Refactor `UserListView` to delegate to `UserService`

**Type:** Fast cycle — existing 52 `UserListViewTest` tests cover the behavior.

**Files:**
- Modify: `src/main/java/app/meads/identity/internal/UserListView.java`

**Step 1: State covering tests**

`UserListViewTest` (52 tests) covers all user-facing view behavior. These tests will catch any regression.

**Step 2: Make the changes**

Replace `UserListView` contents. Key changes:

1. **Remove `UserRepository` from constructor and field.** Constructor becomes:
   `UserListView(UserService userService, MagicLinkService magicLinkService, AuthenticationContext authenticationContext)`

2. **Replace `userRepository.findAll()` (4 occurrences) with `userService.findAll()`:**
   - Line 66: `grid.setItems(userService.findAll());`
   - Line 115: `grid.setItems(userService.findAll());`
   - Line 184: `grid.setItems(userService.findAll());`
   - Line 259: `grid.setItems(userService.findAll());`

3. **Replace create dialog save handler (lines 215-263).** Remove email regex validation and `userRepository.findByEmail` check. Replace `new User(...)` + `userRepository.save()` with:
   ```java
   try {
       userService.createUser(
           emailField.getValue(),
           nameField.getValue(),
           statusSelect.getValue(),
           roleSelect.getValue()
       );
       grid.setItems(userService.findAll());
       var notification = Notification.show("User created successfully");
       notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
       dialog.close();
   } catch (IllegalArgumentException ex) {
       emailField.setInvalid(true);
       emailField.setErrorMessage(ex.getMessage());
   } catch (Exception ex) {
       Notification.show("Failed to save user. Please try again.");
   }
   ```
   Keep the blank-field checks before the service call (for UX — immediate field-level feedback).

4. **Replace edit dialog save handler (lines 97-123).** Remove `userRepository.findById` + `userRepository.save`. Replace with:
   ```java
   try {
       String currentUserEmail = authenticationContext.getAuthenticatedUser(UserDetails.class)
               .map(UserDetails::getUsername)
               .orElse("");
       userService.updateUser(
           user.getId(),
           nameField.getValue(),
           roleSelect.getValue(),
           statusSelect.getValue(),
           currentUserEmail
       );
       grid.setItems(userService.findAll());
       var notification = Notification.show("User saved successfully");
       notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
       dialog.close();
   } catch (Exception ex) {
       nameField.setInvalid(true);
       nameField.setErrorMessage("Failed to save user. Please try again.");
   }
   ```

5. **Replace self-edit check in `openEditDialog` (lines 89-95).** Replace the inline `authenticationContext` check with:
   ```java
   String currentUserEmail = authenticationContext.getAuthenticatedUser(UserDetails.class)
           .map(UserDetails::getUsername)
           .orElse("");
   boolean isEditingSelf = userService.isEditingSelf(user.getId(), currentUserEmail);
   ```

6. **Remove `UserRepository` import.**

**Step 3: Run tests**

Run: `mvn test -Dtest=UserListViewTest -Dsurefire.useFile=false`
Expected: All 52 tests pass.

Run: `mvn test -Dsurefire.useFile=false`
Expected: Full suite passes.

**Step 4: Commit**

```
Refactor: UserListView delegates to UserService for all CRUD operations

Removed direct UserRepository access from the view. All persistence and
business validation now goes through UserService.
```

---

### Task 9: Remove email regex from `LoginView`

**Type:** Fast cycle — `LoginViewTest` covers this behavior.

**Files:**
- Modify: `src/main/java/app/meads/identity/LoginView.java:28-33`

**Step 1: State covering tests**

`LoginViewTest` covers the LoginView behavior.

**Step 2: Remove the custom regex validation**

In `LoginView.java`, remove lines 28-33 (the `emailValue.matches(...)` check and error handling). The `EmailField` component already provides client-side email format validation. The view should only check for blank/null:

```java
button.addClickListener(e -> {
    String emailValue = email.getValue();
    if (emailValue == null || emailValue.isBlank()) {
        email.setInvalid(true);
        email.setErrorMessage("Please enter a valid email address");
        return;
    }

    email.setInvalid(false);
    magicLinkService.requestMagicLink(emailValue);
    e.getSource().getUI().ifPresent(ui ->
        ui.navigate("login", QueryParameters.simple(Map.of("tokenSent", "")))
    );
});
```

**Step 3: Run tests**

Run: `mvn test -Dtest=LoginViewTest -Dsurefire.useFile=false`
Expected: PASS. If any test relied on the specific regex rejection, it will fail and we fix the test.

Run: `mvn test -Dsurefire.useFile=false`
Expected: Full suite passes.

**Step 4: Commit**

```
Remove: custom email regex from LoginView — rely on EmailField validation
```

---

### Task 10: Update `CLAUDE.md`

**Type:** Fast cycle — documentation only.

**Files:**
- Modify: `CLAUDE.md`

**Step 1: Update tech stack**

In the tech stack section, add `Jakarta Bean Validation (spring-boot-starter-validation)` to the list.

**Step 2: Add validation pattern to Code Conventions**

Add a new subsection after "Service Pattern":

```markdown
### Validation Pattern
**Reference:** `UserService.java`
- Add `@Validated` to service classes that need input validation
- Use `@Email`, `@NotBlank` on method parameters for format/presence checks
- Use manual checks + `IllegalArgumentException` for business rules (uniqueness, self-referential edits)
- Bean Validation throws `ConstraintViolationException`; business rules throw `IllegalArgumentException`
- Views keep basic blank checks for UX (immediate field-level feedback) but delegate enforcement to services
```

**Step 3: Update Service Pattern**

In the existing "Service Pattern" subsection, add:
- `@Validated` for input validation (alongside existing `@Service` + `@Transactional`)

**Step 4: Commit**

```
Update: CLAUDE.md with Bean Validation patterns and dependency
```

---

### Task 11: Full suite verification

**Step 1: Run full test suite**

Run: `mvn clean test -Dsurefire.useFile=false`
Expected: All tests pass (96 original + 14 new = ~110 tests).

**Step 2: Run modulith structure test**

Run: `mvn test -Dtest=ModulithStructureTest -Dsurefire.useFile=false`
Expected: PASS — no module boundary violations.

**Step 3: Final commit (if any remaining changes)**

Squash any uncommitted adjustments. Update `SESSION_CONTEXT.md` to reflect completed work.
