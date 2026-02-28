package app.meads.competition.internal;

import app.meads.MainLayout;
import app.meads.competition.CompetitionService;
import app.meads.competition.Event;
import app.meads.identity.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.util.UUID;

@Route(value = "events", layout = MainLayout.class)
@RolesAllowed("SYSTEM_ADMIN")
public class EventListView extends VerticalLayout {

    private final CompetitionService competitionService;
    private final UserService userService;
    private final transient AuthenticationContext authenticationContext;
    private final Grid<Event> grid;

    public EventListView(CompetitionService competitionService,
                          UserService userService,
                          AuthenticationContext authenticationContext) {
        this.competitionService = competitionService;
        this.userService = userService;
        this.authenticationContext = authenticationContext;

        var header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        header.addClassName("page-header");

        header.add(new H2("Events"));

        var createButton = new Button("Create Event", e -> openCreateDialog());
        header.add(createButton);

        add(header);

        grid = new Grid<>(Event.class, false);
        grid.addColumn(Event::getName).setHeader("Name").setSortable(true);
        grid.addColumn(Event::getStartDate).setHeader("Start Date").setSortable(true);
        grid.addColumn(Event::getEndDate).setHeader("End Date").setSortable(true);
        grid.addColumn(event -> event.getLocation() != null ? event.getLocation() : "—")
                .setHeader("Location");
        grid.addComponentColumn(event -> {
            var editButton = new Button("Edit", e -> openEditDialog(event));
            var deleteButton = new Button("Delete", e -> openDeleteDialog(event));
            return new HorizontalLayout(editButton, deleteButton);
        }).setHeader("Actions");

        grid.addItemClickListener(e ->
                e.getSource().getUI().ifPresent(ui ->
                        ui.navigate("events/" + e.getItem().getId() + "/competitions")));

        refreshGrid();
        add(grid);
    }

    private void openCreateDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Create Event");

        var nameField = new TextField("Name");
        nameField.setRequired(true);

        var startDatePicker = new DatePicker("Start Date");
        startDatePicker.setRequired(true);

        var endDatePicker = new DatePicker("End Date");
        endDatePicker.setRequired(true);

        var locationField = new TextField("Location");

        var createButton = new Button("Create", e -> {
            if (nameField.getValue().isBlank()) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Name is required");
                return;
            }
            if (startDatePicker.getValue() == null) {
                startDatePicker.setInvalid(true);
                startDatePicker.setErrorMessage("Start date is required");
                return;
            }
            if (endDatePicker.getValue() == null) {
                endDatePicker.setInvalid(true);
                endDatePicker.setErrorMessage("End date is required");
                return;
            }

            try {
                competitionService.createEvent(
                        nameField.getValue(),
                        startDatePicker.getValue(),
                        endDatePicker.getValue(),
                        locationField.getValue().isBlank() ? null : locationField.getValue(),
                        getCurrentUserId());
                refreshGrid();
                var notification = Notification.show("Event created successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());

        var form = new VerticalLayout(nameField, startDatePicker, endDatePicker, locationField);
        form.setPadding(false);
        dialog.add(form);
        dialog.getFooter().add(cancelButton, createButton);
        dialog.open();
    }

    public void openEditDialog(Event event) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Edit Event");

        var nameField = new TextField("Name");
        nameField.setValue(event.getName());
        nameField.setRequired(true);

        var startDatePicker = new DatePicker("Start Date");
        startDatePicker.setValue(event.getStartDate());
        startDatePicker.setRequired(true);

        var endDatePicker = new DatePicker("End Date");
        endDatePicker.setValue(event.getEndDate());
        endDatePicker.setRequired(true);

        var locationField = new TextField("Location");
        locationField.setValue(event.getLocation() != null ? event.getLocation() : "");

        var saveButton = new Button("Save", e -> {
            if (nameField.getValue().isBlank()) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Name is required");
                return;
            }
            if (startDatePicker.getValue() == null || endDatePicker.getValue() == null) {
                return;
            }

            try {
                competitionService.updateEvent(
                        event.getId(),
                        nameField.getValue(),
                        startDatePicker.getValue(),
                        endDatePicker.getValue(),
                        locationField.getValue().isBlank() ? null : locationField.getValue(),
                        getCurrentUserId());
                refreshGrid();
                var notification = Notification.show("Event updated successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());

        var form = new VerticalLayout(nameField, startDatePicker, endDatePicker, locationField);
        form.setPadding(false);
        dialog.add(form);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void openDeleteDialog(Event event) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Delete Event");
        dialog.add("Are you sure you want to delete \"" + event.getName() + "\"?");

        var confirmButton = new Button("Delete", e -> {
            try {
                competitionService.deleteEvent(event.getId(), getCurrentUserId());
                refreshGrid();
                var notification = Notification.show("Event deleted successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
                dialog.close();
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    private void refreshGrid() {
        grid.setItems(competitionService.findAllEvents());
    }

    private UUID getCurrentUserId() {
        var email = authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElseThrow();
        return userService.findByEmail(email).getId();
    }
}
