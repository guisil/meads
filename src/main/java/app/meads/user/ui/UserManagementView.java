package app.meads.user.ui;

import app.meads.user.api.UserDto;
import app.meads.user.api.UserManagementService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * User management view for system administrators.
 * Provides CRUD operations on users and magic link email functionality.
 */
@Slf4j
@Route("admin/users")
@PageTitle("User Management | MEADS")
public class UserManagementView extends VerticalLayout {

  private final UserManagementService userService;
  private final Grid<UserDto> grid;

  public UserManagementView(UserManagementService userService) {
    this.userService = userService;

    setSizeFull();
    setPadding(true);

    // Header
    H2 title = new H2("User Management");
    add(title);

    // Action buttons
    HorizontalLayout actionBar = createActionBar();
    add(actionBar);

    // Grid
    grid = createGrid();
    add(grid);

    // Load data
    refreshGrid();
  }

  private HorizontalLayout createActionBar() {
    Button addButton = new Button("Add User", e -> openUserDialog(null));
    addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button sendBulkButton = new Button("Send Magic Links (Selected)", e -> sendBulkMagicLinks());
    sendBulkButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

    Button refreshButton = new Button("Refresh", e -> refreshGrid());

    HorizontalLayout layout = new HorizontalLayout(addButton, sendBulkButton, refreshButton);
    layout.setAlignItems(FlexComponent.Alignment.CENTER);
    layout.setPadding(false);
    return layout;
  }

  private Grid<UserDto> createGrid() {
    Grid<UserDto> grid = new Grid<>(UserDto.class, false);
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
    grid.setSelectionMode(Grid.SelectionMode.MULTI);
    grid.setSizeFull();

    grid.addColumn(UserDto::email).setHeader("Email").setSortable(true).setAutoWidth(true);
    grid.addColumn(UserDto::displayName).setHeader("Display Name").setSortable(true).setAutoWidth(true);
    grid.addColumn(UserDto::displayCountry).setHeader("Country").setSortable(true).setAutoWidth(true);
    grid.addColumn(user -> user.isSystemAdmin() ? "Yes" : "No")
        .setHeader("System Admin")
        .setSortable(true)
        .setAutoWidth(true);
    grid.addColumn(user -> user.createdAt().toString())
        .setHeader("Created At")
        .setSortable(true)
        .setAutoWidth(true);

    grid.addComponentColumn(user -> {
      Button editButton = new Button("Edit", e -> openUserDialog(user));
      editButton.addThemeVariants(ButtonVariant.LUMO_SMALL);

      Button deleteButton = new Button("Delete", e -> confirmDelete(user));
      deleteButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);

      Button sendLinkButton = new Button("Send Magic Link", e -> sendSingleMagicLink(user));
      sendLinkButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_CONTRAST);

      return new HorizontalLayout(editButton, deleteButton, sendLinkButton);
    }).setHeader("Actions").setAutoWidth(true);

    return grid;
  }

  private void openUserDialog(UserDto user) {
    Dialog dialog = new Dialog();
    dialog.setWidth("500px");

    H3 dialogTitle = new H3(user == null ? "Add User" : "Edit User");

    // Form fields
    EmailField emailField = new EmailField("Email");
    emailField.setRequiredIndicatorVisible(true);
    emailField.setWidthFull();

    TextField displayNameField = new TextField("Display Name");
    displayNameField.setWidthFull();

    TextField displayCountryField = new TextField("Country");
    displayCountryField.setWidthFull();

    Checkbox isSystemAdminCheckbox = new Checkbox("System Administrator");

    FormLayout formLayout = new FormLayout();
    formLayout.add(emailField, displayNameField, displayCountryField, isSystemAdminCheckbox);
    formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

    // Binder
    Binder<UserFormData> binder = new Binder<>(UserFormData.class);
    binder.forField(emailField)
        .asRequired("Email is required")
        .bind(UserFormData::getEmail, UserFormData::setEmail);
    binder.bind(displayNameField, UserFormData::getDisplayName, UserFormData::setDisplayName);
    binder.bind(displayCountryField, UserFormData::getDisplayCountry, UserFormData::setDisplayCountry);
    binder.bind(isSystemAdminCheckbox, UserFormData::getIsSystemAdmin, UserFormData::setIsSystemAdmin);

    // Populate form if editing
    UserFormData formData = new UserFormData();
    if (user != null) {
      formData.setEmail(user.email());
      formData.setDisplayName(user.displayName());
      formData.setDisplayCountry(user.displayCountry());
      formData.setIsSystemAdmin(user.isSystemAdmin());
      emailField.setReadOnly(true); // Email cannot be changed
    }
    binder.readBean(formData);

    // Buttons
    Button saveButton = new Button("Save", e -> {
      try {
        binder.writeBean(formData);
        saveUser(user, formData);
        dialog.close();
        refreshGrid();
      } catch (ValidationException ex) {
        showNotification("Please fix validation errors", NotificationVariant.LUMO_ERROR);
      }
    });
    saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button cancelButton = new Button("Cancel", e -> dialog.close());

    HorizontalLayout buttons = new HorizontalLayout(saveButton, cancelButton);
    buttons.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

    VerticalLayout dialogLayout = new VerticalLayout(dialogTitle, formLayout, buttons);
    dialogLayout.setPadding(false);
    dialogLayout.setSpacing(true);

    dialog.add(dialogLayout);
    dialog.open();
  }

  private void saveUser(UserDto existingUser, UserFormData formData) {
    try {
      if (existingUser == null) {
        // Create new user
        userService.createUser(
            formData.getEmail(),
            formData.getDisplayName(),
            formData.getDisplayCountry(),
            formData.getIsSystemAdmin()
        );
        showNotification("User created successfully", NotificationVariant.LUMO_SUCCESS);
      } else {
        // Update existing user
        userService.updateUser(
            existingUser.id(),
            formData.getDisplayName(),
            formData.getDisplayCountry(),
            formData.getIsSystemAdmin()
        );
        showNotification("User updated successfully", NotificationVariant.LUMO_SUCCESS);
      }
    } catch (Exception e) {
      log.error("Error saving user", e);
      showNotification("Error: " + e.getMessage(), NotificationVariant.LUMO_ERROR);
    }
  }

  private void confirmDelete(UserDto user) {
    ConfirmDialog dialog = new ConfirmDialog();
    dialog.setHeader("Delete User");
    dialog.setText("Are you sure you want to delete user " + user.email() + "?");
    dialog.setCancelable(true);
    dialog.setConfirmText("Delete");
    dialog.setConfirmButtonTheme("error primary");

    dialog.addConfirmListener(e -> {
      try {
        userService.deleteUser(user.id());
        showNotification("User deleted successfully", NotificationVariant.LUMO_SUCCESS);
        refreshGrid();
      } catch (Exception ex) {
        log.error("Error deleting user", ex);
        showNotification("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
      }
    });

    dialog.open();
  }

  private void sendSingleMagicLink(UserDto user) {
    try {
      userService.sendMagicLink(user.id());
      showNotification("Magic link sent to " + user.email(), NotificationVariant.LUMO_SUCCESS);
    } catch (Exception e) {
      log.error("Error sending magic link", e);
      showNotification("Error: " + e.getMessage(), NotificationVariant.LUMO_ERROR);
    }
  }

  private void sendBulkMagicLinks() {
    Set<UserDto> selectedUsers = grid.getSelectedItems();
    if (selectedUsers.isEmpty()) {
      showNotification("Please select at least one user", NotificationVariant.LUMO_WARNING);
      return;
    }

    ConfirmDialog dialog = new ConfirmDialog();
    dialog.setHeader("Send Magic Links");
    dialog.setText("Send magic links to " + selectedUsers.size() + " selected user(s)?");
    dialog.setCancelable(true);
    dialog.setConfirmText("Send");
    dialog.setConfirmButtonTheme("primary");

    dialog.addConfirmListener(e -> {
      try {
        var userIds = selectedUsers.stream()
            .map(UserDto::id)
            .collect(Collectors.toList());
        userService.sendBulkMagicLinks(userIds);
        showNotification("Magic links sent to " + selectedUsers.size() + " users", NotificationVariant.LUMO_SUCCESS);
        grid.deselectAll();
      } catch (Exception ex) {
        log.error("Error sending bulk magic links", ex);
        showNotification("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
      }
    });

    dialog.open();
  }

  private void refreshGrid() {
    grid.setItems(userService.findAllUsers());
  }

  private void showNotification(String message, NotificationVariant variant) {
    Notification notification = new Notification(message, 3000);
    notification.addThemeVariants(variant);
    notification.setPosition(Notification.Position.TOP_CENTER);
    notification.open();
  }

  /**
   * Form data holder for user creation/editing.
   */
  private static class UserFormData {
    private String email;
    private String displayName;
    private String displayCountry;
    private Boolean isSystemAdmin = false;

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public String getDisplayName() {
      return displayName;
    }

    public void setDisplayName(String displayName) {
      this.displayName = displayName;
    }

    public String getDisplayCountry() {
      return displayCountry;
    }

    public void setDisplayCountry(String displayCountry) {
      this.displayCountry = displayCountry;
    }

    public Boolean getIsSystemAdmin() {
      return isSystemAdmin;
    }

    public void setIsSystemAdmin(Boolean isSystemAdmin) {
      this.isSystemAdmin = isSystemAdmin;
    }
  }
}
