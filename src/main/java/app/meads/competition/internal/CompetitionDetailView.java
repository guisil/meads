package app.meads.competition.internal;

import app.meads.BusinessRuleException;
import app.meads.LanguageMapping;
import app.meads.MainLayout;
import app.meads.MeadsI18NProvider;
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
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
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
import com.vaadin.flow.component.html.Paragraph;
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
import com.vaadin.flow.data.value.ValueChangeMode;
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
        } catch (BusinessRuleException e) {
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
                isSystemAdmin ? getTranslation("nav.competitions") : getTranslation("nav.my-competitions"));
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
        var formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

        if (start.getMonth() == end.getMonth() && start.getYear() == end.getYear()) {
            return start.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
                    + "–" + end.format(DateTimeFormatter.ofPattern("d, yyyy", Locale.ENGLISH));
        }
        return start.format(formatter) + " – " + end.format(formatter);
    }

    private TabSheet createTabSheet() {
        var tabSheet = new TabSheet();
        tabSheet.setWidthFull();

        tabSheet.add(getTranslation("competition-detail.tab.divisions"), createDivisionsTab());
        tabSheet.add(getTranslation("competition-detail.tab.participants"), createParticipantsTab());
        tabSheet.add(getTranslation("competition-detail.tab.settings"), createSettingsTab());
        tabSheet.add(getTranslation("competition-detail.tab.documents"), createDocumentsTab());

        return tabSheet;
    }

    private VerticalLayout createDivisionsTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var actions = new HorizontalLayout();
        actions.setWidthFull();
        actions.setJustifyContentMode(JustifyContentMode.END);
        actions.add(new Button(getTranslation("competition-detail.divisions.create"), e -> openCreateDivisionDialog()));
        tab.add(actions);

        divisionsGrid = new Grid<>(Division.class, false);
        divisionsGrid.setAllRowsVisible(true);
        divisionsGrid.addColumn(Division::getName).setHeader(getTranslation("competition-detail.divisions.column.name")).setSortable(true).setFlexGrow(2);
        divisionsGrid.addComponentColumn(div -> createStatusBadge(div.getStatus()))
                .setHeader(getTranslation("competition-detail.divisions.column.status")).setAutoWidth(true);
        divisionsGrid.addColumn(div -> div.getScoringSystem().name())
                .setHeader(getTranslation("competition-detail.divisions.column.scoring")).setAutoWidth(true);
        divisionsGrid.addColumn(div -> {
            if (div.getRegistrationDeadline() == null) return "";
            var deadline = div.getRegistrationDeadline()
                    .atZone(ZoneId.of(div.getRegistrationDeadlineTimezone()))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return deadline + " " + div.getRegistrationDeadlineTimezone();
        }).setHeader(getTranslation("competition-detail.divisions.column.deadline")).setSortable(true).setAutoWidth(true);
        divisionsGrid.addComponentColumn(div -> {
            var revertButton = new Button(new Icon(VaadinIcon.BACKWARDS));
            revertButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            revertButton.setAriaLabel(getTranslation("competition-detail.divisions.action.revert"));
            revertButton.setTooltipText(getTranslation("competition-detail.divisions.action.revert.tooltip"));
            revertButton.setEnabled(div.getStatus() != DivisionStatus.DRAFT);
            revertButton.addClickListener(e -> revertDivisionStatus(div));

            var advanceButton = new Button(new Icon(VaadinIcon.FORWARD));
            advanceButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            advanceButton.setAriaLabel(getTranslation("competition-detail.divisions.action.advance"));
            advanceButton.setTooltipText(getTranslation("competition-detail.divisions.action.advance.tooltip"));
            advanceButton.setEnabled(div.getStatus() != DivisionStatus.RESULTS_PUBLISHED);
            advanceButton.addClickListener(e -> advanceDivisionStatus(div));

            var deleteButton = new Button(new Icon(VaadinIcon.TRASH));
            deleteButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            deleteButton.setAriaLabel(getTranslation("competition-detail.divisions.action.delete"));
            deleteButton.setTooltipText(getTranslation("competition-detail.divisions.action.delete"));
            deleteButton.addClickListener(e -> openDeleteDivisionDialog(div));

            return new HorizontalLayout(revertButton, advanceButton, deleteButton);
        }).setHeader(getTranslation("competition-detail.divisions.column.actions")).setAutoWidth(true);

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
        filterField.setPlaceholder(getTranslation("competition-detail.participants.filter.placeholder"));
        filterField.setValueChangeMode(ValueChangeMode.EAGER);
        filterField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        filterField.setClearButtonVisible(true);

        var addButton = new Button(getTranslation("competition-detail.participants.add"), e -> openAddParticipantDialog());

        var toolbar = new HorizontalLayout(filterField, addButton);
        toolbar.setWidthFull();
        toolbar.setFlexGrow(1, filterField);
        tab.add(toolbar);

        participantsGrid = new Grid<>();
        participantsGrid.setAllRowsVisible(true);
        participantsGrid.addColumn(p -> {
            var user = userMap.get(p.getUserId());
            return user != null ? user.getName() : getTranslation("competition-detail.participants.unknown");
        }).setHeader(getTranslation("competition-detail.participants.column.name")).setSortable(true).setFlexGrow(2);
        participantsGrid.addColumn(p -> {
            var user = userMap.get(p.getUserId());
            return user != null ? user.getEmail() : "—";
        }).setHeader(getTranslation("competition-detail.participants.column.email")).setSortable(true).setFlexGrow(3);
        participantsGrid.addColumn(p -> {
            var user = userMap.get(p.getUserId());
            return user != null && user.getMeaderyName() != null ? user.getMeaderyName() : "—";
        }).setHeader(getTranslation("competition-detail.participants.column.meadery")).setSortable(true).setFlexGrow(2);
        participantsGrid.addColumn(p -> {
            var user = userMap.get(p.getUserId());
            if (user == null || user.getCountry() == null) return "—";
            return new Locale("", user.getCountry()).getDisplayCountry(Locale.ENGLISH);
        }).setHeader(getTranslation("competition-detail.participants.column.country")).setSortable(true).setAutoWidth(true);
        participantsGrid.addColumn(p -> {
            var roles = rolesMap.getOrDefault(p.getId(), List.of());
            return roles.stream()
                    .map(r -> r.getRole().getDisplayName())
                    .sorted()
                    .collect(Collectors.joining(", "));
        }).setHeader(getTranslation("competition-detail.participants.column.roles")).setSortable(true).setAutoWidth(true);
        participantsGrid.addColumn(p ->
            p.getAccessCode() != null ? p.getAccessCode() : "—"
        ).setHeader(getTranslation("competition-detail.participants.column.access-code")).setAutoWidth(true);
        participantsGrid.addComponentColumn(p -> {
            var actions = new HorizontalLayout();
            actions.setSpacing(false);
            actions.getStyle().set("gap", "var(--lumo-space-xs)");

            var user = userMap.get(p.getUserId());

            var editButton = new Button(new Icon(VaadinIcon.EDIT));
            editButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            editButton.setTooltipText(getTranslation("competition-detail.participants.action.edit.tooltip"));
            editButton.addClickListener(e -> openEditRolesDialog(p));
            actions.add(editButton);

            if (user != null && user.getPasswordHash() == null) {
                var sendLinkButton = new Button(new Icon(VaadinIcon.ENVELOPE));
                sendLinkButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
                sendLinkButton.setTooltipText(getTranslation("competition-detail.participants.action.login-link.tooltip"));
                sendLinkButton.addClickListener(e -> sendMagicLink(user));
                actions.add(sendLinkButton);
            }

            var removeButton = new Button(new Icon(VaadinIcon.CLOSE));
            removeButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            removeButton.setTooltipText(getTranslation("competition-detail.participants.action.remove.tooltip"));
            removeButton.addClickListener(e -> openRemoveParticipantDialog(p));
            actions.add(removeButton);

            return actions;
        }).setHeader(getTranslation("competition-detail.participants.column.actions")).setAutoWidth(true);

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
        var displayName = user != null ? user.getEmail() : getTranslation("competition-detail.participants.unknown");
        var roles = rolesMap.getOrDefault(participant.getId(), List.of());
        var rolesDisplay = roles.stream()
                .map(r -> r.getRole().getDisplayName())
                .sorted()
                .collect(Collectors.joining(", "));

        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("competition-detail.participants.remove.title"));
        dialog.add(getTranslation("competition-detail.participants.remove.confirm", displayName, rolesDisplay));
        dialog.add(new Paragraph(getTranslation("competition-detail.participants.remove.warning")));

        var confirmButton = new Button(getTranslation("competition-detail.participants.remove.button"), e -> {
            try {
                competitionService.removeParticipant(
                        competitionId, participant.getId(), getCurrentUserId());
                refreshParticipantsGrid();
                var notification = Notification.show(getTranslation("competition-detail.participants.removed"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
                dialog.close();
            }
        });
        confirmButton.setDisableOnClick(true);

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    private void openEditRolesDialog(Participant participant) {
        var user = userMap.get(participant.getUserId());
        var displayName = user != null ? user.getEmail() : getTranslation("competition-detail.participants.unknown");
        var currentRoles = rolesMap.getOrDefault(participant.getId(), List.of());
        var currentRoleSet = currentRoles.stream()
                .map(ParticipantRole::getRole)
                .collect(Collectors.toSet());

        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("competition-detail.participants.edit.title", displayName));

        var nameField = new TextField(getTranslation("competition-detail.participants.edit.name"));
        nameField.setMaxLength(255);
        nameField.setWidthFull();
        if (user != null && StringUtils.hasText(user.getName()) && !user.getName().equals(user.getEmail())) {
            nameField.setValue(user.getName());
            nameField.setReadOnly(true);
        }

        var meaderyField = new TextField(getTranslation("competition-detail.participants.edit.meadery"));
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

        var saveButton = new Button(getTranslation("button.save"), e -> {
            var selectedRoles = checkboxes.entrySet().stream()
                    .filter(entry -> entry.getValue().getValue())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            if (selectedRoles.isEmpty()) {
                Notification.show(getTranslation("competition-detail.participants.edit.role-error"));
                e.getSource().setEnabled(true);
                return;
            }

            var allowedCombination = Set.of(CompetitionRole.JUDGE, CompetitionRole.ENTRANT);
            if (selectedRoles.size() > 1 && !allowedCombination.containsAll(selectedRoles)) {
                Notification.show(getTranslation("competition-detail.participants.edit.role-conflict"));
                e.getSource().setEnabled(true);
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
                var notification = Notification.show(getTranslation("competition-detail.participants.updated"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
            }
        });
        saveButton.setDisableOnClick(true);

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void openAddParticipantDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("competition-detail.participants.add.title"));

        var emailField = new TextField(getTranslation("competition-detail.participants.add.email"));
        emailField.setMaxLength(255);
        emailField.setWidthFull();

        var nameField = new TextField(getTranslation("competition-detail.participants.add.name"));
        nameField.setMaxLength(255);
        nameField.setWidthFull();

        var meaderyField = new TextField(getTranslation("competition-detail.participants.add.meadery"));
        meaderyField.setMaxLength(255);
        meaderyField.setWidthFull();

        var countryCombo = createCountryComboBox();

        var roleSelect = new Select<CompetitionRole>();
        roleSelect.setLabel(getTranslation("competition-detail.participants.add.role"));
        roleSelect.setItems(CompetitionRole.values());
        roleSelect.setValue(CompetitionRole.JUDGE);

        var addButton = new Button(getTranslation("competition-detail.participants.add.button"), e -> {
            if (!StringUtils.hasText(emailField.getValue())) {
                emailField.setInvalid(true);
                emailField.setErrorMessage(getTranslation("competition-detail.participants.add.email.error"));
                e.getSource().setEnabled(true);
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
                var notification = Notification.show(getTranslation("competition-detail.participants.added"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                generatePasswordSetupLinkIfNeeded(email, role);
                dialog.close();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
            }
        });
        addButton.setDisableOnClick(true);

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());

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
        var countryCombo = new ComboBox<String>(getTranslation("competition-detail.settings.country"));
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
            userService.updateProfile(user.getId(), updatedName, updatedMeadery, updatedCountry, user.getPreferredLanguage());
        }
    }

    private VerticalLayout createSettingsTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var nameField = new TextField(getTranslation("competition-detail.settings.name"));
        nameField.setValue(competition.getName());
        nameField.setMaxLength(255);
        nameField.setWidth("400px");

        var shortNameField = new TextField(getTranslation("competition-detail.settings.short-name"));
        shortNameField.setValue(competition.getShortName());
        shortNameField.setMaxLength(100);
        shortNameField.setHelperText(getTranslation("competition-detail.settings.short-name.helper"));

        var startDatePicker = new DatePicker(getTranslation("competition-detail.settings.start-date"));
        startDatePicker.setValue(competition.getStartDate());

        var endDatePicker = new DatePicker(getTranslation("competition-detail.settings.end-date"));
        endDatePicker.setValue(competition.getEndDate());

        var locationField = new TextField(getTranslation("competition-detail.settings.location"));
        locationField.setValue(competition.getLocation() != null ? competition.getLocation() : "");
        locationField.setMaxLength(500);
        locationField.setWidth("400px");

        var contactEmailField = new EmailField(getTranslation("competition-detail.settings.contact-email"));
        contactEmailField.setValue(competition.getContactEmail() != null ? competition.getContactEmail() : "");
        contactEmailField.setHelperText(getTranslation("competition-detail.settings.contact-email.helper"));
        contactEmailField.setClearButtonVisible(true);
        contactEmailField.setMaxLength(255);
        contactEmailField.setWidth("400px");

        var shippingAddressField = new TextArea(getTranslation("competition-detail.settings.shipping-address"));
        shippingAddressField.setValue(competition.getShippingAddress() != null ? competition.getShippingAddress() : "");
        shippingAddressField.setHelperText(getTranslation("competition-detail.settings.shipping-address.helper"));
        shippingAddressField.setWidthFull();
        shippingAddressField.setMaxLength(1000);

        var phoneNumberField = new TextField(getTranslation("competition-detail.settings.phone"));
        phoneNumberField.setValue(competition.getPhoneNumber() != null ? competition.getPhoneNumber() : "");
        phoneNumberField.setMaxLength(50);
        phoneNumberField.setHelperText(getTranslation("competition-detail.settings.phone.helper"));
        phoneNumberField.setClearButtonVisible(true);

        var websiteField = new TextField(getTranslation("competition-detail.settings.website"));
        websiteField.setValue(competition.getWebsite() != null ? competition.getWebsite() : "");
        websiteField.setMaxLength(500);
        websiteField.setHelperText(getTranslation("competition-detail.settings.website.helper"));
        websiteField.setClearButtonVisible(true);

        var judgingSection = new Span(getTranslation("competition-detail.settings.judging.section"));
        judgingSection.addClassName("field-label");

        var commentLanguagesCombo = new MultiSelectComboBox<String>(
                getTranslation("competition-detail.settings.comment-languages.label"));
        commentLanguagesCombo.setId("comment-languages-combo");
        var uiLocale = UI.getCurrent().getLocale();
        var languageCodes = MeadsI18NProvider.getSupportedLanguageCodes().stream()
                .sorted((a, b) -> java.util.Locale.forLanguageTag(a).getDisplayLanguage(uiLocale)
                        .compareTo(java.util.Locale.forLanguageTag(b).getDisplayLanguage(uiLocale)))
                .toList();
        commentLanguagesCombo.setItems(languageCodes);
        commentLanguagesCombo.setItemLabelGenerator(
                code -> java.util.Locale.forLanguageTag(code).getDisplayLanguage(uiLocale));
        commentLanguagesCombo.setHelperText(getTranslation("competition-detail.settings.comment-languages.help"));
        commentLanguagesCombo.setWidth("400px");
        commentLanguagesCombo.setValue(competition.getCommentLanguages());

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

        var logoLabel = new Span(getTranslation("competition-detail.settings.logo.label"));
        logoLabel.addClassName("field-label");

        var logoSection = new HorizontalLayout();
        logoSection.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        logoSection.add(upload);

        if (competition.hasLogo()) {
            var removeLogoButton = new Button(getTranslation("competition-detail.settings.logo.remove"), e -> {
                try {
                    competitionService.updateCompetitionLogo(
                            competitionId, null, null, getCurrentUserId());
                    competition = competitionService.findCompetitionById(competitionId);
                    logoSection.remove(e.getSource());
                    var notification = Notification.show(getTranslation("competition-detail.settings.logo.removed"));
                    notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                } catch (BusinessRuleException ex) {
                    Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                    e.getSource().setEnabled(true);
                }
            });
            removeLogoButton.setDisableOnClick(true);
            logoSection.add(removeLogoButton);
        }

        var saveButton = new Button(getTranslation("button.save"), e -> {
            if (!StringUtils.hasText(nameField.getValue())) {
                nameField.setInvalid(true);
                nameField.setErrorMessage(getTranslation("competition-detail.settings.name.error"));
                e.getSource().setEnabled(true);
                return;
            }
            if (!StringUtils.hasText(shortNameField.getValue())) {
                shortNameField.setInvalid(true);
                shortNameField.setErrorMessage(getTranslation("competition-detail.settings.short-name.error"));
                e.getSource().setEnabled(true);
                return;
            }
            if (startDatePicker.getValue() == null) {
                startDatePicker.setInvalid(true);
                startDatePicker.setErrorMessage(getTranslation("competition-detail.settings.start-date.error"));
                e.getSource().setEnabled(true);
                return;
            }
            if (endDatePicker.getValue() == null) {
                endDatePicker.setInvalid(true);
                endDatePicker.setErrorMessage(getTranslation("competition-detail.settings.end-date.error"));
                e.getSource().setEnabled(true);
                return;
            }
            if (StringUtils.hasText(contactEmailField.getValue()) && contactEmailField.isInvalid()) {
                contactEmailField.setErrorMessage(getTranslation("competition-detail.settings.contact-email.error"));
                e.getSource().setEnabled(true);
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
                var website = StringUtils.hasText(websiteField.getValue())
                        ? websiteField.getValue().trim() : null;
                competitionService.updateCompetitionShippingDetails(
                        competitionId, shippingAddress, phoneNumber, website, getCurrentUserId());
                competitionService.updateCommentLanguages(competitionId,
                        commentLanguagesCombo.getValue(), getCurrentUserId());
                if (logoData[0] != null) {
                    competitionService.updateCompetitionLogo(
                            competitionId, logoData[0], logoContentType[0],
                            getCurrentUserId());
                }
                competition = competitionService.findCompetitionById(competitionId);
                refreshHeader();
                var notification = Notification.show(getTranslation("competition-detail.settings.updated"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                e.getSource().setEnabled(true);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
            }
        });
        saveButton.setDisableOnClick(true);

        tab.add(nameField, shortNameField, startDatePicker, endDatePicker, locationField, contactEmailField, shippingAddressField, phoneNumberField, websiteField, judgingSection, commentLanguagesCombo, logoLabel, logoSection, saveButton);
        return tab;
    }

    private void openCreateDivisionDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("competition-detail.division.create.title"));

        var nameField = new TextField(getTranslation("competition-detail.division.create.name"));
        nameField.setRequired(true);
        nameField.setMaxLength(255);

        var shortNameField = new TextField(getTranslation("competition-detail.division.create.short-name"));
        shortNameField.setRequired(true);
        shortNameField.setMaxLength(100);
        shortNameField.setHelperText(getTranslation("competition-detail.division.create.short-name.helper"));
        shortNameField.setPattern("[a-z0-9][a-z0-9-]*[a-z0-9]");

        var scoringSelect = new Select<ScoringSystem>();
        scoringSelect.setLabel(getTranslation("competition-detail.division.create.scoring"));
        scoringSelect.setItems(ScoringSystem.values());
        scoringSelect.setValue(ScoringSystem.MJP);

        var deadlinePicker = new DateTimePicker(getTranslation("competition-detail.division.create.deadline"));
        deadlinePicker.setRequiredIndicatorVisible(true);

        var timezoneCombo = new ComboBox<String>(getTranslation("competition-detail.division.create.timezone"));
        timezoneCombo.setItems(ZoneId.getAvailableZoneIds().stream().sorted().toList());
        timezoneCombo.setValue("UTC");
        timezoneCombo.setRequired(true);
        timezoneCombo.setAllowCustomValue(false);

        var maxPerSubcategoryField = new IntegerField(getTranslation("competition-detail.division.create.max-per-subcategory"));
        maxPerSubcategoryField.setMin(1);
        maxPerSubcategoryField.setStepButtonsVisible(true);
        maxPerSubcategoryField.setClearButtonVisible(true);
        maxPerSubcategoryField.setHelperText(getTranslation("competition-detail.division.create.max-per-subcategory.helper"));

        var maxPerMainCategoryField = new IntegerField(getTranslation("competition-detail.division.create.max-per-main-category"));
        maxPerMainCategoryField.setMin(1);
        maxPerMainCategoryField.setStepButtonsVisible(true);
        maxPerMainCategoryField.setClearButtonVisible(true);
        maxPerMainCategoryField.setHelperText(getTranslation("competition-detail.division.create.max-per-main-category.helper"));

        var maxTotalField = new IntegerField(getTranslation("competition-detail.division.create.max-total"));
        maxTotalField.setMin(1);
        maxTotalField.setStepButtonsVisible(true);
        maxTotalField.setClearButtonVisible(true);
        maxTotalField.setHelperText(getTranslation("competition-detail.division.create.max-total.helper"));

        var saveButton = new Button(getTranslation("button.save"), e -> {
            if (!StringUtils.hasText(nameField.getValue())) {
                nameField.setInvalid(true);
                nameField.setErrorMessage(getTranslation("competition-detail.division.create.name.error"));
                e.getSource().setEnabled(true);
                return;
            }
            if (!StringUtils.hasText(shortNameField.getValue())) {
                shortNameField.setInvalid(true);
                shortNameField.setErrorMessage(getTranslation("competition-detail.division.create.short-name.error"));
                e.getSource().setEnabled(true);
                return;
            }
            if (deadlinePicker.getValue() == null) {
                deadlinePicker.setInvalid(true);
                deadlinePicker.setErrorMessage(getTranslation("competition-detail.division.create.deadline.error"));
                e.getSource().setEnabled(true);
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
                var notification = Notification.show(getTranslation("competition-detail.division.created"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
            }
        });
        saveButton.setDisableOnClick(true);

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());

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
        dialog.setHeaderTitle(getTranslation("competition-detail.division.revert.title"));
        var prevStatus = division.getStatus().previous()
                .map(DivisionStatus::getDisplayName).orElse("—");
        dialog.add(getTranslation("competition-detail.division.revert.confirm", division.getName(), division.getStatus().getDisplayName(), prevStatus));

        var confirmButton = new Button(getTranslation("competition-detail.division.revert.button"), e -> {
            try {
                competitionService.revertDivisionStatus(division.getId(), getCurrentUserId());
                refreshDivisionsGrid();
                var notification = Notification.show(getTranslation("competition-detail.division.reverted"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
                dialog.close();
            }
        });
        confirmButton.setDisableOnClick(true);

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    private void advanceDivisionStatus(Division division) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("competition-detail.division.advance.title"));
        var nextStatus = division.getStatus().next()
                .map(DivisionStatus::getDisplayName).orElse("—");
        dialog.add(getTranslation("competition-detail.division.advance.confirm", division.getName(), division.getStatus().getDisplayName(), nextStatus));

        var confirmButton = new Button(getTranslation("competition-detail.division.advance.button"), e -> {
            try {
                competitionService.advanceDivisionStatus(division.getId(), getCurrentUserId());
                refreshDivisionsGrid();
                var notification = Notification.show(getTranslation("competition-detail.division.advanced"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
                dialog.close();
            }
        });
        confirmButton.setDisableOnClick(true);

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    private void openDeleteDivisionDialog(Division division) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("competition-detail.division.delete.title"));
        dialog.add(getTranslation("competition-detail.division.delete.confirm", division.getName()));

        var confirmButton = new Button(getTranslation("button.delete"), e -> {
            try {
                competitionService.deleteDivision(division.getId(), getCurrentUserId());
                refreshDivisionsGrid();
                var notification = Notification.show(getTranslation("competition-detail.division.deleted"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
                dialog.close();
            }
        });
        confirmButton.setDisableOnClick(true);

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());
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
        var locale = LanguageMapping.resolveLocale(user.getPreferredLanguage(), user.getCountry());
        emailService.sendMagicLink(user.getEmail(), locale);
        var notification = Notification.show(getTranslation("competition-detail.participants.login-link.sent", user.getEmail()));
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void generatePasswordSetupLinkIfNeeded(String email, CompetitionRole role) {
        if (role == CompetitionRole.ADMIN) {
            try {
                var user = userService.findByEmail(email);
                if (!userService.hasPassword(user.getId())) {
                    var locale = LanguageMapping.resolveLocale(user.getPreferredLanguage(), user.getCountry());
                    emailService.sendPasswordSetup(email, competition.getName(),
                            competition.getContactEmail(), locale);
                    Notification.show(getTranslation("competition-detail.participants.password-setup.sent", email));
                }
            } catch (BusinessRuleException ignored) {
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
        actions.add(new Button(getTranslation("competition-detail.documents.add"), e -> openAddDocumentDialog()));
        tab.add(actions);

        documentsGrid = new Grid<>(CompetitionDocument.class, false);
        documentsGrid.setAllRowsVisible(true);
        documentsGrid.addColumn(CompetitionDocument::getName).setHeader(getTranslation("competition-detail.documents.column.name")).setFlexGrow(3);
        documentsGrid.addComponentColumn(doc -> {
            var badge = new Span(doc.getType().name());
            badge.getElement().getThemeList().add("badge pill small");
            return badge;
        }).setHeader(getTranslation("competition-detail.documents.column.type")).setAutoWidth(true);
        documentsGrid.addColumn(doc -> doc.getLanguage() != null
                ? MeadsI18NProvider.getLanguageLabel(doc.getLanguage())
                : getTranslation("competition-detail.documents.language.all")).setHeader(getTranslation("competition-detail.documents.column.language")).setAutoWidth(true);
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
                downloadButton.setTooltipText(getTranslation("competition-detail.documents.action.download.tooltip"));
                downloadAnchor.add(downloadButton);
                layout.add(downloadAnchor);
            } else {
                var openAnchor = new Anchor(doc.getUrl(), "");
                openAnchor.setTarget("_blank");
                var openButton = new Button(new Icon(VaadinIcon.EXTERNAL_LINK));
                openButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
                openButton.setTooltipText(getTranslation("competition-detail.documents.action.open.tooltip"));
                openAnchor.add(openButton);
                layout.add(openAnchor);
            }

            var upButton = new Button(new Icon(VaadinIcon.ARROW_UP));
            upButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            upButton.setTooltipText(getTranslation("competition-detail.documents.action.up.tooltip"));
            upButton.addClickListener(e -> moveDocument(documentsGrid, doc, -1));

            var downButton = new Button(new Icon(VaadinIcon.ARROW_DOWN));
            downButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            downButton.setTooltipText(getTranslation("competition-detail.documents.action.down.tooltip"));
            downButton.addClickListener(e -> moveDocument(documentsGrid, doc, 1));

            var editButton = new Button(new Icon(VaadinIcon.EDIT));
            editButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            editButton.setTooltipText(getTranslation("competition-detail.documents.action.edit.tooltip"));
            editButton.addClickListener(e -> openEditDocumentNameDialog(documentsGrid, doc));

            var deleteButton = new Button(new Icon(VaadinIcon.TRASH));
            deleteButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            deleteButton.setTooltipText(getTranslation("competition-detail.documents.action.delete.tooltip"));
            deleteButton.addClickListener(e -> openDeleteDocumentDialog(documentsGrid, doc));

            layout.add(upButton, downButton, editButton, deleteButton);
            return layout;
        }).setHeader(getTranslation("competition-detail.documents.column.actions")).setAutoWidth(true);

        documentsGrid.setItems(competitionService.getDocuments(competitionId));
        tab.add(documentsGrid);
        return tab;
    }

    private void openAddDocumentDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("competition-detail.documents.add.title"));

        var nameField = new TextField(getTranslation("competition-detail.documents.add.name"));
        nameField.setRequired(true);
        nameField.setWidthFull();
        nameField.setMaxLength(255);

        var typeSelect = new Select<DocumentType>();
        typeSelect.setLabel(getTranslation("competition-detail.documents.add.type"));
        typeSelect.setItems(DocumentType.values());
        typeSelect.setValue(DocumentType.PDF);
        typeSelect.setWidthFull();

        var urlField = new TextField(getTranslation("competition-detail.documents.add.url"));
        urlField.setWidthFull();
        urlField.setMaxLength(2000);
        urlField.setVisible(false);

        var languageCombo = new ComboBox<String>(getTranslation("competition-detail.documents.add.language"));
        languageCombo.setItems(MeadsI18NProvider.getSupportedLanguageCodes());
        languageCombo.setItemLabelGenerator(MeadsI18NProvider::getLanguageLabel);
        languageCombo.setClearButtonVisible(true);
        languageCombo.setPlaceholder(getTranslation("competition-detail.documents.add.language.placeholder"));
        languageCombo.setWidthFull();

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

        var saveButton = new Button(getTranslation("button.save"), e -> {
            if (!StringUtils.hasText(nameField.getValue())) {
                nameField.setInvalid(true);
                nameField.setErrorMessage(getTranslation("competition-detail.documents.add.name.error"));
                e.getSource().setEnabled(true);
                return;
            }
            try {
                var type = typeSelect.getValue();
                if (type == DocumentType.PDF && pdfData[0] == null) {
                    Notification.show(getTranslation("competition-detail.documents.add.pdf.error"));
                    e.getSource().setEnabled(true);
                    return;
                }
                if (type == DocumentType.LINK && !StringUtils.hasText(urlField.getValue())) {
                    urlField.setInvalid(true);
                    urlField.setErrorMessage(getTranslation("competition-detail.documents.add.url.error"));
                    e.getSource().setEnabled(true);
                    return;
                }
                competitionService.addDocument(competitionId, nameField.getValue().trim(),
                        type, pdfData[0], pdfContentType[0],
                        type == DocumentType.LINK ? urlField.getValue().trim() : null,
                        languageCombo.getValue(), getCurrentUserId());
                documentsGrid.setItems(competitionService.getDocuments(competitionId));
                var notification = Notification.show(getTranslation("competition-detail.documents.added"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
            }
        });
        saveButton.setDisableOnClick(true);

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());

        var form = new VerticalLayout(nameField, typeSelect, upload, urlField, languageCombo);
        form.setPadding(false);
        dialog.add(form);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void moveDocument(Grid<CompetitionDocument> grid, CompetitionDocument doc, int direction) {
        var docs = competitionService.getDocuments(competitionId);
        var ids = docs.stream().map(CompetitionDocument::getId).collect(Collectors.toList());
        int currentIndex = ids.indexOf(doc.getId());
        int targetIndex = currentIndex + direction;
        if (targetIndex < 0 || targetIndex >= ids.size()) return;
        ids.remove(currentIndex);
        ids.add(targetIndex, doc.getId());
        try {
            competitionService.reorderDocuments(competitionId, ids, getCurrentUserId());
            grid.setItems(competitionService.getDocuments(competitionId));
        } catch (BusinessRuleException ex) {
            Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
        }
    }

    private void openEditDocumentNameDialog(Grid<CompetitionDocument> grid, CompetitionDocument doc) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("competition-detail.documents.edit.title"));

        var nameField = new TextField(getTranslation("competition-detail.documents.edit.name"));
        nameField.setMaxLength(255);
        nameField.setValue(doc.getName());
        nameField.setWidthFull();

        var saveButton = new Button(getTranslation("button.save"), e -> {
            if (!StringUtils.hasText(nameField.getValue())) {
                nameField.setInvalid(true);
                nameField.setErrorMessage(getTranslation("competition-detail.documents.edit.name.error"));
                e.getSource().setEnabled(true);
                return;
            }
            try {
                competitionService.updateDocumentName(doc.getId(),
                        nameField.getValue().trim(), getCurrentUserId());
                grid.setItems(competitionService.getDocuments(competitionId));
                var notification = Notification.show(getTranslation("competition-detail.documents.edit.updated"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
            }
        });
        saveButton.setDisableOnClick(true);

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.add(nameField);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void openDeleteDocumentDialog(Grid<CompetitionDocument> grid, CompetitionDocument doc) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("competition-detail.documents.delete.title"));
        dialog.add(getTranslation("competition-detail.documents.delete.confirm", doc.getName()));

        var confirmButton = new Button(getTranslation("button.delete"), e -> {
            try {
                competitionService.removeDocument(doc.getId(), getCurrentUserId());
                grid.setItems(competitionService.getDocuments(competitionId));
                var notification = Notification.show(getTranslation("competition-detail.documents.deleted"));
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()));
                e.getSource().setEnabled(true);
                dialog.close();
            }
        });
        confirmButton.setDisableOnClick(true);

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());
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
