package app.meads.identity.internal;

import app.meads.MainLayout;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserService;
import app.meads.identity.UserStatus;
import jakarta.validation.ConstraintViolationException;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.userdetails.UserDetails;

@Route(value = "users", layout = MainLayout.class)
@RolesAllowed("SYSTEM_ADMIN")
public class UserListView extends VerticalLayout {

    private final UserService userService;
    private final MagicLinkService magicLinkService;
    private final transient AuthenticationContext authenticationContext;
    private final Grid<User> grid;

    public UserListView(UserService userService, MagicLinkService magicLinkService, AuthenticationContext authenticationContext) {
        this.userService = userService;
        this.magicLinkService = magicLinkService;
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

            String deleteButtonText = user.getStatus() == UserStatus.DISABLED ? "Delete" : "Disable";
            Button deleteButton = new Button(deleteButtonText);
            deleteButton.addClickListener(e -> handleDeleteClick(user));

            Button magicLinkButton = new Button("Send Magic Link");
            magicLinkButton.addClickListener(e -> sendMagicLink(user));

            HorizontalLayout actions = new HorizontalLayout(editButton, deleteButton, magicLinkButton);
            actions.setSpacing(true);
            return actions;
        }).setHeader("Actions");

        grid.setItems(userService.findAll());

        add(grid);
    }

    public void openEditDialog(User user) {
        Dialog dialog = new Dialog();

        TextField nameField = new TextField("Name");
        nameField.setValue(user.getName());
        nameField.setRequired(true);

        Select<Role> roleSelect = new Select<>();
        roleSelect.setLabel("Role");
        roleSelect.setItems(Role.values());
        roleSelect.setValue(user.getRole());

        Select<UserStatus> statusSelect = new Select<>();
        statusSelect.setLabel("Status");
        statusSelect.setItems(UserStatus.values());
        statusSelect.setValue(user.getStatus());

        // Prevent users from editing their own role or status
        String currentUserEmail = authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElse("");
        boolean isEditingSelf = userService.isEditingSelf(user.getId(), currentUserEmail);
        if (isEditingSelf) {
            roleSelect.setEnabled(false);
            statusSelect.setEnabled(false);
        }

        Button saveButton = new Button("Save");
        saveButton.addClickListener(e -> {
            // Validate name field is not empty
            if (nameField.getValue().isBlank()) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Name is required");
                return;
            }

            try {
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
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.addClickListener(e -> dialog.close());

        VerticalLayout formLayout = new VerticalLayout(nameField, roleSelect, statusSelect, saveButton, cancelButton);
        dialog.add(formLayout);

        dialog.open();
    }

    public void handleDeleteClick(User user) {
        if (user.getStatus() == UserStatus.DISABLED) {
            // Hard delete - show confirmation dialog
            showDeleteConfirmationDialog(user);
        } else {
            // Soft delete - no confirmation needed
            try {
                deleteUser(user);
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
                deleteUser(user);
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

    public void deleteUser(User user) {
        String currentUserEmail = authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElse("");

        boolean isSoftDelete = user.getStatus() != UserStatus.DISABLED;
        userService.deleteUser(user.getId(), currentUserEmail);
        grid.setItems(userService.findAll());
        var notification = Notification.show(isSoftDelete ? "User disabled successfully" : "User deleted successfully");
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    public void sendMagicLink(User user) {
        magicLinkService.requestMagicLink(user.getEmail());
        var notification = Notification.show("Magic link sent successfully");
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    public void openCreateUserDialog() {
        Dialog dialog = new Dialog();

        EmailField emailField = new EmailField("Email");
        emailField.setRequired(true);

        TextField nameField = new TextField("Name");
        nameField.setRequired(true);

        Select<Role> roleSelect = new Select<>();
        roleSelect.setLabel("Role");
        roleSelect.setItems(Role.values());
        roleSelect.setValue(Role.USER);

        Select<UserStatus> statusSelect = new Select<>();
        statusSelect.setLabel("Status");
        statusSelect.setItems(UserStatus.values());
        statusSelect.setValue(UserStatus.PENDING);

        Button saveButton = new Button("Save");
        saveButton.addClickListener(e -> {
            // Validate email field is not empty
            if (emailField.getValue().isBlank()) {
                emailField.setInvalid(true);
                emailField.setErrorMessage("Email is required");
                return;
            }

            // Validate name field is not empty
            if (nameField.getValue().isBlank()) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Name is required");
                return;
            }

            // Validate role is selected
            if (roleSelect.isEmpty()) {
                roleSelect.setInvalid(true);
                roleSelect.setErrorMessage("Role is required");
                return;
            }

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
            } catch (ConstraintViolationException ex) {
                emailField.setInvalid(true);
                emailField.setErrorMessage("Please enter a valid email address");
            } catch (Exception ex) {
                Notification.show("Failed to save user. Please try again.");
            }
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.addClickListener(e -> dialog.close());

        VerticalLayout formLayout = new VerticalLayout(emailField, nameField, roleSelect, statusSelect, saveButton, cancelButton);
        dialog.add(formLayout);

        dialog.open();
    }
}
