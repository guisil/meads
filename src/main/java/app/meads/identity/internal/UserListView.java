package app.meads.identity.internal;

import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.notification.Notification;
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
    private final Grid<User> grid;

    public UserListView(UserRepository userRepository) {
        this.userRepository = userRepository;
        add(new H1("Users"));

        grid = new Grid<>(User.class, false);
        grid.addColumn(User::getEmail).setHeader("Email");
        grid.addColumn(User::getName).setHeader("Name");
        grid.addColumn(User::getRole).setHeader("Role");
        grid.addColumn(User::getStatus).setHeader("Status");
        grid.addComponentColumn(user -> {
            Button editButton = new Button("Edit");
            editButton.addClickListener(e -> openEditDialog(user));
            return editButton;
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
}
