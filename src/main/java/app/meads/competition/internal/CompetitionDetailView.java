package app.meads.competition.internal;

import app.meads.MainLayout;
import app.meads.competition.*;
import app.meads.identity.EmailService;
import app.meads.identity.User;
import app.meads.identity.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Nav;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Route(value = "competitions/:shortName", layout = MainLayout.class)
@PermitAll
public class CompetitionDetailView extends VerticalLayout implements BeforeEnterObserver {

    private final CompetitionService competitionService;
    private final UserService userService;
    private final EmailService emailService;
    private final transient AuthenticationContext authenticationContext;

    private UUID competitionId;
    private Competition competition;
    private HorizontalLayout header;
    private Nav breadcrumb;
    private Grid<Division> divisionsGrid;
    private Grid<ParticipantRole> participantsGrid;
    private Map<UUID, Participant> participantMap;
    private Map<UUID, User> userMap;

    public CompetitionDetailView(CompetitionService competitionService,
                                  UserService userService,
                                  EmailService emailService,
                                  AuthenticationContext authenticationContext) {
        this.competitionService = competitionService;
        this.userService = userService;
        this.emailService = emailService;
        this.authenticationContext = authenticationContext;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent beforeEnterEvent) {
        var shortName = beforeEnterEvent.getRouteParameters().get("shortName")
                .orElse(null);

        if (shortName == null) {
            beforeEnterEvent.forwardTo("competitions");
            return;
        }

        try {
            competition = competitionService.findCompetitionByShortName(shortName);
            competitionId = competition.getId();
        } catch (IllegalArgumentException e) {
            beforeEnterEvent.forwardTo("competitions");
            return;
        }

        if (!competitionService.isAuthorizedForCompetition(competitionId, getCurrentUserId())) {
            beforeEnterEvent.forwardTo("");
            return;
        }

        removeAll();
        breadcrumb = createBreadcrumb();
        header = createHeader();
        add(breadcrumb);
        add(header);
        add(createTabSheet());
    }

    private Nav createBreadcrumb() {
        var nav = new Nav();
        boolean isSystemAdmin = authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(user -> user.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_SYSTEM_ADMIN")))
                .orElse(false);
        var listLink = new Anchor(isSystemAdmin ? "competitions" : "my-competitions",
                isSystemAdmin ? "Competitions" : "My Competitions");
        nav.add(listLink);
        nav.add(new Span(" / "));
        nav.add(new Span(competition.getName()));
        return nav;
    }

    private HorizontalLayout createHeader() {
        var header = new HorizontalLayout();
        header.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        header.setWidthFull();

        if (competition.hasLogo()) {
            var dataUri = "data:" + competition.getLogoContentType() + ";base64,"
                    + Base64.getEncoder().encodeToString(competition.getLogo());
            var logo = new Image(dataUri, competition.getName() + " logo");
            logo.setHeight("64px");
            header.add(logo);
        }

        var textBlock = new VerticalLayout();
        textBlock.setPadding(false);
        textBlock.setSpacing(false);
        textBlock.add(new H2(competition.getName()));

        var details = formatDateRange();
        if (competition.getLocation() != null) {
            details += "  ·  " + competition.getLocation();
        }
        textBlock.add(new Span(details));

        header.add(textBlock);
        return header;
    }

    private void refreshHeader() {
        var newBreadcrumb = createBreadcrumb();
        replace(breadcrumb, newBreadcrumb);
        breadcrumb = newBreadcrumb;

        var newHeader = createHeader();
        replace(header, newHeader);
        header = newHeader;
    }

    private String formatDateRange() {
        var start = competition.getStartDate();
        var end = competition.getEndDate();
        var formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.ENGLISH);

        if (start.getMonth() == end.getMonth() && start.getYear() == end.getYear()) {
            return start.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.ENGLISH))
                    + "–" + end.format(java.time.format.DateTimeFormatter.ofPattern("d, yyyy", java.util.Locale.ENGLISH));
        }
        return start.format(formatter) + " – " + end.format(formatter);
    }

    private TabSheet createTabSheet() {
        var tabSheet = new TabSheet();
        tabSheet.setWidthFull();

        tabSheet.add("Divisions", createDivisionsTab());
        tabSheet.add("Participants", createParticipantsTab());
        tabSheet.add("Settings", createSettingsTab());

        return tabSheet;
    }

    private VerticalLayout createDivisionsTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var actions = new HorizontalLayout();
        actions.setWidthFull();
        actions.setJustifyContentMode(JustifyContentMode.END);
        actions.add(new Button("Create Division", e -> openCreateDivisionDialog()));
        tab.add(actions);

        divisionsGrid = new Grid<>(Division.class, false);
        divisionsGrid.setAllRowsVisible(true);
        divisionsGrid.addColumn(Division::getName).setHeader("Name").setSortable(true).setFlexGrow(2);
        divisionsGrid.addComponentColumn(div -> createStatusBadge(div.getStatus()))
                .setHeader("Status").setAutoWidth(true);
        divisionsGrid.addColumn(div -> div.getScoringSystem().name())
                .setHeader("Scoring").setAutoWidth(true);
        divisionsGrid.addComponentColumn(div -> {
            var revertButton = new Button(new Icon(VaadinIcon.BACKWARDS));
            revertButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            revertButton.setAriaLabel("Revert");
            revertButton.setTooltipText("Revert Status");
            revertButton.setEnabled(div.getStatus() != DivisionStatus.DRAFT);
            revertButton.addClickListener(e -> revertDivisionStatus(div));

            var advanceButton = new Button(new Icon(VaadinIcon.FORWARD));
            advanceButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            advanceButton.setAriaLabel("Advance");
            advanceButton.setTooltipText("Advance Status");
            advanceButton.setEnabled(div.getStatus() != DivisionStatus.RESULTS_PUBLISHED);
            advanceButton.addClickListener(e -> advanceDivisionStatus(div));

            var deleteButton = new Button(new Icon(VaadinIcon.TRASH));
            deleteButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            deleteButton.setAriaLabel("Delete");
            deleteButton.setTooltipText("Delete");
            deleteButton.addClickListener(e -> openDeleteDivisionDialog(div));

            return new HorizontalLayout(revertButton, advanceButton, deleteButton);
        }).setHeader("Actions").setAutoWidth(true);

        divisionsGrid.addItemClickListener(e ->
                e.getSource().getUI().ifPresent(ui ->
                        ui.navigate("competitions/" + competition.getShortName()
                                + "/divisions/" + e.getItem().getShortName())));

        refreshDivisionsGrid();
        tab.add(divisionsGrid);
        return tab;
    }

    private VerticalLayout createParticipantsTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var filterField = new TextField();
        filterField.setPlaceholder("Filter by name or email...");
        filterField.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.EAGER);
        filterField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        filterField.setClearButtonVisible(true);

        var addButton = new Button("Add Participant", e -> openAddParticipantDialog());

        var toolbar = new HorizontalLayout(filterField, addButton);
        toolbar.setWidthFull();
        toolbar.setFlexGrow(1, filterField);
        tab.add(toolbar);

        participantsGrid = new Grid<>();
        participantsGrid.setAllRowsVisible(true);
        participantsGrid.addColumn(pr -> {
            var participant = participantMap.get(pr.getParticipantId());
            var user = participant != null ? userMap.get(participant.getUserId()) : null;
            return user != null ? user.getName() : "Unknown";
        }).setHeader("Name").setSortable(true).setFlexGrow(2);
        participantsGrid.addColumn(pr -> {
            var participant = participantMap.get(pr.getParticipantId());
            var user = participant != null ? userMap.get(participant.getUserId()) : null;
            return user != null ? user.getEmail() : "—";
        }).setHeader("Email").setSortable(true).setFlexGrow(3);
        participantsGrid.addColumn(pr -> pr.getRole().getDisplayName()).setHeader("Role").setSortable(true).setAutoWidth(true);
        participantsGrid.addColumn(pr -> {
            var participant = participantMap.get(pr.getParticipantId());
            return participant != null && participant.getAccessCode() != null
                    ? participant.getAccessCode() : "—";
        }).setHeader("Access Code").setAutoWidth(true);
        participantsGrid.addComponentColumn(pr -> {
            var actions = new HorizontalLayout();
            actions.setSpacing(false);
            actions.getStyle().set("gap", "var(--lumo-space-xs)");

            var participant = participantMap.get(pr.getParticipantId());
            var user = participant != null ? userMap.get(participant.getUserId()) : null;

            if (user != null && user.getPasswordHash() == null) {
                var sendLinkButton = new Button(new Icon(VaadinIcon.ENVELOPE));
                sendLinkButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
                sendLinkButton.setTooltipText("Send login link");
                sendLinkButton.addClickListener(e -> sendMagicLink(user));
                actions.add(sendLinkButton);
            }

            var removeButton = new Button(new Icon(VaadinIcon.CLOSE));
            removeButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            removeButton.setTooltipText("Remove");
            removeButton.addClickListener(e -> openRemoveParticipantDialog(pr));
            actions.add(removeButton);

            return actions;
        }).setHeader("").setAutoWidth(true);

        refreshParticipantsGrid();

        filterField.addValueChangeListener(e -> {
            var filterString = e.getValue().toLowerCase();
            if (filterString.isBlank()) {
                participantsGrid.getListDataView().removeFilters();
            } else {
                participantsGrid.getListDataView().setFilter(pr -> {
                    var participant = participantMap.get(pr.getParticipantId());
                    var user = participant != null ? userMap.get(participant.getUserId()) : null;
                    if (user == null) return false;
                    return user.getName().toLowerCase().contains(filterString)
                            || user.getEmail().toLowerCase().contains(filterString);
                });
            }
        });

        tab.add(participantsGrid);
        return tab;
    }

    private void openRemoveParticipantDialog(ParticipantRole participantRole) {
        var participant = participantMap.get(participantRole.getParticipantId());
        var user = participant != null ? userMap.get(participant.getUserId()) : null;
        var displayName = user != null ? user.getEmail() : "this participant";

        var dialog = new Dialog();
        dialog.setHeaderTitle("Remove Participant");
        dialog.add("Remove " + displayName + " ("
                + participantRole.getRole().getDisplayName() + ") from this competition?");

        var confirmButton = new Button("Remove", e -> {
            try {
                competitionService.removeParticipant(
                        competitionId, participantRole.getParticipantId(), getCurrentUserId());
                refreshParticipantsGrid();
                var notification = Notification.show("Participant removed");
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

    private void openAddParticipantDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Add Participant");

        var emailField = new TextField("Email");

        var roleSelect = new Select<CompetitionRole>();
        roleSelect.setLabel("Role");
        roleSelect.setItems(CompetitionRole.values());
        roleSelect.setValue(CompetitionRole.JUDGE);

        var addButton = new Button("Add", e -> {
            if (!StringUtils.hasText(emailField.getValue())) {
                emailField.setInvalid(true);
                emailField.setErrorMessage("Email is required");
                return;
            }
            try {
                var email = emailField.getValue().trim();
                var role = roleSelect.getValue();
                competitionService.addParticipantByEmail(
                        competitionId, email, role, getCurrentUserId());
                refreshParticipantsGrid();
                var notification = Notification.show("Participant added successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                generatePasswordSetupLinkIfNeeded(email, role);
                dialog.close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());

        var form = new VerticalLayout(emailField, roleSelect);
        form.setPadding(false);
        dialog.add(form);
        dialog.getFooter().add(cancelButton, addButton);
        dialog.open();
    }

    private void refreshParticipantsGrid() {
        var roles = competitionService.findRolesByCompetition(competitionId);

        var participants = competitionService.findParticipantsByCompetition(competitionId);
        participantMap = participants.stream()
                .collect(Collectors.toMap(Participant::getId, Function.identity()));

        var userIds = participants.stream()
                .map(Participant::getUserId)
                .distinct()
                .toList();
        userMap = userService.findAllByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        participantsGrid.setItems(roles);
    }

    private VerticalLayout createSettingsTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var nameField = new TextField("Name");
        nameField.setValue(competition.getName());

        var shortNameField = new TextField("Short Name");
        shortNameField.setValue(competition.getShortName());
        shortNameField.setHelperText("URL-friendly identifier (e.g. chip-2026)");

        var startDatePicker = new DatePicker("Start Date");
        startDatePicker.setValue(competition.getStartDate());

        var endDatePicker = new DatePicker("End Date");
        endDatePicker.setValue(competition.getEndDate());

        var locationField = new TextField("Location");
        locationField.setValue(competition.getLocation() != null ? competition.getLocation() : "");

        var contactEmailField = new EmailField("Contact Email");
        contactEmailField.setValue(competition.getContactEmail() != null ? competition.getContactEmail() : "");
        contactEmailField.setHelperText("Shown in emails sent to competition participants");
        contactEmailField.setClearButtonVisible(true);

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

        if (competition.hasLogo()) {
            var removeLogoButton = new Button("Remove Logo", e -> {
                try {
                    competitionService.updateCompetitionLogo(
                            competitionId, null, null, getCurrentUserId());
                    competition = competitionService.findCompetitionById(competitionId);
                    logoSection.remove(e.getSource());
                    var notification = Notification.show("Logo removed");
                    notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                } catch (IllegalArgumentException ex) {
                    Notification.show(ex.getMessage());
                }
            });
            logoSection.add(removeLogoButton);
        }

        var saveButton = new Button("Save", e -> {
            if (!StringUtils.hasText(nameField.getValue())) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Name is required");
                return;
            }
            if (!StringUtils.hasText(shortNameField.getValue())) {
                shortNameField.setInvalid(true);
                shortNameField.setErrorMessage("Short name is required");
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
                competition = competitionService.updateCompetition(competitionId,
                        nameField.getValue(), shortNameField.getValue(),
                        startDatePicker.getValue(),
                        endDatePicker.getValue(), location, getCurrentUserId());
                var contactEmail = StringUtils.hasText(contactEmailField.getValue())
                        ? contactEmailField.getValue().trim() : null;
                competitionService.updateCompetitionContactEmail(
                        competitionId, contactEmail, getCurrentUserId());
                if (logoData[0] != null) {
                    competitionService.updateCompetitionLogo(
                            competitionId, logoData[0], logoContentType[0],
                            getCurrentUserId());
                }
                competition = competitionService.findCompetitionById(competitionId);
                refreshHeader();
                var notification = Notification.show("Competition updated successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        tab.add(nameField, shortNameField, startDatePicker, endDatePicker, locationField, contactEmailField, logoSection, saveButton);
        return tab;
    }

    private void openCreateDivisionDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Create Division");

        var nameField = new TextField("Name");
        nameField.setRequired(true);

        var shortNameField = new TextField("Short Name");
        shortNameField.setRequired(true);
        shortNameField.setHelperText("URL-friendly identifier (e.g. amadora)");
        shortNameField.setPattern("[a-z0-9][a-z0-9-]*[a-z0-9]");

        var scoringSelect = new Select<ScoringSystem>();
        scoringSelect.setLabel("Scoring System");
        scoringSelect.setItems(ScoringSystem.values());
        scoringSelect.setValue(ScoringSystem.MJP);

        var saveButton = new Button("Save", e -> {
            if (!StringUtils.hasText(nameField.getValue())) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Name is required");
                return;
            }
            if (!StringUtils.hasText(shortNameField.getValue())) {
                shortNameField.setInvalid(true);
                shortNameField.setErrorMessage("Short name is required");
                return;
            }
            try {
                competitionService.createDivision(
                        competitionId,
                        nameField.getValue(),
                        shortNameField.getValue(),
                        scoringSelect.getValue(),
                        getCurrentUserId());
                refreshDivisionsGrid();
                var notification = Notification.show("Division created successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());

        var form = new VerticalLayout(nameField, shortNameField, scoringSelect);
        form.setPadding(false);
        dialog.add(form);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void revertDivisionStatus(Division division) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Revert Status");
        var prevStatus = division.getStatus().previous()
                .map(DivisionStatus::getDisplayName).orElse("—");
        dialog.add("Revert division '" + division.getName() + "' from "
                + division.getStatus().getDisplayName() + " to " + prevStatus + "?");

        var confirmButton = new Button("Revert", e -> {
            try {
                competitionService.revertDivisionStatus(division.getId(), getCurrentUserId());
                refreshDivisionsGrid();
                var notification = Notification.show("Status reverted successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (IllegalArgumentException | IllegalStateException ex) {
                Notification.show(ex.getMessage());
                dialog.close();
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    private void advanceDivisionStatus(Division division) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Advance Status");
        var nextStatus = division.getStatus().next()
                .map(DivisionStatus::getDisplayName).orElse("—");
        dialog.add("Advance division '" + division.getName() + "' from "
                + division.getStatus().getDisplayName() + " to " + nextStatus + "?");

        var confirmButton = new Button("Advance", e -> {
            try {
                competitionService.advanceDivisionStatus(division.getId(), getCurrentUserId());
                refreshDivisionsGrid();
                var notification = Notification.show("Status advanced successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (IllegalArgumentException | IllegalStateException ex) {
                Notification.show(ex.getMessage());
                dialog.close();
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    private void openDeleteDivisionDialog(Division division) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Delete Division");
        dialog.add("Are you sure you want to delete \"" + division.getName() + "\"? "
                + "This will also remove all categories.");

        var confirmButton = new Button("Delete", e -> {
            try {
                competitionService.deleteDivision(division.getId(), getCurrentUserId());
                refreshDivisionsGrid();
                var notification = Notification.show("Division deleted successfully");
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

    private Span createStatusBadge(DivisionStatus status) {
        var badge = new Span(status.getDisplayName());
        badge.getElement().getThemeList().add("badge pill small");
        badge.addClassName(status.getBadgeCssClass());
        return badge;
    }

    private void refreshDivisionsGrid() {
        divisionsGrid.setItems(competitionService.findDivisionsByCompetition(competitionId));
    }

    private void sendMagicLink(User user) {
        emailService.sendMagicLink(user.getEmail());
        var notification = Notification.show("Login link sent to " + user.getEmail());
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void generatePasswordSetupLinkIfNeeded(String email, CompetitionRole role) {
        if (role == CompetitionRole.ADMIN) {
            try {
                var user = userService.findByEmail(email);
                if (!userService.hasPassword(user.getId())) {
                    emailService.sendPasswordSetup(email, competition.getName(),
                            competition.getContactEmail());
                    Notification.show("Password setup email sent to " + email);
                }
            } catch (IllegalArgumentException ignored) {
                // User not found — shouldn't happen since we just added them
            }
        }
    }

    private UUID getCurrentUserId() {
        var email = authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElseThrow();
        return userService.findByEmail(email).getId();
    }
}
