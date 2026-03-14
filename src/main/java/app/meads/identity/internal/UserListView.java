package app.meads.identity.internal;

import app.meads.MainLayout;
import app.meads.identity.EmailService;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserService;
import app.meads.identity.UserStatus;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import java.util.Arrays;
import java.util.Locale;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;

@Slf4j
@Route(value = "users", layout = MainLayout.class)
@PermitAll
public class UserListView extends VerticalLayout implements BeforeEnterObserver {

    private final UserService userService;
    private final EmailService emailService;
    private final transient AuthenticationContext authenticationContext;
    private final Grid<User> grid;

    public UserListView(UserService userService, EmailService emailService, AuthenticationContext authenticationContext) {
        this.userService = userService;
        this.emailService = emailService;
        this.authenticationContext = authenticationContext;
        add(new H1("Users"));

        TextField filterField = new TextField();
        filterField.setPlaceholder("Filter by email or name...");
        filterField.setValueChangeMode(ValueChangeMode.EAGER);
        filterField.setWidthFull();
        filterField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        filterField.setClearButtonVisible(true);

        Button createUserButton = new Button("Create User");
        createUserButton.addClickListener(e -> openCreateUserDialog());

        var toolbar = new HorizontalLayout(filterField, createUserButton);
        toolbar.setWidthFull();
        toolbar.setFlexGrow(1, filterField);
        add(toolbar);

        grid = new Grid<>(User.class, false);
        grid.setAllRowsVisible(true);
        grid.addColumn(User::getName).setHeader("Name").setSortable(true).setFlexGrow(2);
        grid.addColumn(User::getEmail).setHeader("Email").setSortable(true).setFlexGrow(3);
        grid.addColumn(user -> user.getMeaderyName() != null ? user.getMeaderyName() : "—")
                .setHeader("Meadery").setSortable(true).setFlexGrow(2);
        grid.addColumn(user -> {
            if (user.getCountry() == null) return "—";
            return new Locale("", user.getCountry()).getDisplayCountry(Locale.ENGLISH);
        }).setHeader("Country").setSortable(true).setAutoWidth(true);
        grid.addColumn(User::getRole).setHeader("Role").setSortable(true).setAutoWidth(true);
        grid.addColumn(User::getStatus).setHeader("Status").setSortable(true).setAutoWidth(true);
        grid.addComponentColumn(user -> {
            Button editButton = new Button(new Icon(VaadinIcon.EDIT));
            editButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            editButton.setAriaLabel("Edit");
            editButton.setTooltipText("Edit");
            editButton.addClickListener(e -> openEditDialog(user));

            boolean isInactive = user.getStatus() == UserStatus.INACTIVE;
            Button deleteButton = new Button(new Icon(isInactive ? VaadinIcon.TRASH : VaadinIcon.BAN));
            deleteButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            deleteButton.setAriaLabel(isInactive ? "Delete" : "Deactivate");
            deleteButton.setTooltipText(isInactive ? "Delete" : "Deactivate");
            deleteButton.addClickListener(e -> handleDeleteClick(user));

            HorizontalLayout actions = new HorizontalLayout(editButton, deleteButton);

            if (user.getPasswordHash() == null) {
                Button loginLinkButton = new Button(new Icon(VaadinIcon.ENVELOPE));
                loginLinkButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
                loginLinkButton.setAriaLabel("Send Login Link");
                loginLinkButton.setTooltipText("Send Login Link");
                loginLinkButton.addClickListener(e -> sendMagicLink(user));
                actions.add(loginLinkButton);
            }

            Button passwordResetButton = new Button(new Icon(VaadinIcon.KEY));
            passwordResetButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            passwordResetButton.setAriaLabel("Password Reset");
            passwordResetButton.setTooltipText("Password Reset");
            passwordResetButton.addClickListener(e -> sendPasswordResetLink(user));
            actions.add(passwordResetButton);
            return actions;
        }).setHeader("Actions").setAutoWidth(true);

        grid.getColumns().forEach(col -> col.setResizable(true));

        var dataView = grid.setItems(userService.findAll());
        filterField.addValueChangeListener(e -> {
            var filterString = e.getValue().toLowerCase();
            if (filterString.isBlank()) {
                dataView.removeFilters();
            } else {
                dataView.setFilter(user ->
                        user.getEmail().toLowerCase().contains(filterString) ||
                        user.getName().toLowerCase().contains(filterString));
            }
        });

        add(grid);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var isAdmin = authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(user -> user.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_SYSTEM_ADMIN")))
                .orElse(false);
        if (!isAdmin) {
            event.forwardTo("");
        }
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

        dialog.add(content);
        dialog.getFooter().add(cancelButton, confirmButton);
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
        emailService.sendMagicLink(user.getEmail());
        var notification = Notification.show("Login link sent successfully");
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    public void sendPasswordResetLink(User user) {
        emailService.sendPasswordReset(user.getEmail());
        var notification = Notification.show("Password reset link sent successfully");
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    public void openCreateUserDialog() {
        openUserDialog(null);
    }

    private void openUserDialog(User existingUser) {
        Dialog dialog = new Dialog();
        boolean isCreate = existingUser == null;

        EmailField emailField = new EmailField("Email");
        emailField.setMaxLength(255);
        if (isCreate) {
            emailField.setRequired(true);
        } else {
            emailField.setValue(existingUser.getEmail());
            emailField.setReadOnly(true);
        }

        TextField nameField = new TextField("Name");
        nameField.setRequired(true);
        nameField.setMaxLength(255);

        Select<Role> roleSelect = new Select<>();
        roleSelect.setLabel("Role");
        roleSelect.setItems(Role.values());

        var meaderyField = new TextField("Meadery Name");
        meaderyField.setWidthFull();
        meaderyField.setMaxLength(255);
        if (!isCreate && existingUser.getMeaderyName() != null) {
            meaderyField.setValue(existingUser.getMeaderyName());
        }

        var countryCombo = new ComboBox<String>("Country");
        var countries = Arrays.stream(Locale.getISOCountries())
                .sorted((a, b) -> new Locale("", a).getDisplayCountry(Locale.ENGLISH)
                        .compareTo(new Locale("", b).getDisplayCountry(Locale.ENGLISH)))
                .toList();
        countryCombo.setItems(countries);
        countryCombo.setItemLabelGenerator(code ->
                new Locale("", code).getDisplayCountry(Locale.ENGLISH));
        countryCombo.setClearButtonVisible(true);
        countryCombo.setWidthFull();
        if (!isCreate && existingUser.getCountry() != null) {
            countryCombo.setValue(existingUser.getCountry());
        }

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
            if (isCreate && !StringUtils.hasText(emailField.getValue())) {
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
                    var meadery = meaderyField.getValue();
                    userService.updateProfile(savedUser.getId(), savedUser.getName(),
                            meadery != null && !meadery.isBlank() ? meadery.trim() : null,
                            countryCombo.getValue());
                } else {
                    savedUser = userService.updateUser(
                        existingUser.getId(),
                        nameField.getValue(),
                        roleSelect.getValue(),
                        statusSelectRef.getValue(),
                        getCurrentUserEmail()
                    );
                    var meadery = meaderyField.getValue();
                    userService.updateProfile(existingUser.getId(), nameField.getValue(),
                            meadery != null && !meadery.isBlank() ? meadery.trim() : null,
                            countryCombo.getValue());
                }
                grid.setItems(userService.findAll());
                var notification = Notification.show(isCreate ? "User created successfully" : "User saved successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                generatePasswordSetupLinkIfNeeded(savedUser);
                dialog.close();
            } catch (IllegalArgumentException ex) {
                if (isCreate) {
                    emailField.setInvalid(true);
                    emailField.setErrorMessage(ex.getMessage());
                } else {
                    nameField.setInvalid(true);
                    nameField.setErrorMessage("Failed to save user. Please try again.");
                }
            } catch (ConstraintViolationException ex) {
                if (isCreate) {
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
        formLayout.add(emailField, nameField, meaderyField, countryCombo, roleSelect);
        if (statusSelect != null) {
            formLayout.add(statusSelect);
        }
        var buttonRow = new HorizontalLayout(cancelButton, saveButton);
        formLayout.add(buttonRow);
        dialog.add(formLayout);

        dialog.open();
    }

    private void generatePasswordSetupLinkIfNeeded(User user) {
        if (user.getRole() == Role.SYSTEM_ADMIN && !userService.hasPassword(user.getId())) {
            emailService.sendPasswordReset(user.getEmail());
            Notification.show("Password setup link sent successfully");
        }
    }

    private String getCurrentUserEmail() {
        return authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElse("");
    }
}
