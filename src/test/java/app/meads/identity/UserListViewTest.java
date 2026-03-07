package app.meads.identity;

import app.meads.TestcontainersConfiguration;
import app.meads.identity.internal.UserListView;
import app.meads.identity.internal.UserRepository;
import com.github.mvysny.fakeservlet.FakeRequest;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.server.VaadinServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.github.mvysny.kaributesting.v10.LocatorJ.*;
import static org.assertj.core.api.Assertions.assertThat;

import app.meads.identity.JwtMagicLinkService;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UserListViewTest {

    @Autowired
    ApplicationContext ctx;

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserService userService;

    @BeforeEach
    void setup(TestInfo testInfo) {
        var routes = new Routes().autoDiscoverViews("app.meads");
        var servlet = new MockSpringServlet(routes, ctx, UI::new);
        MockVaadin.setup(UI::new, servlet);

        var authentication = resolveAuthentication(testInfo);
        if (authentication != null) {
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        propagateSecurityContext(authentication);
    }

    private Authentication resolveAuthentication(TestInfo testInfo) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            return auth;
        }
        var method = testInfo.getTestMethod().orElse(null);
        if (method == null) {
            return null;
        }
        var withMockUser = method.getAnnotation(WithMockUser.class);
        if (withMockUser == null) {
            return null;
        }
        var username = withMockUser.username().isEmpty() ? withMockUser.value() : withMockUser.username();
        if (username.isEmpty()) {
            username = "user";
        }
        var authorities = Arrays.stream(withMockUser.roles())
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        var userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(username)
                .password("password")
                .authorities(authorities)
                .build();
        return new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
    }

    private void propagateSecurityContext(Authentication authentication) {
        if (authentication != null) {
            var fakeRequest = (FakeRequest) VaadinServletRequest.getCurrent().getRequest();
            fakeRequest.setUserPrincipalInt(authentication);
            fakeRequest.setUserInRole((principal, role) ->
                    authentication.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_" + role)));
        }
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
        SecurityContextHolder.clearContext();
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
    void shouldRedirectToRootWhenRegularUserAccessesUsersView() {
        UI.getCurrent().navigate("users");

        assertThat(UI.getCurrent().getInternals().getActiveViewLocation().getPath()).isEmpty();
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
        var user = new User(
                "edit-test-" + UUID.randomUUID() + "@example.com",
                "Test User",
                UserStatus.ACTIVE,
                Role.USER
        );
        userRepository.save(user);

        // Act - navigate and trigger edit dialog
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
        var user = new User(
                "form-test-" + UUID.randomUUID() + "@example.com",
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
        var nameField = _get(TextField.class, spec -> spec.withLabel("Name"));
        var roleSelect = _get(Select.class, spec -> spec.withLabel("Role"));
        var statusSelect = _get(Select.class, spec -> spec.withLabel("Status"));

        assertThat(nameField.getValue()).isEqualTo("Test User");
        assertThat(roleSelect.getValue()).isEqualTo(Role.USER);
        assertThat(statusSelect.getValue()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void shouldUpdateUserWhenSaveButtonClicked() {
        // Arrange - create a test user
        var user = new User(
                "save-test-" + UUID.randomUUID() + "@example.com",
                "Original Name",
                UserStatus.PENDING,
                Role.USER
        );
        userRepository.save(user);

        // Act - navigate, open dialog, change values, and save
        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.openEditDialog(user);

        var nameField = _get(TextField.class, spec -> spec.withLabel("Name"));
        var roleSelect = _get(Select.class, spec -> spec.withLabel("Role"));
        var statusSelect = _get(Select.class, spec -> spec.withLabel("Status"));

        nameField.setValue("Updated Name");
        roleSelect.setValue(Role.SYSTEM_ADMIN);
        statusSelect.setValue(UserStatus.ACTIVE);

        var saveButton = _get(Button.class, spec -> spec.withText("Save"));
        _click(saveButton);

        // Assert - user should be updated in database and dialog closed
        var updatedUser = userRepository.findById(user.getId()).orElseThrow();
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
        var user = new User(
                "cancel-test-" + UUID.randomUUID() + "@example.com",
                "Original Name",
                UserStatus.PENDING,
                Role.USER
        );
        userRepository.save(user);

        // Act - navigate, open dialog, change values, and cancel
        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.openEditDialog(user);

        var nameField = _get(TextField.class, spec -> spec.withLabel("Name"));
        var roleSelect = _get(Select.class, spec -> spec.withLabel("Role"));
        var statusSelect = _get(Select.class, spec -> spec.withLabel("Status"));

        nameField.setValue("Changed Name");
        roleSelect.setValue(Role.SYSTEM_ADMIN);
        statusSelect.setValue(UserStatus.ACTIVE);

        var cancelButton = _get(Button.class, spec -> spec.withText("Cancel"));
        _click(cancelButton);

        // Assert - user should be unchanged in database and dialog closed
        var unchangedUser = userRepository.findById(user.getId()).orElseThrow();
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
        var user = new User(
                "validation-test-" + UUID.randomUUID() + "@example.com",
                "Original Name",
                UserStatus.PENDING,
                Role.USER
        );
        userRepository.save(user);

        // Act - navigate, open dialog, clear name field, and try to save
        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.openEditDialog(user);

        var nameField = _get(TextField.class, spec -> spec.withLabel("Name"));
        nameField.setValue("");

        var saveButton = _get(Button.class, spec -> spec.withText("Save"));
        _click(saveButton);

        // Assert - user should be unchanged in database
        var unchangedUser = userRepository.findById(user.getId()).orElseThrow();
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
        var user = new User(
                "refresh-test-" + UUID.randomUUID() + "@example.com",
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

        var nameField = _get(TextField.class, spec -> spec.withLabel("Name"));
        var roleSelect = _get(Select.class, spec -> spec.withLabel("Role"));
        var statusSelect = _get(Select.class, spec -> spec.withLabel("Status"));

        nameField.setValue("Updated Name");
        roleSelect.setValue(Role.SYSTEM_ADMIN);
        statusSelect.setValue(UserStatus.ACTIVE);

        var saveButton = _get(Button.class, spec -> spec.withText("Save"));
        _click(saveButton);

        // Assert - grid should display updated values
        var gridItems = grid.getListDataView().getItems().toList();
        var updatedUserInGrid = gridItems.stream()
                .filter(u -> u.getId().equals(user.getId()))
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
        var user = new User(
                "error-test-" + UUID.randomUUID() + "@example.com",
                "Original Name",
                UserStatus.PENDING,
                Role.USER
        );
        userRepository.save(user);

        // Act - navigate, open dialog, delete user (simulate concurrent deletion), and try to save
        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.openEditDialog(user);

        var nameField = _get(TextField.class, spec -> spec.withLabel("Name"));
        nameField.setValue("Updated Name");

        // Delete the user while dialog is open (simulates concurrent deletion or database error)
        userRepository.deleteById(user.getId());

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
        var user = new User(
                "notification-test-" + UUID.randomUUID() + "@example.com",
                "Original Name",
                UserStatus.PENDING,
                Role.USER
        );
        userRepository.save(user);

        // Act - navigate, open dialog, change values, and save
        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.openEditDialog(user);

        var nameField = _get(TextField.class, spec -> spec.withLabel("Name"));
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
        var currentUser = new User(
                "admin@example.com",
                "Admin User",
                UserStatus.ACTIVE,
                Role.SYSTEM_ADMIN
        );
        userRepository.save(currentUser);

        // Manually set up authentication to ensure it's present
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"));
        var userDetails = org.springframework.security.core.userdetails.User.builder()
                .username("admin@example.com")
                .password("password")
                .authorities(authorities)
                .build();
        var authentication = new UsernamePasswordAuthenticationToken(
            userDetails, null, authorities
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        propagateSecurityContext(authentication);

        // Act - navigate and open edit dialog for current user
        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.openEditDialog(currentUser);

        // Assert - role and status fields should be disabled
        var nameField = _get(TextField.class, spec -> spec.withLabel("Name"));
        var roleSelect = _get(Select.class, spec -> spec.withLabel("Role"));
        var statusSelect = _get(Select.class, spec -> spec.withLabel("Status"));

        assertThat(nameField.isEnabled()).isTrue(); // Name can be edited
        assertThat(roleSelect.isEnabled()).isFalse(); // Role cannot be changed
        assertThat(statusSelect.isEnabled()).isFalse(); // Status cannot be changed
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "SYSTEM_ADMIN")
    @DirtiesContext
    void shouldDeactivateUserWhenStatusIsNotInactive() {
        // Arrange - create a test user
        var user = new User(
                "delete-test-" + UUID.randomUUID() + "@example.com",
                "Test User",
                UserStatus.ACTIVE,
                Role.USER
        );
        userRepository.save(user);

        // Act - navigate and trigger remove
        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.removeUser(user);

        // Assert - user should be deactivated (status changed to INACTIVE)
        var deactivatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(deactivatedUser.getStatus()).isEqualTo(UserStatus.INACTIVE);
        assertThat(userRepository.findById(user.getId())).isPresent(); // Still in database
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "SYSTEM_ADMIN")
    @DirtiesContext
    void shouldShowConfirmationDialogBeforeHardDelete() {
        // Arrange - create an INACTIVE user
        var user = new User(
                "hard-delete-test-" + UUID.randomUUID() + "@example.com",
                "Test User",
                UserStatus.INACTIVE,
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
        // Arrange - create an INACTIVE user
        var user = new User(
                "confirm-delete-test-" + UUID.randomUUID() + "@example.com",
                "Test User",
                UserStatus.INACTIVE,
                Role.USER
        );
        userRepository.save(user);

        // Act - navigate, trigger delete, and confirm
        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.handleDeleteClick(user);

        // Click the confirm button
        var confirmButton = _get(Button.class, spec -> spec.withText("Confirm"));
        _click(confirmButton);

        // Assert - user should be hard deleted (removed from database)
        assertThat(userRepository.findById(user.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "SYSTEM_ADMIN")
    @DirtiesContext
    void shouldSendMagicLinkWhenButtonClicked() {
        // Arrange - create a test user
        var user = new User(
                "magic-link-test-" + UUID.randomUUID() + "@example.com",
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

        // Assert - form fields should be present (email/name empty, role defaults to USER, no status field)
        var emailField = _get(EmailField.class, spec -> spec.withLabel("Email"));
        var nameField = _get(TextField.class, spec -> spec.withLabel("Name"));
        var roleSelect = _get(Select.class, spec -> spec.withLabel("Role"));

        assertThat(emailField.getValue()).isEmpty();
        assertThat(nameField.getValue()).isEmpty();
        assertThat(roleSelect.getValue()).isEqualTo(Role.USER);
        assertThat(_find(Select.class, spec -> spec.withLabel("Status"))).isEmpty();
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DirtiesContext
    void shouldCreateUserWhenSaveButtonClicked() {
        // Act - navigate, open dialog, fill form, and save
        UI.getCurrent().navigate("users");
        var createButton = _get(Button.class, spec -> spec.withText("Create User"));
        _click(createButton);

        var emailField = _get(EmailField.class, spec -> spec.withLabel("Email"));
        var nameField = _get(TextField.class, spec -> spec.withLabel("Name"));
        var roleSelect = _get(Select.class, spec -> spec.withLabel("Role"));

        emailField.setValue("newuser@example.com");
        nameField.setValue("New User");
        roleSelect.setValue(Role.USER);

        var saveButton = _get(Button.class, spec -> spec.withText("Save"));
        _click(saveButton);

        // Assert - user should be created in database with PENDING status
        var createdUser = userRepository.findByEmail("newuser@example.com");
        assertThat(createdUser).isPresent();
        assertThat(createdUser.get().getName()).isEqualTo("New User");
        assertThat(createdUser.get().getRole()).isEqualTo(Role.USER);
        assertThat(createdUser.get().getStatus()).isEqualTo(UserStatus.PENDING);

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

        var emailField = _get(EmailField.class, spec -> spec.withLabel("Email"));
        var nameField = _get(TextField.class, spec -> spec.withLabel("Name"));
        var roleSelect = _get(Select.class, spec -> spec.withLabel("Role"));

        // Leave email empty
        nameField.setValue("Test User");
        roleSelect.setValue(Role.USER);

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

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DirtiesContext
    void shouldNotCreateUserWhenEmailAlreadyExists() {
        // Arrange - create an existing user with a specific email
        var existingUser = new User(
                "existing@example.com",
                "Existing User",
                UserStatus.ACTIVE,
                Role.USER
        );
        userRepository.save(existingUser);

        // Act - navigate, open dialog, try to create user with same email
        UI.getCurrent().navigate("users");
        var createButton = _get(Button.class, spec -> spec.withText("Create User"));
        _click(createButton);

        var emailField = _get(EmailField.class, spec -> spec.withLabel("Email"));
        var nameField = _get(TextField.class, spec -> spec.withLabel("Name"));
        var roleSelect = _get(Select.class, spec -> spec.withLabel("Role"));

        emailField.setValue("existing@example.com");
        nameField.setValue("New User");
        roleSelect.setValue(Role.USER);

        var saveButton = _get(Button.class, spec -> spec.withText("Save"));
        _click(saveButton);

        // Assert - user should NOT be created (still only 1 user)
        assertThat(userRepository.count()).isEqualTo(1);

        // Dialog should still be open (save failed due to validation)
        var dialog = _get(Dialog.class);
        assertThat(dialog.isOpened()).isTrue();

        // Email field should show validation error
        assertThat(emailField.isInvalid()).isTrue();
        assertThat(emailField.getErrorMessage()).contains("already exists");
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DirtiesContext
    void shouldNotCreateUserWhenEmailFormatIsInvalid() {
        // Arrange - count users before
        long userCountBefore = userRepository.count();

        // Act - navigate, open dialog, enter invalid email format
        UI.getCurrent().navigate("users");
        var createButton = _get(Button.class, spec -> spec.withText("Create User"));
        _click(createButton);

        var emailField = _get(EmailField.class, spec -> spec.withLabel("Email"));
        var nameField = _get(TextField.class, spec -> spec.withLabel("Name"));
        var roleSelect = _get(Select.class, spec -> spec.withLabel("Role"));

        emailField.setValue("notanemail");
        nameField.setValue("Test User");
        roleSelect.setValue(Role.USER);

        var saveButton = _get(Button.class, spec -> spec.withText("Save"));
        _click(saveButton);

        // Assert - user should NOT be created
        assertThat(userRepository.count()).isEqualTo(userCountBefore);

        // Dialog should still be open (save failed due to validation)
        var dialog = _get(Dialog.class);
        assertThat(dialog.isOpened()).isTrue();

        // Email field should show validation error
        assertThat(emailField.isInvalid()).isTrue();
        assertThat(emailField.getErrorMessage()).contains("valid email");
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DirtiesContext
    void shouldNotCreateUserWhenNameFieldIsEmpty() {
        // Arrange - count users before
        long userCountBefore = userRepository.count();

        // Act - navigate, open dialog, leave name empty, fill other fields, and try to save
        UI.getCurrent().navigate("users");
        var createButton = _get(Button.class, spec -> spec.withText("Create User"));
        _click(createButton);

        var emailField = _get(EmailField.class, spec -> spec.withLabel("Email"));
        var nameField = _get(TextField.class, spec -> spec.withLabel("Name"));
        var roleSelect = _get(Select.class, spec -> spec.withLabel("Role"));

        emailField.setValue("validuser@example.com");
        // Leave name empty
        roleSelect.setValue(Role.USER);

        var saveButton = _get(Button.class, spec -> spec.withText("Save"));
        _click(saveButton);

        // Assert - user should NOT be created
        assertThat(userRepository.count()).isEqualTo(userCountBefore);

        // Dialog should still be open (save failed due to validation)
        var dialog = _get(Dialog.class);
        assertThat(dialog.isOpened()).isTrue();

        // Name field should show validation error
        assertThat(nameField.isInvalid()).isTrue();
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DirtiesContext
    void shouldNotCreateUserWhenRoleIsNotSelected() {
        // Arrange - count users before
        long userCountBefore = userRepository.count();

        // Act - navigate, open dialog, don't select a role
        UI.getCurrent().navigate("users");
        var createButton = _get(Button.class, spec -> spec.withText("Create User"));
        _click(createButton);

        var emailField = _get(EmailField.class, spec -> spec.withLabel("Email"));
        var nameField = _get(TextField.class, spec -> spec.withLabel("Name"));
        var roleSelect = _get(Select.class, spec -> spec.withLabel("Role"));

        emailField.setValue("validuser@example.com");
        nameField.setValue("Valid User");
        // Clear the default role to test validation
        roleSelect.clear();

        var saveButton = _get(Button.class, spec -> spec.withText("Save"));
        _click(saveButton);

        // Assert - user should NOT be created
        assertThat(userRepository.count()).isEqualTo(userCountBefore);

        // Dialog should still be open (save failed due to validation)
        var dialog = _get(Dialog.class);
        assertThat(dialog.isOpened()).isTrue();

        // Role select should show validation error
        assertThat(roleSelect.isInvalid()).isTrue();
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void shouldNotShowStatusFieldInCreateDialog() {
        // Act - navigate and open create dialog
        UI.getCurrent().navigate("users");
        var createButton = _get(Button.class, spec -> spec.withText("Create User"));
        _click(createButton);

        // Assert - status field should not be present (always PENDING on create)
        assertThat(_find(Select.class, spec -> spec.withLabel("Status"))).isEmpty();
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void shouldDefaultRoleToUserInCreateDialog() {
        // Act - navigate and open create dialog
        UI.getCurrent().navigate("users");
        var createButton = _get(Button.class, spec -> spec.withText("Create User"));
        _click(createButton);

        // Assert - role field should default to USER
        var roleSelect = _get(Select.class, spec -> spec.withLabel("Role"));
        assertThat(roleSelect.getValue()).isEqualTo(Role.USER);
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void shouldUseEmailFieldForEmailInputInCreateDialog() {
        UI.getCurrent().navigate("users");
        _click(_get(Button.class, spec -> spec.withText("Create User")));

        assertThat(_find(EmailField.class, spec -> spec.withLabel("Email"))).isNotEmpty();
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "SYSTEM_ADMIN")
    @DirtiesContext
    void shouldShowDeactivatedMessageWhenDeactivatingUser() {
        var user = new User("deactivate-msg-" + UUID.randomUUID() + "@example.com", "Test User", UserStatus.ACTIVE, Role.USER);
        userRepository.save(user);

        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.removeUser(user);

        assertThat(_get(Notification.class).getElement().getProperty("text"))
                .isEqualTo("User deactivated successfully");
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "SYSTEM_ADMIN")
    @DirtiesContext
    void shouldShowSuccessVariantOnMagicLinkNotification() {
        var user = new User("magic-variant-" + UUID.randomUUID() + "@example.com", "Test User", UserStatus.ACTIVE, Role.USER);
        userRepository.save(user);

        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.sendMagicLink(user);

        assertThat(_get(Notification.class).getThemeNames()).contains("success");
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DirtiesContext
    void shouldShowSuccessVariantOnCreateNotification() {
        UI.getCurrent().navigate("users");
        _click(_get(Button.class, spec -> spec.withText("Create User")));

        _get(EmailField.class, spec -> spec.withLabel("Email")).setValue("new-variant@example.com");
        _get(TextField.class, spec -> spec.withLabel("Name")).setValue("New User");
        _click(_get(Button.class, spec -> spec.withText("Save")));

        assertThat(_get(Notification.class).getThemeNames()).contains("success");
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "SYSTEM_ADMIN")
    @DirtiesContext
    void shouldShowSuccessVariantOnDeactivateNotification() {
        var user = new User("deactivate-variant-" + UUID.randomUUID() + "@example.com", "Test User", UserStatus.ACTIVE, Role.USER);
        userRepository.save(user);

        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.removeUser(user);

        assertThat(_get(Notification.class).getThemeNames()).contains("success");
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DirtiesContext
    void shouldShowSuccessVariantOnSaveNotification() {
        var user = new User("notify-test-" + UUID.randomUUID() + "@example.com", "Test User", UserStatus.PENDING, Role.USER);
        userRepository.save(user);

        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.openEditDialog(user);
        _get(TextField.class, spec -> spec.withLabel("Name")).setValue("Updated Name");
        _click(_get(Button.class, spec -> spec.withText("Save")));

        assertThat(_get(Notification.class).getThemeNames()).contains("success");
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "SYSTEM_ADMIN")
    @DirtiesContext
    void shouldSendPasswordResetLinkWhenButtonClicked() {
        var user = new User("resetpw-" + UUID.randomUUID() + "@example.com", "Reset User", UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
        userRepository.save(user);

        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.sendPasswordResetLink(user);

        var notification = _get(Notification.class);
        assertThat(notification.getElement().getProperty("text")).contains("Password reset link");
        assertThat(notification.getThemeNames()).contains("success");
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DirtiesContext
    void shouldLogPasswordSetupLinkWhenCreatingSystemAdmin() {
        UI.getCurrent().navigate("users");
        _click(_get(Button.class, spec -> spec.withText("Create User")));

        _get(EmailField.class, spec -> spec.withLabel("Email")).setValue("newadmin@example.com");
        _get(TextField.class, spec -> spec.withLabel("Name")).setValue("New Admin");
        _get(Select.class, spec -> spec.withLabel("Role")).setValue(Role.SYSTEM_ADMIN);
        _click(_get(Button.class, spec -> spec.withText("Save")));

        // Should show both "User created" and "Password setup link" notifications
        var notifications = _find(Notification.class);
        assertThat(notifications).hasSizeGreaterThanOrEqualTo(2);
        var notificationTexts = notifications.stream()
                .map(n -> n.getElement().getProperty("text"))
                .toList();
        assertThat(notificationTexts).anyMatch(t -> t.contains("Password setup link"));

        // User should have no password
        var createdUser = userRepository.findByEmail("newadmin@example.com").orElseThrow();
        assertThat(createdUser.getPasswordHash()).isNull();
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DirtiesContext
    void shouldLogPasswordSetupLinkWhenEditingUserToSystemAdmin() {
        var user = new User("promote-test-" + UUID.randomUUID() + "@example.com", "User To Promote", UserStatus.ACTIVE, Role.USER);
        userRepository.save(user);

        UI.getCurrent().navigate("users");
        var view = _get(UserListView.class);
        view.openEditDialog(user);
        _get(Select.class, spec -> spec.withLabel("Role")).setValue(Role.SYSTEM_ADMIN);
        _click(_get(Button.class, spec -> spec.withText("Save")));

        var notifications = _find(Notification.class);
        var notificationTexts = notifications.stream()
                .map(n -> n.getElement().getProperty("text"))
                .toList();
        assertThat(notificationTexts).anyMatch(t -> t.contains("Password setup link"));
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DirtiesContext
    void shouldNotLogPasswordSetupLinkWhenCreatingRegularUser() {
        UI.getCurrent().navigate("users");
        _click(_get(Button.class, spec -> spec.withText("Create User")));

        _get(EmailField.class, spec -> spec.withLabel("Email")).setValue("regularuser@example.com");
        _get(TextField.class, spec -> spec.withLabel("Name")).setValue("Regular User");
        _get(Select.class, spec -> spec.withLabel("Role")).setValue(Role.USER);
        _click(_get(Button.class, spec -> spec.withText("Save")));

        // Should only show "User created" notification, not a password setup link
        var notifications = _find(Notification.class);
        var notificationTexts = notifications.stream()
                .map(n -> n.getElement().getProperty("text"))
                .toList();
        assertThat(notificationTexts).noneMatch(t -> t != null && t.contains("Password setup link"));
    }
}
