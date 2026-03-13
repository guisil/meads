package app.meads.competition.internal;

import app.meads.MainLayout;
import app.meads.competition.*;
import app.meads.identity.EmailService;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
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
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.io.ByteArrayInputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private Grid<Participant> participantsGrid;
    private Grid<CompetitionDocument> documentsGrid;
    private Map<UUID, User> userMap;
    private Map<UUID, List<ParticipantRole>> rolesMap;

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

        var currentUserId = getCurrentUserId();
        if (!competitionService.isAuthorizedForCompetition(competitionId, currentUserId)) {
            beforeEnterEvent.forwardTo("");
            return;
        }

        var currentUser = userService.findById(currentUserId);
        if (currentUser.getRole() != Role.SYSTEM_ADMIN && !userService.hasPassword(currentUserId)) {
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
        tabSheet.add("Documents", createDocumentsTab());

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
        divisionsGrid.addColumn(div -> {
            if (div.getRegistrationDeadline() == null) return "";
            var deadline = div.getRegistrationDeadline()
                    .atZone(ZoneId.of(div.getRegistrationDeadlineTimezone()))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return deadline + " " + div.getRegistrationDeadlineTimezone();
        }).setHeader("Registration Deadline").setSortable(true).setAutoWidth(true);
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
        participantsGrid.addColumn(p -> {
            var user = userMap.get(p.getUserId());
            return user != null ? user.getName() : "Unknown";
        }).setHeader("Name").setSortable(true).setFlexGrow(2);
        participantsGrid.addColumn(p -> {
            var user = userMap.get(p.getUserId());
            return user != null ? user.getEmail() : "—";
        }).setHeader("Email").setSortable(true).setFlexGrow(3);
        participantsGrid.addColumn(p -> {
            var user = userMap.get(p.getUserId());
            return user != null && user.getMeaderyName() != null ? user.getMeaderyName() : "—";
        }).setHeader("Meadery").setSortable(true).setFlexGrow(2);
        participantsGrid.addColumn(p -> {
            var user = userMap.get(p.getUserId());
            if (user == null || user.getCountry() == null) return "—";
            return new Locale("", user.getCountry()).getDisplayCountry(Locale.ENGLISH);
        }).setHeader("Country").setSortable(true).setAutoWidth(true);
        participantsGrid.addColumn(p -> {
            var roles = rolesMap.getOrDefault(p.getId(), List.of());
            return roles.stream()
                    .map(r -> r.getRole().getDisplayName())
                    .sorted()
                    .collect(Collectors.joining(", "));
        }).setHeader("Roles").setSortable(true).setAutoWidth(true);
        participantsGrid.addColumn(p ->
            p.getAccessCode() != null ? p.getAccessCode() : "—"
        ).setHeader("Access Code").setAutoWidth(true);
        participantsGrid.addComponentColumn(p -> {
            var actions = new HorizontalLayout();
            actions.setSpacing(false);
            actions.getStyle().set("gap", "var(--lumo-space-xs)");

            var user = userMap.get(p.getUserId());

            if (user != null && user.getPasswordHash() == null) {
                var sendLinkButton = new Button(new Icon(VaadinIcon.ENVELOPE));
                sendLinkButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
                sendLinkButton.setTooltipText("Send login link");
                sendLinkButton.addClickListener(e -> sendMagicLink(user));
                actions.add(sendLinkButton);
            }

            var editButton = new Button(new Icon(VaadinIcon.EDIT));
            editButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            editButton.setTooltipText("Edit roles");
            editButton.addClickListener(e -> openEditRolesDialog(p));
            actions.add(editButton);

            var removeButton = new Button(new Icon(VaadinIcon.CLOSE));
            removeButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            removeButton.setTooltipText("Remove");
            removeButton.addClickListener(e -> openRemoveParticipantDialog(p));
            actions.add(removeButton);

            return actions;
        }).setHeader("Actions").setAutoWidth(true);

        participantsGrid.getColumns().forEach(col -> col.setResizable(true));

        refreshParticipantsGrid();

        filterField.addValueChangeListener(e -> {
            var filterString = e.getValue().toLowerCase();
            if (filterString.isBlank()) {
                participantsGrid.getListDataView().removeFilters();
            } else {
                participantsGrid.getListDataView().setFilter(p -> {
                    var user = userMap.get(p.getUserId());
                    if (user == null) return false;
                    return user.getName().toLowerCase().contains(filterString)
                            || user.getEmail().toLowerCase().contains(filterString);
                });
            }
        });

        tab.add(participantsGrid);
        return tab;
    }

    private void openRemoveParticipantDialog(Participant participant) {
        var user = userMap.get(participant.getUserId());
        var displayName = user != null ? user.getEmail() : "this participant";
        var roles = rolesMap.getOrDefault(participant.getId(), List.of());
        var rolesDisplay = roles.stream()
                .map(r -> r.getRole().getDisplayName())
                .sorted()
                .collect(Collectors.joining(", "));

        var dialog = new Dialog();
        dialog.setHeaderTitle("Remove Participant");
        dialog.add("Remove " + displayName + " (" + rolesDisplay + ") from this competition?");

        var confirmButton = new Button("Remove", e -> {
            try {
                competitionService.removeParticipant(
                        competitionId, participant.getId(), getCurrentUserId());
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

    private void openEditRolesDialog(Participant participant) {
        var user = userMap.get(participant.getUserId());
        var displayName = user != null ? user.getEmail() : "Unknown";
        var currentRoles = rolesMap.getOrDefault(participant.getId(), List.of());
        var currentRoleSet = currentRoles.stream()
                .map(ParticipantRole::getRole)
                .collect(Collectors.toSet());

        var dialog = new Dialog();
        dialog.setHeaderTitle("Edit Participant — " + displayName);

        var nameField = new TextField("Name");
        nameField.setMaxLength(255);
        nameField.setWidthFull();
        if (user != null && StringUtils.hasText(user.getName()) && !user.getName().equals(user.getEmail())) {
            nameField.setValue(user.getName());
            nameField.setReadOnly(true);
        }

        var meaderyField = new TextField("Meadery");
        meaderyField.setMaxLength(255);
        meaderyField.setWidthFull();
        if (user != null && StringUtils.hasText(user.getMeaderyName())) {
            meaderyField.setValue(user.getMeaderyName());
            meaderyField.setReadOnly(true);
        }

        var countryCombo = createCountryComboBox();
        if (user != null && user.getCountry() != null) {
            countryCombo.setValue(user.getCountry());
            countryCombo.setReadOnly(true);
        }

        var checkboxes = new LinkedHashMap<CompetitionRole, Checkbox>();
        var rolesLayout = new VerticalLayout();
        rolesLayout.setPadding(false);
        rolesLayout.setSpacing(false);
        for (var role : CompetitionRole.values()) {
            var checkbox = new Checkbox(role.getDisplayName());
            checkbox.setValue(currentRoleSet.contains(role));
            checkboxes.put(role, checkbox);
            rolesLayout.add(checkbox);
        }

        var form = new VerticalLayout(nameField, meaderyField, countryCombo, rolesLayout);
        form.setPadding(false);
        dialog.add(form);

        var saveButton = new Button("Save", e -> {
            var selectedRoles = checkboxes.entrySet().stream()
                    .filter(entry -> entry.getValue().getValue())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            if (selectedRoles.isEmpty()) {
                Notification.show("At least one role must be selected");
                return;
            }

            var allowedCombination = Set.of(CompetitionRole.JUDGE, CompetitionRole.ENTRANT);
            if (selectedRoles.size() > 1 && !allowedCombination.containsAll(selectedRoles)) {
                Notification.show("Only Judge and Entrant roles can be combined");
                return;
            }

            try {
                // Fill in blank user fields
                if (user != null) {
                    fillInBlankUserFields(user.getEmail(),
                            nameField.isReadOnly() ? "" : nameField.getValue(),
                            meaderyField.isReadOnly() ? "" : meaderyField.getValue(),
                            countryCombo.isReadOnly() ? null : countryCombo.getValue());
                }

                // Remove roles that were unchecked
                for (var role : currentRoleSet) {
                    if (!selectedRoles.contains(role)) {
                        competitionService.removeParticipantRole(
                                competitionId, participant.getId(), role, getCurrentUserId());
                    }
                }
                // Add roles that were checked
                var userEmail = user != null ? user.getEmail() : null;
                for (var role : selectedRoles) {
                    if (!currentRoleSet.contains(role) && userEmail != null) {
                        competitionService.addParticipantByEmail(
                                competitionId, userEmail, role, getCurrentUserId());
                    }
                }
                refreshParticipantsGrid();
                var notification = Notification.show("Participant updated");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void openAddParticipantDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Add Participant");

        var emailField = new TextField("Email");
        emailField.setMaxLength(255);
        emailField.setWidthFull();

        var nameField = new TextField("Name");
        nameField.setMaxLength(255);
        nameField.setWidthFull();

        var meaderyField = new TextField("Meadery");
        meaderyField.setMaxLength(255);
        meaderyField.setWidthFull();

        var countryCombo = createCountryComboBox();

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
                var name = nameField.getValue() != null ? nameField.getValue().trim() : "";
                var meadery = meaderyField.getValue() != null ? meaderyField.getValue().trim() : "";
                var country = countryCombo.getValue();

                competitionService.addParticipantByEmail(
                        competitionId, email, role, getCurrentUserId());
                fillInBlankUserFields(email, name, meadery, country);
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

        var form = new VerticalLayout(emailField, nameField, meaderyField, countryCombo, roleSelect);
        form.setPadding(false);
        dialog.add(form);
        dialog.getFooter().add(cancelButton, addButton);
        dialog.open();
    }

    private void refreshParticipantsGrid() {
        var participants = competitionService.findParticipantsByCompetition(competitionId);

        rolesMap = participants.stream()
                .collect(Collectors.toMap(
                        Participant::getId,
                        p -> competitionService.findRolesForParticipant(p.getId())));

        var userIds = participants.stream()
                .map(Participant::getUserId)
                .distinct()
                .toList();
        userMap = userService.findAllByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        participantsGrid.setItems(participants);
    }

    private ComboBox<String> createCountryComboBox() {
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
        return countryCombo;
    }

    private void fillInBlankUserFields(String email, String name, String meadery, String country) {
        var user = userService.findByEmail(email);
        if (user == null) return;
        var currentName = user.getName();
        var currentMeadery = user.getMeaderyName();
        var currentCountry = user.getCountry();
        var updatedName = (!StringUtils.hasText(currentName) || currentName.equals(email))
                && StringUtils.hasText(name) ? name : currentName;
        var updatedMeadery = !StringUtils.hasText(currentMeadery)
                && StringUtils.hasText(meadery) ? meadery : currentMeadery;
        var updatedCountry = currentCountry == null && country != null ? country : currentCountry;
        if (!updatedName.equals(currentName) || !Objects.equals(updatedMeadery, currentMeadery)
                || !Objects.equals(updatedCountry, currentCountry)) {
            userService.updateProfile(user.getId(), updatedName, updatedMeadery, updatedCountry);
        }
    }

    private VerticalLayout createSettingsTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var nameField = new TextField("Name");
        nameField.setValue(competition.getName());
        nameField.setMaxLength(255);

        var shortNameField = new TextField("Short Name");
        shortNameField.setValue(competition.getShortName());
        shortNameField.setMaxLength(100);
        shortNameField.setHelperText("URL-friendly identifier (e.g. chip-2026)");

        var startDatePicker = new DatePicker("Start Date");
        startDatePicker.setValue(competition.getStartDate());

        var endDatePicker = new DatePicker("End Date");
        endDatePicker.setValue(competition.getEndDate());

        var locationField = new TextField("Location");
        locationField.setValue(competition.getLocation() != null ? competition.getLocation() : "");
        locationField.setMaxLength(500);

        var contactEmailField = new EmailField("Contact Email");
        contactEmailField.setValue(competition.getContactEmail() != null ? competition.getContactEmail() : "");
        contactEmailField.setHelperText("Shown in emails sent to competition participants");
        contactEmailField.setClearButtonVisible(true);
        contactEmailField.setMaxLength(255);

        var shippingAddressField = new TextArea("Shipping Address");
        shippingAddressField.setValue(competition.getShippingAddress() != null ? competition.getShippingAddress() : "");
        shippingAddressField.setHelperText("Shown on entry labels — where entrants should ship their bottles");
        shippingAddressField.setWidthFull();
        shippingAddressField.setMaxLength(1000);

        var phoneNumberField = new TextField("Phone Number");
        phoneNumberField.setValue(competition.getPhoneNumber() != null ? competition.getPhoneNumber() : "");
        phoneNumberField.setMaxLength(50);
        phoneNumberField.setHelperText("Contact phone number shown on entry labels");
        phoneNumberField.setClearButtonVisible(true);

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

        var logoLabel = new Span("Logo (PNG or JPEG, max 2.5 MB)");
        logoLabel.addClassName("field-label");

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
            if (StringUtils.hasText(contactEmailField.getValue()) && contactEmailField.isInvalid()) {
                contactEmailField.setErrorMessage("Please enter a valid email address");
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
                var shippingAddress = StringUtils.hasText(shippingAddressField.getValue())
                        ? shippingAddressField.getValue().trim() : null;
                var phoneNumber = StringUtils.hasText(phoneNumberField.getValue())
                        ? phoneNumberField.getValue().trim() : null;
                competitionService.updateCompetitionShippingDetails(
                        competitionId, shippingAddress, phoneNumber, getCurrentUserId());
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

        tab.add(nameField, shortNameField, startDatePicker, endDatePicker, locationField, contactEmailField, shippingAddressField, phoneNumberField, logoLabel, logoSection, saveButton);
        return tab;
    }

    private void openCreateDivisionDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Create Division");

        var nameField = new TextField("Name");
        nameField.setRequired(true);
        nameField.setMaxLength(255);

        var shortNameField = new TextField("Short Name");
        shortNameField.setRequired(true);
        shortNameField.setMaxLength(100);
        shortNameField.setHelperText("URL-friendly identifier (e.g. amadora)");
        shortNameField.setPattern("[a-z0-9][a-z0-9-]*[a-z0-9]");

        var scoringSelect = new Select<ScoringSystem>();
        scoringSelect.setLabel("Scoring System");
        scoringSelect.setItems(ScoringSystem.values());
        scoringSelect.setValue(ScoringSystem.MJP);

        var deadlinePicker = new DateTimePicker("Registration Deadline");
        deadlinePicker.setRequiredIndicatorVisible(true);

        var timezoneCombo = new ComboBox<String>("Timezone");
        timezoneCombo.setItems(ZoneId.getAvailableZoneIds().stream().sorted().toList());
        timezoneCombo.setValue("UTC");
        timezoneCombo.setRequired(true);
        timezoneCombo.setAllowCustomValue(false);

        var maxPerSubcategoryField = new IntegerField("Max Entries per Subcategory");
        maxPerSubcategoryField.setMin(1);
        maxPerSubcategoryField.setStepButtonsVisible(true);
        maxPerSubcategoryField.setClearButtonVisible(true);
        maxPerSubcategoryField.setHelperText("Per entrant per subcategory (empty = unlimited)");

        var maxPerMainCategoryField = new IntegerField("Max Entries per Main Category");
        maxPerMainCategoryField.setMin(1);
        maxPerMainCategoryField.setStepButtonsVisible(true);
        maxPerMainCategoryField.setClearButtonVisible(true);
        maxPerMainCategoryField.setHelperText("Per entrant per main category (empty = unlimited)");

        var maxTotalField = new IntegerField("Max Total Entries");
        maxTotalField.setMin(1);
        maxTotalField.setStepButtonsVisible(true);
        maxTotalField.setClearButtonVisible(true);
        maxTotalField.setHelperText("Per entrant total in this division (empty = unlimited)");

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
            if (deadlinePicker.getValue() == null) {
                deadlinePicker.setInvalid(true);
                deadlinePicker.setErrorMessage("Registration deadline is required");
                return;
            }
            try {
                var division = competitionService.createDivision(
                        competitionId,
                        nameField.getValue(),
                        shortNameField.getValue(),
                        scoringSelect.getValue(),
                        deadlinePicker.getValue(),
                        timezoneCombo.getValue(),
                        getCurrentUserId());
                if (maxPerSubcategoryField.getValue() != null
                        || maxPerMainCategoryField.getValue() != null
                        || maxTotalField.getValue() != null) {
                    competitionService.updateDivisionEntryLimits(
                            division.getId(),
                            maxPerSubcategoryField.getValue(),
                            maxPerMainCategoryField.getValue(),
                            maxTotalField.getValue(),
                            getCurrentUserId());
                }
                refreshDivisionsGrid();
                var notification = Notification.show("Division created successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());

        var form = new VerticalLayout(nameField, shortNameField, scoringSelect,
                maxPerSubcategoryField, maxPerMainCategoryField, maxTotalField,
                deadlinePicker, timezoneCombo);
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

    private VerticalLayout createDocumentsTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var actions = new HorizontalLayout();
        actions.setWidthFull();
        actions.setJustifyContentMode(JustifyContentMode.END);
        actions.add(new Button("Add Document", e -> openAddDocumentDialog()));
        tab.add(actions);

        documentsGrid = new Grid<>(CompetitionDocument.class, false);
        documentsGrid.setAllRowsVisible(true);
        documentsGrid.addColumn(CompetitionDocument::getName).setHeader("Name").setFlexGrow(3);
        documentsGrid.addComponentColumn(doc -> {
            var badge = new Span(doc.getType().name());
            badge.getElement().getThemeList().add("badge pill small");
            return badge;
        }).setHeader("Type").setAutoWidth(true);
        documentsGrid.addComponentColumn(doc -> {
            var layout = new HorizontalLayout();
            layout.setSpacing(false);
            layout.getStyle().set("gap", "var(--lumo-space-xs)");

            if (doc.getType() == DocumentType.PDF) {
                var downloadAnchor = new Anchor(
                        new StreamResource(doc.getName() + ".pdf",
                                () -> new ByteArrayInputStream(
                                        competitionService.getDocument(doc.getId()).getData())),
                        "");
                downloadAnchor.getElement().setAttribute("download", true);
                var downloadButton = new Button(new Icon(VaadinIcon.DOWNLOAD));
                downloadButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
                downloadButton.setTooltipText("Download");
                downloadAnchor.add(downloadButton);
                layout.add(downloadAnchor);
            } else {
                var openAnchor = new Anchor(doc.getUrl(), "");
                openAnchor.setTarget("_blank");
                var openButton = new Button(new Icon(VaadinIcon.EXTERNAL_LINK));
                openButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
                openButton.setTooltipText("Open link");
                openAnchor.add(openButton);
                layout.add(openAnchor);
            }

            var upButton = new Button(new Icon(VaadinIcon.ARROW_UP));
            upButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            upButton.setTooltipText("Move up");
            upButton.addClickListener(e -> moveDocument(documentsGrid, doc, -1));

            var downButton = new Button(new Icon(VaadinIcon.ARROW_DOWN));
            downButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            downButton.setTooltipText("Move down");
            downButton.addClickListener(e -> moveDocument(documentsGrid, doc, 1));

            var editButton = new Button(new Icon(VaadinIcon.EDIT));
            editButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            editButton.setTooltipText("Edit name");
            editButton.addClickListener(e -> openEditDocumentNameDialog(documentsGrid, doc));

            var deleteButton = new Button(new Icon(VaadinIcon.TRASH));
            deleteButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            deleteButton.setTooltipText("Delete");
            deleteButton.addClickListener(e -> openDeleteDocumentDialog(documentsGrid, doc));

            layout.add(upButton, downButton, editButton, deleteButton);
            return layout;
        }).setHeader("Actions").setAutoWidth(true);

        documentsGrid.setItems(competitionService.getDocuments(competitionId));
        tab.add(documentsGrid);
        return tab;
    }

    private void openAddDocumentDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Add Document");

        var nameField = new TextField("Name");
        nameField.setRequired(true);
        nameField.setWidthFull();
        nameField.setMaxLength(255);

        var typeSelect = new Select<DocumentType>();
        typeSelect.setLabel("Type");
        typeSelect.setItems(DocumentType.values());
        typeSelect.setValue(DocumentType.PDF);
        typeSelect.setWidthFull();

        var urlField = new TextField("URL");
        urlField.setWidthFull();
        urlField.setMaxLength(2000);
        urlField.setVisible(false);

        var pdfData = new byte[1][];
        var pdfContentType = new String[1];

        var uploadHandler = UploadHandler.inMemory((metadata, data) -> {
            pdfData[0] = data;
            pdfContentType[0] = metadata.contentType();
        });
        var upload = new Upload(uploadHandler);
        upload.setMaxFiles(1);
        upload.setMaxFileSize(10 * 1024 * 1024);
        upload.setAcceptedFileTypes("application/pdf");
        upload.addFileRejectedListener(e ->
                Notification.show(e.getErrorMessage())
                        .addThemeVariants(NotificationVariant.LUMO_ERROR));

        typeSelect.addValueChangeListener(e -> {
            upload.setVisible(e.getValue() == DocumentType.PDF);
            urlField.setVisible(e.getValue() == DocumentType.LINK);
        });

        var saveButton = new Button("Save", e -> {
            if (!StringUtils.hasText(nameField.getValue())) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Name is required");
                return;
            }
            try {
                var type = typeSelect.getValue();
                if (type == DocumentType.PDF && pdfData[0] == null) {
                    Notification.show("Please upload a PDF file");
                    return;
                }
                if (type == DocumentType.LINK && !StringUtils.hasText(urlField.getValue())) {
                    urlField.setInvalid(true);
                    urlField.setErrorMessage("URL is required");
                    return;
                }
                competitionService.addDocument(competitionId, nameField.getValue().trim(),
                        type, pdfData[0], pdfContentType[0],
                        type == DocumentType.LINK ? urlField.getValue().trim() : null,
                        getCurrentUserId());
                documentsGrid.setItems(competitionService.getDocuments(competitionId));
                var notification = Notification.show("Document added successfully");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());

        var form = new VerticalLayout(nameField, typeSelect, upload, urlField);
        form.setPadding(false);
        dialog.add(form);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void moveDocument(Grid<CompetitionDocument> grid, CompetitionDocument doc, int direction) {
        var docs = competitionService.getDocuments(competitionId);
        var ids = docs.stream().map(CompetitionDocument::getId).collect(java.util.stream.Collectors.toList());
        int currentIndex = ids.indexOf(doc.getId());
        int targetIndex = currentIndex + direction;
        if (targetIndex < 0 || targetIndex >= ids.size()) return;
        ids.remove(currentIndex);
        ids.add(targetIndex, doc.getId());
        try {
            competitionService.reorderDocuments(competitionId, ids, getCurrentUserId());
            grid.setItems(competitionService.getDocuments(competitionId));
        } catch (IllegalArgumentException ex) {
            Notification.show(ex.getMessage());
        }
    }

    private void openEditDocumentNameDialog(Grid<CompetitionDocument> grid, CompetitionDocument doc) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Edit Document Name");

        var nameField = new TextField("Name");
        nameField.setMaxLength(255);
        nameField.setValue(doc.getName());
        nameField.setWidthFull();

        var saveButton = new Button("Save", e -> {
            if (!StringUtils.hasText(nameField.getValue())) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Name is required");
                return;
            }
            try {
                competitionService.updateDocumentName(doc.getId(),
                        nameField.getValue().trim(), getCurrentUserId());
                grid.setItems(competitionService.getDocuments(competitionId));
                var notification = Notification.show("Document name updated");
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });

        var cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.add(nameField);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void openDeleteDocumentDialog(Grid<CompetitionDocument> grid, CompetitionDocument doc) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Delete Document");
        dialog.add("Are you sure you want to delete \"" + doc.getName() + "\"?");

        var confirmButton = new Button("Delete", e -> {
            try {
                competitionService.removeDocument(doc.getId(), getCurrentUserId());
                grid.setItems(competitionService.getDocuments(competitionId));
                var notification = Notification.show("Document deleted");
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

    private UUID getCurrentUserId() {
        var email = authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElseThrow();
        return userService.findByEmail(email).getId();
    }
}
