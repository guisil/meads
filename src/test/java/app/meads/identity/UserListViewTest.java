package app.meads.identity;

import app.meads.TestcontainersConfiguration;
import app.meads.identity.internal.UserListView;
import app.meads.identity.internal.UserRepository;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;

import java.util.UUID;

import static com.github.mvysny.kaributesting.v10.LocatorJ.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UserListViewTest {

    @Autowired
    ApplicationContext ctx;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void setup() {
        var routes = new Routes().autoDiscoverViews("app.meads");
        var servlet = new MockSpringServlet(routes, ctx, UI::new);
        MockVaadin.setup(UI::new, servlet);
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void shouldDisplayUserListViewWithGrid() {
        UI.getCurrent().navigate("users");

        var grid = _get(Grid.class);
        assertThat(grid).isNotNull();
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldHaveRolesAllowedAnnotationForSecurity() {
        // Note: @RolesAllowed("SYSTEM_ADMIN") on UserListView is enforced by Spring Security
        // at runtime, but Karibu tests don't go through the full security filter chain.
        // This test documents that the security annotation exists.
        // In production, regular users will get an access denied error.

        var annotation = UserListView.class.getAnnotation(jakarta.annotation.security.RolesAllowed.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly("SYSTEM_ADMIN");
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void shouldDisplayEditButtonForEachUser() {
        UI.getCurrent().navigate("users");

        var grid = _get(Grid.class);
        assertThat(grid.getColumns()).hasSizeGreaterThan(4);
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void shouldOpenDialogWhenEditButtonClicked() {
        // Arrange - create a test user
        var userId = UUID.randomUUID();
        var user = new User(
                userId,
                "edit-test-" + userId + "@example.com",
                "Test User",
                UserStatus.ACTIVE,
                Role.USER
        );
        userRepository.save(user);

        // Act - navigate and trigger edit dialog
        // Note: Buttons in grid component columns aren't accessible via Karibu Testing locators,
        // so we test the dialog opening logic directly
        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.openEditDialog(user);

        // Assert - dialog should be present
        var dialog = _get(Dialog.class);
        assertThat(dialog.isOpened()).isTrue();
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void shouldDisplayFormFieldsInEditDialog() {
        // Arrange - create a test user
        var userId = UUID.randomUUID();
        var user = new User(
                userId,
                "form-test-" + userId + "@example.com",
                "Test User",
                UserStatus.ACTIVE,
                Role.USER
        );
        userRepository.save(user);

        // Act - navigate and open edit dialog
        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.openEditDialog(user);

        // Assert - form fields should be present
        var nameField = _get(TextField.class, spec -> spec.withCaption("Name"));
        var roleSelect = _get(Select.class, spec -> spec.withCaption("Role"));
        var statusSelect = _get(Select.class, spec -> spec.withCaption("Status"));

        assertThat(nameField.getValue()).isEqualTo("Test User");
        assertThat(roleSelect.getValue()).isEqualTo(Role.USER);
        assertThat(statusSelect.getValue()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void shouldUpdateUserWhenSaveButtonClicked() {
        // Arrange - create a test user
        var userId = UUID.randomUUID();
        var user = new User(
                userId,
                "save-test-" + userId + "@example.com",
                "Original Name",
                UserStatus.PENDING,
                Role.USER
        );
        userRepository.save(user);

        // Act - navigate, open dialog, change values, and save
        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.openEditDialog(user);

        var nameField = _get(TextField.class, spec -> spec.withCaption("Name"));
        var roleSelect = _get(Select.class, spec -> spec.withCaption("Role"));
        var statusSelect = _get(Select.class, spec -> spec.withCaption("Status"));

        nameField.setValue("Updated Name");
        roleSelect.setValue(Role.SYSTEM_ADMIN);
        statusSelect.setValue(UserStatus.ACTIVE);

        var saveButton = _get(Button.class, spec -> spec.withText("Save"));
        _click(saveButton);

        // Assert - user should be updated in database and dialog closed
        var updatedUser = userRepository.findById(userId).orElseThrow();
        assertThat(updatedUser.getName()).isEqualTo("Updated Name");
        assertThat(updatedUser.getRole()).isEqualTo(Role.SYSTEM_ADMIN);
        assertThat(updatedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);

        // Dialog should be closed and removed from UI
        assertThat(_find(Dialog.class)).isEmpty();
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void shouldCloseDialogWithoutSavingWhenCancelButtonClicked() {
        // Arrange - create a test user
        var userId = UUID.randomUUID();
        var user = new User(
                userId,
                "cancel-test-" + userId + "@example.com",
                "Original Name",
                UserStatus.PENDING,
                Role.USER
        );
        userRepository.save(user);

        // Act - navigate, open dialog, change values, and cancel
        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.openEditDialog(user);

        var nameField = _get(TextField.class, spec -> spec.withCaption("Name"));
        var roleSelect = _get(Select.class, spec -> spec.withCaption("Role"));
        var statusSelect = _get(Select.class, spec -> spec.withCaption("Status"));

        nameField.setValue("Changed Name");
        roleSelect.setValue(Role.SYSTEM_ADMIN);
        statusSelect.setValue(UserStatus.ACTIVE);

        var cancelButton = _get(Button.class, spec -> spec.withText("Cancel"));
        _click(cancelButton);

        // Assert - user should be unchanged in database and dialog closed
        var unchangedUser = userRepository.findById(userId).orElseThrow();
        assertThat(unchangedUser.getName()).isEqualTo("Original Name");
        assertThat(unchangedUser.getRole()).isEqualTo(Role.USER);
        assertThat(unchangedUser.getStatus()).isEqualTo(UserStatus.PENDING);

        // Dialog should be closed and removed from UI
        assertThat(_find(Dialog.class)).isEmpty();
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void shouldNotSaveWhenNameFieldIsEmpty() {
        // Arrange - create a test user
        var userId = UUID.randomUUID();
        var user = new User(
                userId,
                "validation-test-" + userId + "@example.com",
                "Original Name",
                UserStatus.PENDING,
                Role.USER
        );
        userRepository.save(user);

        // Act - navigate, open dialog, clear name field, and try to save
        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.openEditDialog(user);

        var nameField = _get(TextField.class, spec -> spec.withCaption("Name"));
        nameField.setValue("");

        var saveButton = _get(Button.class, spec -> spec.withText("Save"));
        _click(saveButton);

        // Assert - user should be unchanged in database
        var unchangedUser = userRepository.findById(userId).orElseThrow();
        assertThat(unchangedUser.getName()).isEqualTo("Original Name");

        // Dialog should still be open (save failed due to validation)
        var dialog = _get(Dialog.class);
        assertThat(dialog.isOpened()).isTrue();

        // Name field should show validation error
        assertThat(nameField.isInvalid()).isTrue();
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void shouldRefreshGridAfterSavingChanges() {
        // Arrange - create a test user
        var userId = UUID.randomUUID();
        var user = new User(
                userId,
                "refresh-test-" + userId + "@example.com",
                "Original Name",
                UserStatus.PENDING,
                Role.USER
        );
        userRepository.save(user);

        // Act - navigate, open dialog, change values, and save
        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        @SuppressWarnings("unchecked")
        Grid<User> grid = _get(Grid.class);

        view.openEditDialog(user);

        var nameField = _get(TextField.class, spec -> spec.withCaption("Name"));
        var roleSelect = _get(Select.class, spec -> spec.withCaption("Role"));
        var statusSelect = _get(Select.class, spec -> spec.withCaption("Status"));

        nameField.setValue("Updated Name");
        roleSelect.setValue(Role.SYSTEM_ADMIN);
        statusSelect.setValue(UserStatus.ACTIVE);

        var saveButton = _get(Button.class, spec -> spec.withText("Save"));
        _click(saveButton);

        // Assert - grid should display updated values
        var gridItems = grid.getListDataView().getItems().toList();
        var updatedUserInGrid = gridItems.stream()
                .filter(u -> u.getId().equals(userId))
                .findFirst()
                .orElseThrow();

        assertThat(updatedUserInGrid.getName()).isEqualTo("Updated Name");
        assertThat(updatedUserInGrid.getRole()).isEqualTo(Role.SYSTEM_ADMIN);
        assertThat(updatedUserInGrid.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void shouldShowErrorWhenSaveFails() {
        // Arrange - create a test user
        var userId = UUID.randomUUID();
        var user = new User(
                userId,
                "error-test-" + userId + "@example.com",
                "Original Name",
                UserStatus.PENDING,
                Role.USER
        );
        userRepository.save(user);

        // Act - navigate, open dialog, delete user (simulate concurrent deletion), and try to save
        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.openEditDialog(user);

        var nameField = _get(TextField.class, spec -> spec.withCaption("Name"));
        nameField.setValue("Updated Name");

        // Delete the user while dialog is open (simulates concurrent deletion or database error)
        userRepository.deleteById(userId);

        var saveButton = _get(Button.class, spec -> spec.withText("Save"));
        _click(saveButton);

        // Assert - dialog should still be open
        var dialog = _get(Dialog.class);
        assertThat(dialog.isOpened()).isTrue();

        // Name field should show an error
        assertThat(nameField.isInvalid()).isTrue();
        assertThat(nameField.getErrorMessage()).contains("Failed to save");
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void shouldShowSuccessNotificationAfterSaving() {
        // Arrange - create a test user
        var userId = UUID.randomUUID();
        var user = new User(
                userId,
                "notification-test-" + userId + "@example.com",
                "Original Name",
                UserStatus.PENDING,
                Role.USER
        );
        userRepository.save(user);

        // Act - navigate, open dialog, change values, and save
        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.openEditDialog(user);

        var nameField = _get(TextField.class, spec -> spec.withCaption("Name"));
        nameField.setValue("Updated Name");

        var saveButton = _get(Button.class, spec -> spec.withText("Save"));
        _click(saveButton);

        // Assert - success notification should be shown
        var notification = _get(Notification.class);
        assertThat(notification.isOpened()).isTrue();
    }

    @Test
    @DirtiesContext
    void shouldDisableRoleAndStatusFieldsWhenEditingSelf() {
        // Arrange - create a user with the same email as the logged-in user
        var userId = UUID.randomUUID();
        var currentUser = new User(
                userId,
                "admin@example.com",
                "Admin User",
                UserStatus.ACTIVE,
                Role.SYSTEM_ADMIN
        );
        userRepository.save(currentUser);

        // Manually set up authentication to ensure it's present
        var authorities = java.util.List.of(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN")
        );
        var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            "admin@example.com",
            null,
            authorities
        );
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);

        // Verify the security context is set
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("admin@example.com");

        // Act - navigate and open edit dialog for current user
        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.openEditDialog(currentUser);

        // Assert - role and status fields should be disabled
        var nameField = _get(TextField.class, spec -> spec.withCaption("Name"));
        var roleSelect = _get(Select.class, spec -> spec.withCaption("Role"));
        var statusSelect = _get(Select.class, spec -> spec.withCaption("Status"));

        assertThat(nameField.isEnabled()).isTrue(); // Name can be edited
        assertThat(roleSelect.isEnabled()).isFalse(); // Role cannot be changed
        assertThat(statusSelect.isEnabled()).isFalse(); // Status cannot be changed
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "SYSTEM_ADMIN")
    @DirtiesContext
    void shouldSoftDeleteUserWhenStatusIsNotDisabled() {
        // Arrange - create a test user
        var userId = UUID.randomUUID();
        var user = new User(
                userId,
                "delete-test-" + userId + "@example.com",
                "Test User",
                UserStatus.ACTIVE,
                Role.USER
        );
        userRepository.save(user);

        // Act - navigate and trigger delete
        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.deleteUser(user);

        // Assert - user should be soft deleted (status changed to DISABLED)
        var deletedUser = userRepository.findById(userId).orElseThrow();
        assertThat(deletedUser.getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(userRepository.findById(userId)).isPresent(); // Still in database
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "SYSTEM_ADMIN")
    @DirtiesContext
    void shouldShowConfirmationDialogBeforeHardDelete() {
        // Arrange - create a DISABLED user
        var userId = UUID.randomUUID();
        var user = new User(
                userId,
                "hard-delete-test-" + userId + "@example.com",
                "Test User",
                UserStatus.DISABLED,
                Role.USER
        );
        userRepository.save(user);

        // Act - navigate and trigger delete click
        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.handleDeleteClick(user);

        // Assert - confirmation dialog should appear
        var dialog = _get(Dialog.class);
        assertThat(dialog.isOpened()).isTrue();
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "SYSTEM_ADMIN")
    @DirtiesContext
    void shouldHardDeleteUserWhenConfirmed() {
        // Arrange - create a DISABLED user
        var userId = UUID.randomUUID();
        var user = new User(
                userId,
                "confirm-delete-test-" + userId + "@example.com",
                "Test User",
                UserStatus.DISABLED,
                Role.USER
        );
        userRepository.save(user);

        // Act - navigate, trigger delete, and confirm
        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.handleDeleteClick(user);

        // Click the confirm button
        var confirmButton = _get(Button.class, spec -> spec.withCaption("Confirm"));
        _click(confirmButton);

        // Assert - user should be hard deleted (removed from database)
        assertThat(userRepository.findById(userId)).isEmpty();
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "SYSTEM_ADMIN")
    @DirtiesContext
    void shouldSendMagicLinkWhenButtonClicked() {
        // Arrange - create a test user
        var userId = UUID.randomUUID();
        var user = new User(
                userId,
                "magic-link-test-" + userId + "@example.com",
                "Test User",
                UserStatus.ACTIVE,
                Role.USER
        );
        userRepository.save(user);

        // Act - navigate and trigger send magic link
        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.sendMagicLink(user);

        // Assert - success notification should be shown
        var notification = _get(Notification.class);
        assertThat(notification.isOpened()).isTrue();
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void shouldOpenCreateUserDialogWhenCreateButtonClicked() {
        // Act - navigate and click create user button
        UI.getCurrent().navigate("users");
        var createButton = _get(Button.class, spec -> spec.withText("Create User"));
        _click(createButton);

        // Assert - dialog should be present
        var dialog = _get(Dialog.class);
        assertThat(dialog.isOpened()).isTrue();
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void shouldDisplayFormFieldsInCreateUserDialog() {
        // Act - navigate and open create dialog
        UI.getCurrent().navigate("users");
        var createButton = _get(Button.class, spec -> spec.withText("Create User"));
        _click(createButton);

        // Assert - form fields should be present and empty
        var emailField = _get(TextField.class, spec -> spec.withCaption("Email"));
        var nameField = _get(TextField.class, spec -> spec.withCaption("Name"));
        var roleSelect = _get(Select.class, spec -> spec.withCaption("Role"));
        var statusSelect = _get(Select.class, spec -> spec.withCaption("Status"));

        assertThat(emailField.getValue()).isEmpty();
        assertThat(nameField.getValue()).isEmpty();
        assertThat(roleSelect.isEmpty()).isTrue();
        assertThat(statusSelect.isEmpty()).isTrue();
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DirtiesContext
    void shouldCreateUserWhenSaveButtonClicked() {
        // Act - navigate, open dialog, fill form, and save
        UI.getCurrent().navigate("users");
        var createButton = _get(Button.class, spec -> spec.withText("Create User"));
        _click(createButton);

        var emailField = _get(TextField.class, spec -> spec.withCaption("Email"));
        var nameField = _get(TextField.class, spec -> spec.withCaption("Name"));
        var roleSelect = _get(Select.class, spec -> spec.withCaption("Role"));
        var statusSelect = _get(Select.class, spec -> spec.withCaption("Status"));

        emailField.setValue("newuser@example.com");
        nameField.setValue("New User");
        roleSelect.setValue(Role.USER);
        statusSelect.setValue(UserStatus.ACTIVE);

        var saveButton = _get(Button.class, spec -> spec.withText("Save"));
        _click(saveButton);

        // Assert - user should be created in database
        var createdUser = userRepository.findByEmail("newuser@example.com");
        assertThat(createdUser).isPresent();
        assertThat(createdUser.get().getName()).isEqualTo("New User");
        assertThat(createdUser.get().getRole()).isEqualTo(Role.USER);
        assertThat(createdUser.get().getStatus()).isEqualTo(UserStatus.ACTIVE);

        // Dialog should be closed
        assertThat(_find(Dialog.class)).isEmpty();
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DirtiesContext
    void shouldNotCreateUserWhenEmailFieldIsEmpty() {
        // Arrange - count users before
        long userCountBefore = userRepository.count();

        // Act - navigate, open dialog, leave email empty, fill other fields, and try to save
        UI.getCurrent().navigate("users");
        var createButton = _get(Button.class, spec -> spec.withText("Create User"));
        _click(createButton);

        var emailField = _get(TextField.class, spec -> spec.withCaption("Email"));
        var nameField = _get(TextField.class, spec -> spec.withCaption("Name"));
        var roleSelect = _get(Select.class, spec -> spec.withCaption("Role"));
        var statusSelect = _get(Select.class, spec -> spec.withCaption("Status"));

        // Leave email empty
        nameField.setValue("Test User");
        roleSelect.setValue(Role.USER);
        statusSelect.setValue(UserStatus.ACTIVE);

        var saveButton = _get(Button.class, spec -> spec.withText("Save"));
        _click(saveButton);

        // Assert - user should NOT be created
        assertThat(userRepository.count()).isEqualTo(userCountBefore);

        // Dialog should still be open (save failed due to validation)
        var dialog = _get(Dialog.class);
        assertThat(dialog.isOpened()).isTrue();

        // Email field should show validation error
        assertThat(emailField.isInvalid()).isTrue();
    }
}
