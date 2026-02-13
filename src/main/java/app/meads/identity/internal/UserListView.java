package app.meads.identity.internal;

import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserService;
import app.meads.identity.UserStatus;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.context.SecurityContextHolder;

@Route("users")
@RolesAllowed("SYSTEM_ADMIN")
public class UserListView extends VerticalLayout {

    private final UserRepository userRepository;
    private final UserService userService;
    private final MagicLinkService magicLinkService;
    private final Grid<User> grid;

    public UserListView(UserRepository userRepository, UserService userService, MagicLinkService magicLinkService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.magicLinkService = magicLinkService;
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

        grid.setItems(userRepository.findAll());

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
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isEditingSelf = authentication != null && user.getEmail().equals(authentication.getName());
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
                // Reload user from database to ensure we're working with a managed entity
                User managedUser = userRepository.findById(user.getId()).orElseThrow();
                managedUser.updateDetails(
                    nameField.getValue(),
                    roleSelect.getValue(),
                    statusSelect.getValue()
                );
                userRepository.save(managedUser);
                grid.setItems(userRepository.findAll());
                Notification.show("User saved successfully");
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
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUserEmail = authentication != null ? authentication.getName() : "";

        userService.deleteUser(user.getId(), currentUserEmail);
        grid.setItems(userRepository.findAll());
        Notification.show("User deleted successfully");
    }

    public void sendMagicLink(User user) {
        magicLinkService.requestMagicLink(user.getEmail());
        Notification.show("Magic link sent successfully");
    }

    public void openCreateUserDialog() {
        Dialog dialog = new Dialog();

        TextField emailField = new TextField("Email");
        emailField.setRequired(true);

        TextField nameField = new TextField("Name");
        nameField.setRequired(true);

        Select<Role> roleSelect = new Select<>();
        roleSelect.setLabel("Role");
        roleSelect.setItems(Role.values());

        Select<UserStatus> statusSelect = new Select<>();
        statusSelect.setLabel("Status");
        statusSelect.setItems(UserStatus.values());

        Button saveButton = new Button("Save");
        Button cancelButton = new Button("Cancel");
        cancelButton.addClickListener(e -> dialog.close());

        VerticalLayout formLayout = new VerticalLayout(emailField, nameField, roleSelect, statusSelect, saveButton, cancelButton);
        dialog.add(formLayout);

        dialog.open();
    }
}
