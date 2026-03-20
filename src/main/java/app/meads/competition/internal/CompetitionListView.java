package app.meads.competition.internal;

import app.meads.BusinessRuleException;
import app.meads.MainLayout;
import app.meads.competition.Competition;
import app.meads.competition.CompetitionService;
import app.meads.identity.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Route(value = "competitions", layout = MainLayout.class)
@PermitAll
public class CompetitionListView extends VerticalLayout implements BeforeEnterObserver {

    private final CompetitionService competitionService;
    private final UserService userService;
    private final transient AuthenticationContext authenticationContext;
    private final Grid<Competition> grid;

    public CompetitionListView(CompetitionService competitionService,
                                UserService userService,
                                AuthenticationContext authenticationContext) {
        this.competitionService = competitionService;
        this.userService = userService;
        this.authenticationContext = authenticationContext;

        add(new H2("Competitions"));

        var filterField = new TextField();
        filterField.setPlaceholder("Filter by name...");
        filterField.setValueChangeMode(ValueChangeMode.EAGER);
        filterField.setWidthFull();
        filterField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        filterField.setClearButtonVisible(true);

        var createButton = new Button("Create Competition", e -> openCompetitionDialog(null));

        var toolbar = new HorizontalLayout(filterField, createButton);
        toolbar.setWidthFull();
        toolbar.setFlexGrow(1, filterField);
        add(toolbar);

        grid = new Grid<>(Competition.class, false);
        grid.setAllRowsVisible(true);
        grid.addColumn(Competition::getName).setHeader("Name").setSortable(true).setFlexGrow(2);
        grid.addColumn(Competition::getStartDate).setHeader("Start Date").setSortable(true).setAutoWidth(true);
        grid.addColumn(Competition::getEndDate).setHeader("End Date").setSortable(true).setAutoWidth(true);
        grid.addColumn(comp -> comp.getLocation() != null ? comp.getLocation() : "—")
                .setHeader("Location").setSortable(true).setFlexGrow(1);
        grid.addComponentColumn(comp -> {
            var editButton = new Button(new Icon(VaadinIcon.EDIT));
            editButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            editButton.setAriaLabel("Edit");
            editButton.setTooltipText("Edit");
            editButton.addClickListener(e -> openCompetitionDialog(comp));

            var deleteButton = new Button(new Icon(VaadinIcon.TRASH));
            deleteButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            deleteButton.setAriaLabel("Delete");
            deleteButton.setTooltipText("Delete");
            deleteButton.addClickListener(e -> openDeleteCompetitionDialog(comp));

            return new HorizontalLayout(editButton, deleteButton);
        }).setHeader("Actions").setAutoWidth(true);

        grid.addItemClickListener(e ->
                e.getSource().getUI().ifPresent(ui ->
                        ui.navigate("competitions/" + e.getItem().getShortName())));

        var dataView = grid.setItems(competitionService.findAllCompetitions());
        filterField.addValueChangeListener(e -> {
            var filterString = e.getValue().toLowerCase();
            if (filterString.isBlank()) {
                dataView.removeFilters();
            } else {
                dataView.setFilter(comp ->
                        comp.getName().toLowerCase().contains(filterString));
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

    private void openCompetitionDialog(Competition existing) {
        boolean isEdit = existing != null;
        var dialog = new Dialog();
        dialog.setHeaderTitle(isEdit ? "Edit Competition" : "Create Competition");

        var nameField = new TextField("Name");
        nameField.setRequired(true);
        nameField.setMaxLength(255);

        var shortNameField = new TextField("Short Name");
        shortNameField.setRequired(true);
        shortNameField.setMaxLength(100);
        shortNameField.setHelperText("URL-friendly identifier (e.g. chip-2026)");
        shortNameField.setPattern("[a-z0-9][a-z0-9-]*[a-z0-9]");

        var startDatePicker = new DatePicker("Start Date");
        startDatePicker.setRequired(true);

        var endDatePicker = new DatePicker("End Date");
        endDatePicker.setRequired(true);

        var locationField = new TextField("Location");
        locationField.setMaxLength(500);

        var logoData = new byte[1][];
        var logoContentType = new String[1];

        var uploadHandler = UploadHandler.inMemory((metadata, data) -> {
            logoData[0] = data;
            logoContentType[0] = metadata.contentType();
        });
        var upload = new Upload(uploadHandler);
        upload.setMaxFiles(1);
        upload.setMaxFileSize(2560 * 1024);
        upload.setAcceptedFileTypes("image/png", "image/jpeg");
        upload.addFileRejectedListener(e ->
                Notification.show(e.getErrorMessage())
                        .addThemeVariants(NotificationVariant.LUMO_ERROR));

        var logoSection = new HorizontalLayout();
        logoSection.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        logoSection.add(upload);

        if (isEdit) {
            nameField.setValue(existing.getName());
            shortNameField.setValue(existing.getShortName());
            startDatePicker.setValue(existing.getStartDate());
            endDatePicker.setValue(existing.getEndDate());
            locationField.setValue(existing.getLocation() != null ? existing.getLocation() : "");

            if (existing.hasLogo()) {
                var removeLogoButton = new Button("Remove Logo", e -> {
                    try {
                        competitionService.updateCompetitionLogo(
                                existing.getId(), null, null, getCurrentUserId());
                        logoSection.remove(e.getSource());
                        var notification = Notification.show("Logo removed");
                        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    } catch (BusinessRuleException ex) {
                        e.getSource().setEnabled(true);
                        Notification.show(getTranslation(ex.getMessageKey(), java.util.Locale.ENGLISH, ex.getParams()));
                    }
                });
                removeLogoButton.setDisableOnClick(true);
                logoSection.add(removeLogoButton);
            }
        }

        var submitButton = new Button("Save", e -> {
            if (!StringUtils.hasText(nameField.getValue())) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Name is required");
                e.getSource().setEnabled(true);
                return;
            }
            if (!StringUtils.hasText(shortNameField.getValue())) {
                shortNameField.setInvalid(true);
                shortNameField.setErrorMessage("Short name is required");
                e.getSource().setEnabled(true);
                return;
            }
            if (startDatePicker.getValue() == null) {
                startDatePicker.setInvalid(true);
                startDatePicker.setErrorMessage("Start date is required");
                e.getSource().setEnabled(true);
                return;
            }
            if (endDatePicker.getValue() == null) {
                endDatePicker.setInvalid(true);
                endDatePicker.setErrorMessage("End date is required");
                e.getSource().setEnabled(true);
                return;
            }

            try {
                var location = StringUtils.hasText(locationField.getValue())
                        ? locationField.getValue() : null;
                Competition saved;
                if (isEdit) {
                    saved = competitionService.updateCompetition(existing.getId(),
                            nameField.getValue(), shortNameField.getValue(),
                            startDatePicker.getValue(),
                            endDatePicker.getValue(), location, getCurrentUserId());
                } else {
                    saved = competitionService.createCompetition(nameField.getValue(),
                            shortNameField.getValue(),
                            startDatePicker.getValue(), endDatePicker.getValue(),
                            location, getCurrentUserId());
                }
                if (logoData[0] != null) {
                    competitionService.updateCompetitionLogo(
                            saved.getId(), logoData[0], logoContentType[0],
                            getCurrentUserId());
                }
                refreshGrid();
                var notification = Notification.show(
                        isEdit ? "Competition updated successfully" : "Competition created successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (BusinessRuleException ex) {
                e.getSource().setEnabled(true);
                Notification.show(getTranslation(ex.getMessageKey(), java.util.Locale.ENGLISH, ex.getParams()));
            }
        });
        submitButton.setDisableOnClick(true);

        var cancelButton = new Button("Cancel", e -> dialog.close());

        var form = new VerticalLayout(nameField, shortNameField, startDatePicker, endDatePicker, locationField, logoSection);
        form.setPadding(false);
        dialog.add(form);
        dialog.getFooter().add(cancelButton, submitButton);
        dialog.open();
    }

    private void openDeleteCompetitionDialog(Competition competition) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Delete Competition");
        var participants = competitionService.findParticipantsByCompetition(competition.getId());
        if (participants.isEmpty()) {
            dialog.add("Are you sure you want to delete \"" + competition.getName() + "\"?");
        } else {
            dialog.add("Are you sure you want to delete \"" + competition.getName() + "\"? "
                    + "This will also remove all " + participants.size() + " participant(s) and their roles.");
        }

        var confirmButton = new Button("Delete", e -> {
            try {
                competitionService.deleteCompetition(competition.getId(), getCurrentUserId());
                refreshGrid();
                var notification = Notification.show("Competition deleted successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (BusinessRuleException ex) {
                e.getSource().setEnabled(true);
                Notification.show(getTranslation(ex.getMessageKey(), java.util.Locale.ENGLISH, ex.getParams()));
                dialog.close();
            }
        });
        confirmButton.setDisableOnClick(true);

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    private void refreshGrid() {
        grid.setItems(competitionService.findAllCompetitions());
    }

    private UUID getCurrentUserId() {
        var email = authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElseThrow();
        return userService.findByEmail(email).getId();
    }
}
