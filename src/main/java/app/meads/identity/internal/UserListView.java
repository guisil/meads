package app.meads.identity.internal;

import app.meads.MainLayout;
import app.meads.identity.JwtMagicLinkService;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserService;
import app.meads.identity.UserStatus;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import java.time.Duration;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;

@Slf4j
@Route(value = "users", layout = MainLayout.class)
@RolesAllowed("SYSTEM_ADMIN")
public class UserListView extends VerticalLayout {

    private final UserService userService;
    private final JwtMagicLinkService jwtMagicLinkService;
    private final transient AuthenticationContext authenticationContext;
    private final Grid<User> grid;

    public UserListView(UserService userService, JwtMagicLinkService jwtMagicLinkService, AuthenticationContext authenticationContext) {
        this.userService = userService;
        this.jwtMagicLinkService = jwtMagicLinkService;
        this.authenticationContext = authenticationContext;
        add(new H1("Users"));

        Button createUserButton = new Button("Create User");
        createUserButton.addClickListener(e -> openCreateUserDialog());
        add(createUserButton);

        grid = new Grid<>(User.class, false);
        grid.addColumn(User::getEmail).setHeader("Email");
        grid.addColumn(User::getName).setHeader("Name");
        grid.addColumn(User::getRole).setHeader("Role");
        grid.addColumn(User::getStatus).setHeader("Status");
        grid.addComponentColumn(user -> {
            Button editButton = new Button("Edit");
            editButton.addClickListener(e -> openEditDialog(user));

            String deleteButtonText = user.getStatus() == UserStatus.INACTIVE ? "Delete" : "Deactivate";
            Button deleteButton = new Button(deleteButtonText);
            deleteButton.addClickListener(e -> handleDeleteClick(user));

            HorizontalLayout actions = new HorizontalLayout(editButton, deleteButton);

            if (user.getPasswordHash() == null) {
                Button magicLinkButton = new Button("Send Magic Link");
                magicLinkButton.addClickListener(e -> sendMagicLink(user));
                actions.add(magicLinkButton);
            }

            Button passwordResetButton = new Button("Password Reset");
            passwordResetButton.addClickListener(e -> sendPasswordResetLink(user));
            actions.add(passwordResetButton);
            actions.setSpacing(true);
            return actions;
        }).setHeader("Actions").setAutoWidth(true);

        grid.setItems(userService.findAll());

        add(grid);
    }

    public void openEditDialog(User user) {
        openUserDialog(user);
    }

    public void handleDeleteClick(User user) {
        if (user.getStatus() == UserStatus.INACTIVE) {
            // Hard delete - show confirmation dialog
            showDeleteConfirmationDialog(user);
        } else {
            // Soft delete - no confirmation needed
            try {
                removeUser(user);
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        }
    }

    private void showDeleteConfirmationDialog(User user) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Confirm Deletion");

        VerticalLayout content = new VerticalLayout();
        content.add("Are you sure you want to permanently delete user " + user.getEmail() + "?");
        content.add("This action cannot be undone.");

        Button confirmButton = new Button("Confirm");
        confirmButton.addClickListener(e -> {
            try {
                removeUser(user);
                dialog.close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
                dialog.close();
            }
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.addClickListener(e -> dialog.close());

        HorizontalLayout buttons = new HorizontalLayout(confirmButton, cancelButton);
        content.add(buttons);

        dialog.add(content);
        dialog.open();
    }

    public void removeUser(User user) {
        String currentUserEmail = getCurrentUserEmail();

        boolean isSoftDelete = user.getStatus() != UserStatus.INACTIVE;
        userService.removeUser(user.getId(), currentUserEmail);
        grid.setItems(userService.findAll());
        var notification = Notification.show(isSoftDelete ? "User deactivated successfully" : "User deleted successfully");
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    public void sendMagicLink(User user) {
        String link = jwtMagicLinkService.generateLink(user.getEmail(), Duration.ofDays(7));
        log.info("\n\n\tMagic link for {}: {}\n", user.getEmail(), link);
        var notification = Notification.show("Magic link sent successfully");
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    public void sendPasswordResetLink(User user) {
        String link = jwtMagicLinkService.generatePasswordSetupLink(user.getEmail(), Duration.ofDays(7));
        log.info("\n\n\tPassword reset link for {}: {}\n", user.getEmail(), link);
        var notification = Notification.show("Password reset link generated (check server logs)");
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    public void openCreateUserDialog() {
        openUserDialog(null);
    }

    private void openUserDialog(User existingUser) {
        Dialog dialog = new Dialog();
        boolean isCreate = existingUser == null;

        EmailField emailField = isCreate ? new EmailField("Email") : null;
        if (emailField != null) {
            emailField.setRequired(true);
        }

        TextField nameField = new TextField("Name");
        nameField.setRequired(true);

        Select<Role> roleSelect = new Select<>();
        roleSelect.setLabel("Role");
        roleSelect.setItems(Role.values());

        Select<UserStatus> statusSelect = null;

        if (isCreate) {
            roleSelect.setValue(Role.USER);
        } else {
            statusSelect = new Select<>();
            statusSelect.setLabel("Status");
            statusSelect.setItems(UserStatus.values());

            nameField.setValue(existingUser.getName());
            roleSelect.setValue(existingUser.getRole());
            statusSelect.setValue(existingUser.getStatus());

            String currentUserEmail = getCurrentUserEmail();
            if (userService.isEditingSelf(existingUser.getId(), currentUserEmail)) {
                roleSelect.setEnabled(false);
                statusSelect.setEnabled(false);
            }
        }

        var statusSelectRef = statusSelect;
        Button saveButton = new Button("Save");
        saveButton.addClickListener(e -> {
            if (emailField != null && !StringUtils.hasText(emailField.getValue())) {
                emailField.setInvalid(true);
                emailField.setErrorMessage("Email is required");
                return;
            }
            if (!StringUtils.hasText(nameField.getValue())) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Name is required");
                return;
            }
            if (isCreate && roleSelect.isEmpty()) {
                roleSelect.setInvalid(true);
                roleSelect.setErrorMessage("Role is required");
                return;
            }

            try {
                User savedUser;
                if (isCreate) {
                    savedUser = userService.createUser(
                        emailField.getValue(),
                        nameField.getValue(),
                        UserStatus.PENDING,
                        roleSelect.getValue()
                    );
                } else {
                    savedUser = userService.updateUser(
                        existingUser.getId(),
                        nameField.getValue(),
                        roleSelect.getValue(),
                        statusSelectRef.getValue(),
                        getCurrentUserEmail()
                    );
                }
                grid.setItems(userService.findAll());
                var notification = Notification.show(isCreate ? "User created successfully" : "User saved successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                generatePasswordSetupLinkIfNeeded(savedUser);
                dialog.close();
            } catch (IllegalArgumentException ex) {
                if (emailField != null) {
                    emailField.setInvalid(true);
                    emailField.setErrorMessage(ex.getMessage());
                } else {
                    nameField.setInvalid(true);
                    nameField.setErrorMessage("Failed to save user. Please try again.");
                }
            } catch (ConstraintViolationException ex) {
                if (emailField != null) {
                    emailField.setInvalid(true);
                    emailField.setErrorMessage("Please enter a valid email address");
                }
            } catch (Exception ex) {
                Notification.show("Failed to save user. Please try again.");
            }
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.addClickListener(e -> dialog.close());

        VerticalLayout formLayout = new VerticalLayout();
        if (emailField != null) {
            formLayout.add(emailField);
        }
        formLayout.add(nameField, roleSelect);
        if (statusSelect != null) {
            formLayout.add(statusSelect);
        }
        formLayout.add(saveButton, cancelButton);
        dialog.add(formLayout);

        dialog.open();
    }

    private void generatePasswordSetupLinkIfNeeded(User user) {
        if (user.getRole() == Role.SYSTEM_ADMIN && !userService.hasPassword(user.getId())) {
            String link = jwtMagicLinkService.generatePasswordSetupLink(user.getEmail(), Duration.ofDays(7));
            log.info("\n\n\tPassword setup link for {}: {}\n", user.getEmail(), link);
            Notification.show("Password setup link generated (check server logs)");
        }
    }

    private String getCurrentUserEmail() {
        return authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElse("");
    }
}
