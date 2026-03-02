package app.meads.competition.internal;

import app.meads.MainLayout;
import app.meads.competition.CompetitionService;
import app.meads.competition.MeadEvent;
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
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Route(value = "events", layout = MainLayout.class)
@RolesAllowed("SYSTEM_ADMIN")
public class MeadEventListView extends VerticalLayout {

    private final CompetitionService competitionService;
    private final UserService userService;
    private final transient AuthenticationContext authenticationContext;
    private final Grid<MeadEvent> grid;

    public MeadEventListView(CompetitionService competitionService,
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

        var createButton = new Button("Create Event", e -> openMeadEventDialog(null));
        header.add(createButton);

        add(header);

        grid = new Grid<>(MeadEvent.class, false);
        grid.addColumn(MeadEvent::getName).setHeader("Name").setSortable(true);
        grid.addColumn(MeadEvent::getStartDate).setHeader("Start Date").setSortable(true);
        grid.addColumn(MeadEvent::getEndDate).setHeader("End Date").setSortable(true);
        grid.addColumn(meadEvent -> meadEvent.getLocation() != null ? meadEvent.getLocation() : "—")
                .setHeader("Location");
        grid.addComponentColumn(meadEvent -> {
            var editButton = new Button("Edit", e -> openMeadEventDialog(meadEvent));
            var deleteButton = new Button("Delete", e -> openDeleteMeadEventDialog(meadEvent));
            return new HorizontalLayout(editButton, deleteButton);
        }).setHeader("Actions");

        grid.addItemClickListener(e ->
                e.getSource().getUI().ifPresent(ui ->
                        ui.navigate("events/" + e.getItem().getId() + "/competitions")));

        refreshGrid();
        add(grid);
    }

    private void openMeadEventDialog(MeadEvent existing) {
        boolean isEdit = existing != null;
        var dialog = new Dialog();
        dialog.setHeaderTitle(isEdit ? "Edit Event" : "Create Event");

        var nameField = new TextField("Name");
        nameField.setRequired(true);

        var startDatePicker = new DatePicker("Start Date");
        startDatePicker.setRequired(true);

        var endDatePicker = new DatePicker("End Date");
        endDatePicker.setRequired(true);

        var locationField = new TextField("Location");

        var logoData = new byte[1][];
        var logoContentType = new String[1];

        var uploadHandler = UploadHandler.inMemory((metadata, data) -> {
            logoData[0] = data;
            logoContentType[0] = metadata.contentType();
        });
        var upload = new Upload(uploadHandler);
        upload.setMaxFiles(1);
        upload.setMaxFileSize(512 * 1024);
        upload.setAcceptedFileTypes("image/png", "image/jpeg");

        var logoSection = new HorizontalLayout();
        logoSection.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        logoSection.add(upload);

        if (isEdit) {
            nameField.setValue(existing.getName());
            startDatePicker.setValue(existing.getStartDate());
            endDatePicker.setValue(existing.getEndDate());
            locationField.setValue(existing.getLocation() != null ? existing.getLocation() : "");

            if (existing.hasLogo()) {
                var removeLogoButton = new Button("Remove Logo", e -> {
                    try {
                        competitionService.updateMeadEventLogo(
                                existing.getId(), null, null, getCurrentUserId());
                        logoSection.remove(e.getSource());
                        var notification = Notification.show("Logo removed");
                        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    } catch (IllegalArgumentException ex) {
                        Notification.show(ex.getMessage());
                    }
                });
                logoSection.add(removeLogoButton);
            }
        }

        var submitButton = new Button(isEdit ? "Save" : "Create", e -> {
            if (!StringUtils.hasText(nameField.getValue())) {
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
                var location = StringUtils.hasText(locationField.getValue())
                        ? locationField.getValue() : null;
                MeadEvent saved;
                if (isEdit) {
                    saved = competitionService.updateMeadEvent(existing.getId(),
                            nameField.getValue(), startDatePicker.getValue(),
                            endDatePicker.getValue(), location, getCurrentUserId());
                } else {
                    saved = competitionService.createMeadEvent(nameField.getValue(),
                            startDatePicker.getValue(), endDatePicker.getValue(),
                            location, getCurrentUserId());
                }
                if (logoData[0] != null) {
                    competitionService.updateMeadEventLogo(
                            saved.getId(), logoData[0], logoContentType[0],
                            getCurrentUserId());
                }
                refreshGrid();
                var notification = Notification.show(
                        isEdit ? "Event updated successfully" : "Event created successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());

        var form = new VerticalLayout(nameField, startDatePicker, endDatePicker, locationField, logoSection);
        form.setPadding(false);
        dialog.add(form);
        dialog.getFooter().add(cancelButton, submitButton);
        dialog.open();
    }

    private void openDeleteMeadEventDialog(MeadEvent meadEvent) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Delete Event");
        dialog.add("Are you sure you want to delete \"" + meadEvent.getName() + "\"?");

        var confirmButton = new Button("Delete", e -> {
            try {
                competitionService.deleteMeadEvent(meadEvent.getId(), getCurrentUserId());
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
        grid.setItems(competitionService.findAllMeadEvents());
    }

    private UUID getCurrentUserId() {
        var email = authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElseThrow();
        return userService.findByEmail(email).getId();
    }
}
