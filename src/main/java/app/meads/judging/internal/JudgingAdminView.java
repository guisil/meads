package app.meads.judging.internal;

import app.meads.BusinessRuleException;
import app.meads.MainLayout;
import app.meads.competition.Competition;
import app.meads.competition.CompetitionRole;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import app.meads.competition.DivisionStatus;
import app.meads.entry.Entry;
import app.meads.entry.EntryService;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserService;
import app.meads.judging.CategoryJudgingConfig;
import app.meads.judging.CoiCheckService;
import app.meads.judging.Medal;
import app.meads.judging.MedalRoundStatus;
import app.meads.judging.JudgingPhase;
import app.meads.judging.BosPlacement;
import app.meads.judging.Judging;
import app.meads.judging.JudgingService;
import app.meads.judging.JudgingRound;
import app.meads.judging.JudgingRoundStatus;
import app.meads.judging.RoundType;
import app.meads.judging.MedalAward;
import app.meads.judging.ScoresheetService;
import app.meads.judging.ScoresheetStatus;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Nav;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Route(value = "competitions/:compShortName/divisions/:divShortName/judging-admin", layout = MainLayout.class)
@PermitAll
public class JudgingAdminView extends VerticalLayout implements BeforeEnterObserver {

    private final CompetitionService competitionService;
    private final UserService userService;
    private final JudgingService judgingService;
    private final ScoresheetService scoresheetService;
    private final EntryService entryService;
    private final CoiCheckService coiCheckService;
    private final transient AuthenticationContext authenticationContext;

    private Competition competition;
    private Division division;
    private Judging judging;
    private String compShortName;
    private String divShortName;
    private UUID currentUserId;

    private Grid<JudgingRound> roundsGrid;
    private ComboBox<RoundTypeFilter> roundsTypeFilter;

    /** Filter values for the Rounds tab Type ComboBox. ALL is the null-object case. */
    enum RoundTypeFilter {
        ALL, SCORING, MEDAL
    }

    public JudgingAdminView(CompetitionService competitionService,
                            UserService userService,
                            JudgingService judgingService,
                            ScoresheetService scoresheetService,
                            EntryService entryService,
                            CoiCheckService coiCheckService,
                            AuthenticationContext authenticationContext) {
        this.competitionService = competitionService;
        this.userService = userService;
        this.judgingService = judgingService;
        this.scoresheetService = scoresheetService;
        this.entryService = entryService;
        this.coiCheckService = coiCheckService;
        this.authenticationContext = authenticationContext;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        compShortName = event.getRouteParameters().get("compShortName").orElse(null);
        divShortName = event.getRouteParameters().get("divShortName").orElse(null);

        if (compShortName == null || divShortName == null) {
            event.forwardTo("");
            return;
        }

        try {
            competition = competitionService.findCompetitionByShortName(compShortName);
            division = competitionService.findDivisionByShortName(competition.getId(), divShortName);
        } catch (BusinessRuleException e) {
            event.forwardTo("");
            return;
        }

        currentUserId = getCurrentUserId();
        var user = userService.findById(currentUserId);

        if (user.getRole() != Role.SYSTEM_ADMIN
                && !competitionService.isAuthorizedForDivision(division.getId(), currentUserId)) {
            event.forwardTo("");
            return;
        }

        if (user.getRole() != Role.SYSTEM_ADMIN && !userService.hasPassword(currentUserId)) {
            event.forwardTo("");
            return;
        }

        if (division.getStatus().ordinal() < DivisionStatus.JUDGING.ordinal()) {
            event.forwardTo("competitions/" + compShortName + "/divisions/" + divShortName);
            return;
        }

        judging = judgingService.ensureJudgingExists(division.getId());

        beforeEnterRefresh();
    }

    private Nav createBreadcrumb() {
        var nav = new Nav();
        var user = userService.findById(currentUserId);
        boolean isSystemAdmin = user.getRole() == Role.SYSTEM_ADMIN;
        nav.add(new Anchor(
                isSystemAdmin ? "competitions" : "my-competitions",
                isSystemAdmin ? getTranslation("nav.competitions") : getTranslation("nav.my-competitions")));
        nav.add(new Span(" / "));
        nav.add(new Anchor("competitions/" + compShortName, competition.getName()));
        nav.add(new Span(" / "));
        nav.add(new Anchor(
                "competitions/" + compShortName + "/divisions/" + divShortName,
                division.getName()));
        nav.add(new Span(" / "));
        nav.add(new Span(getTranslation("judging-admin.nav.judging-admin")));
        return nav;
    }

    private HorizontalLayout createHeader() {
        var header = new HorizontalLayout();
        header.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        if (competition.hasLogo()) {
            var dataUri = "data:" + competition.getLogoContentType() + ";base64,"
                    + Base64.getEncoder().encodeToString(competition.getLogo());
            var logo = new Image(dataUri, competition.getName() + " logo");
            logo.setHeight("64px");
            header.add(logo);
        }

        header.add(new H2(competition.getName() + " — " + division.getName()
                + " — " + getTranslation("judging-admin.nav.judging-admin")));

        if (division.getStatus().ordinal() >= DivisionStatus.DELIBERATION.ordinal()) {
            var manageResults = new Button(getTranslation("judging-admin.manage-results"),
                    e -> com.vaadin.flow.component.UI.getCurrent().navigate(
                            "competitions/" + compShortName
                                    + "/divisions/" + divShortName + "/results-admin"));
            manageResults.setId("judging-admin-manage-results");
            header.add(manageResults);
        }
        return header;
    }

    private TabSheet createTabSheet() {
        var tabSheet = new TabSheet();
        tabSheet.setWidthFull();
        tabSheet.add(getTranslation("judging-admin.tab.physical-tables"), createPhysicalTablesTab());
        tabSheet.add(getTranslation("judging-admin.tab.rounds"), createRoundsTab());
        tabSheet.add(getTranslation("judging-admin.tab.results"), createResultsTab());
        tabSheet.add(getTranslation("judging-admin.tab.bos"), createBosTab());
        return tabSheet;
    }

    private Grid<app.meads.judging.PhysicalTable> physicalTablesGrid;

    private VerticalLayout createPhysicalTablesTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var addButton = new Button(getTranslation("judging-admin.physical-tables.add"),
                e -> openAddPhysicalTableDialog());
        addButton.setId("add-physical-table-button");
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        tab.add(addButton);

        physicalTablesGrid = new Grid<>(app.meads.judging.PhysicalTable.class, false);
        physicalTablesGrid.setId("physical-tables-grid");
        physicalTablesGrid.addColumn(app.meads.judging.PhysicalTable::getLabel)
                .setHeader(getTranslation("judging-admin.physical-tables.column.label"));
        physicalTablesGrid.addComponentColumn(this::physicalTableActions)
                .setHeader(getTranslation("judging-admin.physical-tables.column.actions"));
        refreshPhysicalTablesGrid();
        tab.add(physicalTablesGrid);
        return tab;
    }

    private void refreshPhysicalTablesGrid() {
        if (physicalTablesGrid != null) {
            physicalTablesGrid.setItems(judgingService.findPhysicalTablesByDivision(division.getId()));
        }
    }

    private HorizontalLayout physicalTableActions(app.meads.judging.PhysicalTable pt) {
        var layout = new HorizontalLayout();
        layout.setPadding(false);
        var editButton = new Button(new Icon(VaadinIcon.EDIT));
        editButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        editButton.setTooltipText(getTranslation("judging-admin.physical-tables.action.edit"));
        editButton.addClickListener(e -> openEditPhysicalTableDialog(pt));
        var deleteButton = new Button(new Icon(VaadinIcon.TRASH));
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
        deleteButton.setTooltipText(getTranslation("judging-admin.physical-tables.action.delete"));
        deleteButton.addClickListener(e -> openDeletePhysicalTableDialog(pt));
        layout.add(editButton, deleteButton);
        return layout;
    }

    /** Package-public for tests — exercised via the +Add toolbar button in normal use. */
    public void openAddPhysicalTableDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("judging-admin.physical-tables.add"));
        var labelField = new TextField(getTranslation("judging-admin.physical-tables.label"));
        labelField.setId("add-physical-table-label");
        labelField.setMaxLength(50);
        labelField.setWidthFull();
        dialog.add(new VerticalLayout(labelField));
        var saveBtn = new Button(getTranslation("button.save"), e -> {
            try {
                judgingService.createPhysicalTable(division.getId(), labelField.getValue(), currentUserId);
                dialog.close();
                refreshPhysicalTablesGrid();
                Notification.show(getTranslation("judging-admin.physical-tables.added"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                e.getSource().setEnabled(true);
            }
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.setDisableOnClick(true);
        var cancelBtn = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

    /** Package-public for tests — wired to the per-row Edit action button. */
    public void openEditPhysicalTableDialog(app.meads.judging.PhysicalTable pt) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("judging-admin.physical-tables.edit"));
        var labelField = new TextField(getTranslation("judging-admin.physical-tables.label"));
        labelField.setValue(pt.getLabel());
        labelField.setMaxLength(50);
        labelField.setWidthFull();
        dialog.add(new VerticalLayout(labelField));
        var saveBtn = new Button(getTranslation("button.save"), e -> {
            try {
                judgingService.updatePhysicalTableLabel(pt.getId(), labelField.getValue(), currentUserId);
                dialog.close();
                refreshPhysicalTablesGrid();
                Notification.show(getTranslation("judging-admin.physical-tables.updated"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                e.getSource().setEnabled(true);
            }
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.setDisableOnClick(true);
        var cancelBtn = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

    /** Package-public for tests — wired to the per-row Delete action button. */
    public void openDeletePhysicalTableDialog(app.meads.judging.PhysicalTable pt) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("judging-admin.physical-tables.delete.title", pt.getLabel()));
        dialog.add(new Span(getTranslation("judging-admin.physical-tables.delete.body", pt.getLabel())));
        var deleteBtn = new Button(getTranslation("button.delete"), e -> {
            try {
                judgingService.deletePhysicalTable(pt.getId(), currentUserId);
                dialog.close();
                refreshPhysicalTablesGrid();
                Notification.show(getTranslation("judging-admin.physical-tables.deleted"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                e.getSource().setEnabled(true);
            }
        });
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
        deleteBtn.setDisableOnClick(true);
        var cancelBtn = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancelBtn, deleteBtn);
        dialog.open();
    }

    private String formatCategory(UUID divisionCategoryId) {
        if (divisionCategoryId == null) {
            return "";
        }
        return categoriesById().getOrDefault(divisionCategoryId, null) instanceof DivisionCategory dc
                ? dc.getCode() + " — " + dc.getName()
                : "";
    }

    private Map<UUID, DivisionCategory> categoriesById() {
        return competitionService.findJudgingCategories(division.getId()).stream()
                .collect(Collectors.toMap(DivisionCategory::getId, c -> c));
    }

    public void openEditTableDialog(JudgingRound table) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("judging-admin.tables.action.edit"));

        var form = new VerticalLayout();
        form.setPadding(false);

        var nameField = new TextField(getTranslation("judging-admin.tables.dialog.name"));
        nameField.setId("edit-table-name");
        nameField.setWidthFull();
        nameField.setMaxLength(120);
        nameField.setValue(table.getName());

        var datePicker = new DatePicker(getTranslation("judging-admin.tables.dialog.scheduled"));
        datePicker.setId("edit-table-scheduled");
        datePicker.setWidthFull();
        datePicker.setValue(table.getScheduledDate());

        form.add(nameField, datePicker);
        dialog.add(form);

        var saveButton = new Button(getTranslation("button.save"), e -> {
            if (nameField.getValue() == null || nameField.getValue().isBlank()) {
                nameField.setInvalid(true);
                nameField.setErrorMessage(getTranslation("judging-admin.tables.dialog.name.error"));
                return;
            }
            try {
                if (!nameField.getValue().trim().equals(table.getName())) {
                    judgingService.updateRoundName(table.getId(), nameField.getValue().trim(), currentUserId);
                }
                if (!java.util.Objects.equals(datePicker.getValue(), table.getScheduledDate())) {
                    judgingService.updateRoundScheduledDate(table.getId(), datePicker.getValue(), currentUserId);
                }
                dialog.close();
                refreshRoundsGrid();
                Notification.show(getTranslation("judging-admin.tables.updated"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.setDisableOnClick(true);

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());

        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    public void openStartTableDialog(JudgingRound table) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("judging-admin.tables.action.start.confirm.title", table.getName()));
        boolean hasEntries = !entryService.findEntriesByFinalCategoryId(table.getDivisionCategoryId()).isEmpty();
        var bodyKey = hasEntries
                ? "judging-admin.tables.action.start.confirm.body"
                : "judging-admin.tables.action.start.confirm.body.empty";
        dialog.add(new Span(getTranslation(bodyKey)));

        var startButton = new Button(getTranslation("judging-admin.tables.action.start"), e -> {
            try {
                judgingService.startRound(table.getId(), currentUserId);
                dialog.close();
                refreshRoundsGrid();
                Notification.show(getTranslation("judging-admin.tables.started"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        startButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        startButton.setDisableOnClick(true);

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());

        dialog.getFooter().add(cancelButton, startButton);
        dialog.open();
    }

    public void openAssignJudgesDialog(JudgingRound table) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("judging-admin.tables.action.assign-judges"));
        dialog.setWidth("700px");

        var availableJudges = competitionService.findUsersByRoleInCompetition(
                competition.getId(), CompetitionRole.JUDGE);
        var entriesInCategory = entryService.findEntriesByFinalCategoryId(table.getDivisionCategoryId());
        var currentlyAssigned = table.getAssignments().stream()
                .map(a -> a.getJudgeUserId())
                .collect(Collectors.toSet());

        var judgesGrid = new Grid<User>(User.class, false);
        judgesGrid.setId("assign-judges-grid");
        judgesGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        judgesGrid.addColumn(User::getName)
                .setHeader(getTranslation("judging-admin.tables.assign.column.name"));
        judgesGrid.addColumn(u -> u.getMeaderyName() == null ? "" : u.getMeaderyName())
                .setHeader(getTranslation("judging-admin.tables.assign.column.meadery"));
        judgesGrid.addColumn(u -> u.getCountry() == null ? "" : u.getCountry())
                .setHeader(getTranslation("judging-admin.tables.assign.column.country"));
        judgesGrid.addComponentColumn(judge -> coiChips(judge, entriesInCategory))
                .setHeader(getTranslation("judging-admin.tables.assign.column.coi"));
        judgesGrid.setItems(availableJudges);
        availableJudges.stream()
                .filter(j -> currentlyAssigned.contains(j.getId()))
                .forEach(j -> judgesGrid.asMultiSelect().select(j));

        dialog.add(judgesGrid);

        var saveButton = new Button(getTranslation("button.save"), e -> {
            var selected = judgesGrid.asMultiSelect().getSelectedItems().stream()
                    .map(User::getId)
                    .collect(Collectors.toSet());
            try {
                for (var judgeId : selected) {
                    if (!currentlyAssigned.contains(judgeId)) {
                        judgingService.assignJudge(table.getId(), judgeId, currentUserId);
                    }
                }
                for (var judgeId : currentlyAssigned) {
                    if (!selected.contains(judgeId)) {
                        judgingService.removeJudge(table.getId(), judgeId, currentUserId);
                    }
                }
                dialog.close();
                refreshRoundsGrid();
                Notification.show(getTranslation("judging-admin.tables.assign.saved"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.setDisableOnClick(true);

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());

        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private HorizontalLayout coiChips(User judge, List<Entry> entries) {
        var layout = new HorizontalLayout();
        layout.setSpacing(false);
        for (var entry : entries) {
            var coi = coiCheckService.check(judge.getId(), entry.getId());
            if (coi.hardBlock()) {
                var chip = new Span(getTranslation("judging-admin.tables.assign.coi.hard"));
                chip.getElement().getThemeList().add("badge error");
                layout.add(chip);
            } else if (coi.softWarningKey().isPresent()) {
                var chip = new Span(getTranslation("judging-admin.tables.assign.coi.soft", entry.getEntryNumber()));
                chip.getElement().getThemeList().add("badge contrast");
                layout.add(chip);
            }
        }
        return layout;
    }

    public void openDeleteTableDialog(JudgingRound table) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("judging-admin.tables.action.delete.confirm.title", table.getName()));
        dialog.add(new Span(getTranslation("judging-admin.tables.action.delete.confirm.body")));

        var deleteButton = new Button(getTranslation("button.delete"), e -> {
            try {
                judgingService.deleteRound(table.getId(), currentUserId);
                dialog.close();
                refreshRoundsGrid();
                Notification.show(getTranslation("judging-admin.tables.deleted"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        deleteButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        deleteButton.setDisableOnClick(true);

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());

        dialog.getFooter().add(cancelButton, deleteButton);
        dialog.open();
    }

    private void beforeEnterRefresh() {
        removeAll();
        add(createBreadcrumb());
        add(createHeader());
        long unassigned = entryService.countEntriesNeedingFinalCategory(division.getId());
        if (unassigned > 0) {
            add(createUnassignedWarning(unassigned));
        }
        add(createTabSheet());
    }

    private Span createUnassignedWarning(long count) {
        var warning = new Span(getTranslation("judging-admin.unassigned-warning", count));
        warning.setId("judging-admin-unassigned-warning");
        warning.getStyle().set("color", "var(--lumo-error-text-color)")
                .set("font-weight", "600");
        return warning;
    }

    private VerticalLayout createRoundsTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var topRow = new HorizontalLayout();
        topRow.setDefaultVerticalComponentAlignment(Alignment.END);

        var addButton = new Button(getTranslation("judging-admin.rounds.add"),
                e -> openAddRoundDialog());
        addButton.setId("add-round-button");
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        topRow.add(addButton);

        roundsTypeFilter = new ComboBox<>(getTranslation("judging-admin.rounds.type-filter.label"));
        roundsTypeFilter.setId("rounds-type-filter");
        roundsTypeFilter.setItems(RoundTypeFilter.values());
        roundsTypeFilter.setItemLabelGenerator(this::roundTypeFilterLabel);
        roundsTypeFilter.setValue(RoundTypeFilter.ALL);
        roundsTypeFilter.addValueChangeListener(e -> refreshRoundsGrid());
        topRow.add(roundsTypeFilter);

        tab.add(topRow);

        roundsGrid = new Grid<>(JudgingRound.class, false);
        roundsGrid.setId("rounds-grid");
        roundsGrid.addColumn(r -> roundTypeLabel(r.getType()))
                .setHeader(getTranslation("judging-admin.rounds.column.type"));
        roundsGrid.addColumn(JudgingRound::getName)
                .setHeader(getTranslation("judging-admin.rounds.column.name"));
        roundsGrid.addColumn(r -> formatCategory(r.getDivisionCategoryId()))
                .setHeader(getTranslation("judging-admin.rounds.column.category"));
        roundsGrid.addColumn(r -> {
                    if (r.getPhysicalTableId() == null) return "—";
                    return judgingService.findPhysicalTableById(r.getPhysicalTableId())
                            .map(app.meads.judging.PhysicalTable::getLabel).orElse("—");
                })
                .setHeader(getTranslation("judging-admin.rounds.column.physical-table"));
        roundsGrid.addColumn(r -> r.getStatus().name())
                .setHeader(getTranslation("judging-admin.rounds.column.status"));
        roundsGrid.addColumn(r -> r.getAssignments().size())
                .setHeader(getTranslation("judging-admin.rounds.column.judges"));
        roundsGrid.addColumn(r -> r.getScheduledDate() == null ? ""
                        : r.getScheduledDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                                .withLocale(getLocale())))
                .setHeader(getTranslation("judging-admin.rounds.column.scheduled"));
        roundsGrid.addComponentColumn(this::createRoundsActionsCell)
                .setHeader(getTranslation("judging-admin.rounds.column.actions"));

        refreshRoundsGrid();
        tab.add(roundsGrid);
        return tab;
    }

    private void refreshRoundsGrid() {
        if (roundsGrid == null) {
            return;
        }
        var allRounds = judgingService.findRoundsByJudgingId(judging.getId());
        var filterValue = roundsTypeFilter == null ? RoundTypeFilter.ALL : roundsTypeFilter.getValue();
        var filtered = allRounds.stream()
                .filter(r -> matchesRoundTypeFilter(r, filterValue))
                .toList();
        roundsGrid.setItems(filtered);
    }

    private boolean matchesRoundTypeFilter(JudgingRound round, RoundTypeFilter filter) {
        return switch (filter == null ? RoundTypeFilter.ALL : filter) {
            case ALL -> true;
            case SCORING -> round.getType() == RoundType.SCORING;
            case MEDAL -> round.getType() == RoundType.MEDAL;
        };
    }

    private String roundTypeFilterLabel(RoundTypeFilter f) {
        return switch (f) {
            case ALL -> getTranslation("judging-admin.rounds.type-filter.all");
            case SCORING -> getTranslation("judging-admin.rounds.type.scoring");
            case MEDAL -> getTranslation("judging-admin.rounds.type.medal");
        };
    }

    private String roundTypeLabel(RoundType type) {
        return switch (type) {
            case SCORING -> getTranslation("judging-admin.rounds.type.scoring");
            case MEDAL -> getTranslation("judging-admin.rounds.type.medal");
        };
    }

    /** Package-public for tests — opened by the "+ Add Round" toolbar button on the Rounds tab. */
    public void openAddRoundDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("judging-admin.rounds.add"));

        var form = new VerticalLayout();
        form.setPadding(false);

        var typeSelect = new Select<RoundType>();
        typeSelect.setId("add-round-type");
        typeSelect.setLabel(getTranslation("judging-admin.rounds.column.type"));
        typeSelect.setItems(RoundType.values());
        typeSelect.setItemLabelGenerator(this::roundTypeLabel);
        typeSelect.setValue(RoundType.SCORING);
        typeSelect.setWidthFull();

        var nameField = new TextField(getTranslation("judging-admin.tables.dialog.name"));
        nameField.setId("add-round-name");
        nameField.setWidthFull();
        nameField.setMaxLength(120);

        var categorySelect = new Select<DivisionCategory>();
        categorySelect.setId("add-round-category");
        categorySelect.setLabel(getTranslation("judging-admin.tables.dialog.category"));
        categorySelect.setWidthFull();
        var categories = competitionService.findJudgingCategories(division.getId());
        categorySelect.setItems(categories);
        categorySelect.setItemLabelGenerator(c -> c == null ? "" : c.getCode() + " — " + c.getName());

        var physicalTableSelect = new Select<app.meads.judging.PhysicalTable>();
        physicalTableSelect.setId("add-round-physical-table");
        physicalTableSelect.setLabel(getTranslation("judging-admin.tables.dialog.physical-table"));
        physicalTableSelect.setWidthFull();
        var physicalTables = judgingService.findPhysicalTablesByDivision(division.getId());
        physicalTableSelect.setItems(physicalTables);
        physicalTableSelect.setItemLabelGenerator(pt -> pt == null ? "" : pt.getLabel());
        if (physicalTables.isEmpty()) {
            physicalTableSelect.setHelperText(getTranslation("judging-admin.tables.dialog.physical-table.empty"));
        }

        var datePicker = new DatePicker(getTranslation("judging-admin.tables.dialog.scheduled"));
        datePicker.setWidthFull();

        form.add(typeSelect, nameField, categorySelect, physicalTableSelect, datePicker);
        dialog.add(form);

        // MEDAL rounds derive their name from the category and don't need an admin-entered name.
        typeSelect.addValueChangeListener(e -> nameField.setVisible(e.getValue() == RoundType.SCORING));

        var saveButton = new Button(getTranslation("button.save"), e -> {
            var type = typeSelect.getValue();
            if (type == RoundType.SCORING) {
                if (nameField.getValue() == null || nameField.getValue().isBlank()) {
                    nameField.setInvalid(true);
                    nameField.setErrorMessage(getTranslation("judging-admin.tables.dialog.name.error"));
                    return;
                }
            }
            if (categorySelect.getValue() == null) {
                categorySelect.setInvalid(true);
                categorySelect.setErrorMessage(getTranslation("judging-admin.tables.dialog.category.error"));
                return;
            }
            if (physicalTableSelect.getValue() == null) {
                physicalTableSelect.setInvalid(true);
                physicalTableSelect.setErrorMessage(getTranslation("judging-admin.tables.dialog.physical-table.error"));
                return;
            }
            try {
                JudgingRound created;
                if (type == RoundType.MEDAL) {
                    created = judgingService.createMedalRound(judging.getId(),
                            categorySelect.getValue().getId(), currentUserId);
                } else {
                    created = judgingService.createRound(judging.getId(), nameField.getValue().trim(),
                            categorySelect.getValue().getId(), datePicker.getValue(), currentUserId);
                }
                judgingService.assignRoundToPhysicalTable(created.getId(),
                        physicalTableSelect.getValue().getId(), currentUserId);
                dialog.close();
                refreshRoundsGrid();
                Notification.show(getTranslation("judging-admin.rounds.added"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.setDisableOnClick(true);

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());

        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private VerticalLayout createResultsTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var completeRounds = judgingService.findRoundsByJudgingId(judging.getId()).stream()
                .filter(r -> r.getStatus() == JudgingRoundStatus.COMPLETE)
                .toList();

        var resultsGrid = new Grid<>(JudgingRound.class, false);
        resultsGrid.setId("results-grid");
        resultsGrid.addColumn(r -> roundTypeLabel(r.getType()))
                .setHeader(getTranslation("judging-admin.rounds.column.type"));
        resultsGrid.addColumn(JudgingRound::getName)
                .setHeader(getTranslation("judging-admin.rounds.column.name"));
        resultsGrid.addColumn(r -> formatCategory(r.getDivisionCategoryId()))
                .setHeader(getTranslation("judging-admin.rounds.column.category"));
        resultsGrid.addColumn(r -> {
                    if (r.getPhysicalTableId() == null) return "—";
                    return judgingService.findPhysicalTableById(r.getPhysicalTableId())
                            .map(app.meads.judging.PhysicalTable::getLabel).orElse("—");
                })
                .setHeader(getTranslation("judging-admin.rounds.column.physical-table"));
        resultsGrid.addColumn(this::formatRoundOutcome)
                .setHeader(getTranslation("judging-admin.results.column.outcome"));
        resultsGrid.addComponentColumn(this::createRoundsActionsCell)
                .setHeader(getTranslation("judging-admin.rounds.column.actions"));
        resultsGrid.setItems(completeRounds);

        if (completeRounds.isEmpty()) {
            tab.add(new Span(getTranslation("judging-admin.results.empty")));
        }
        tab.add(resultsGrid);
        return tab;
    }

    private String formatRoundOutcome(JudgingRound round) {
        if (round.getType() == RoundType.MEDAL) {
            var awards = judgingService.findMedalAwardsForCategory(round.getDivisionCategoryId());
            long gold = awards.stream().filter(a -> a.getMedal() == Medal.GOLD).count();
            long silver = awards.stream().filter(a -> a.getMedal() == Medal.SILVER).count();
            long bronze = awards.stream().filter(a -> a.getMedal() == Medal.BRONZE).count();
            long withheld = awards.stream().filter(a -> a.getMedal() == null).count();
            return "G:" + gold + " S:" + silver + " B:" + bronze + " W:" + withheld;
        }
        long submitted = scoresheetService.countByRoundIdAndStatus(round.getId(), ScoresheetStatus.SUBMITTED);
        return getTranslation("judging-admin.results.outcome.scoresheets", submitted);
    }

    private HorizontalLayout createRoundsActionsCell(JudgingRound round) {
        var openButton = new Button(new Icon(VaadinIcon.ARROW_RIGHT));
        openButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        openButton.setTooltipText(getTranslation("judging-admin.rounds.action.open"));
        openButton.addClickListener(e -> {
            String url = round.getType() == RoundType.MEDAL
                    ? "competitions/" + compShortName + "/divisions/" + divShortName
                        + "/medal-rounds/" + round.getDivisionCategoryId()
                    : "competitions/" + compShortName + "/divisions/" + divShortName
                        + "/tables/" + round.getId();
            com.vaadin.flow.component.UI.getCurrent().navigate(url);
        });

        if (round.getType() == RoundType.MEDAL) {
            return new HorizontalLayout(openButton);
        }

        var editButton = new Button(new Icon(VaadinIcon.EDIT));
        editButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        editButton.setTooltipText(getTranslation("judging-admin.tables.action.edit"));
        editButton.addClickListener(e -> openEditTableDialog(round));

        var startButton = new Button(new Icon(VaadinIcon.PLAY));
        startButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        startButton.setEnabled(round.getStatus() == JudgingRoundStatus.PENDING
                || round.getStatus() == JudgingRoundStatus.READY);
        startButton.setTooltipText(getTranslation("judging-admin.tables.action.start"));
        startButton.addClickListener(e -> openStartTableDialog(round));

        var assignJudgesButton = new Button(new Icon(VaadinIcon.USERS));
        assignJudgesButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        assignJudgesButton.setTooltipText(getTranslation("judging-admin.tables.action.assign-judges"));
        assignJudgesButton.addClickListener(e -> openAssignJudgesDialog(round));

        var deleteButton = new Button(new Icon(VaadinIcon.TRASH));
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        boolean canDelete = round.getStatus() == JudgingRoundStatus.PENDING
                && round.getAssignments().isEmpty();
        deleteButton.setEnabled(canDelete);
        deleteButton.setTooltipText(canDelete
                ? getTranslation("judging-admin.tables.action.delete")
                : getTranslation("judging-admin.tables.action.delete.blocked"));
        deleteButton.addClickListener(e -> openDeleteTableDialog(round));

        return new HorizontalLayout(editButton, startButton, assignJudgesButton, deleteButton, openButton);
    }

    private VerticalLayout createBosTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);
        tab.setId("bos-tab");

        if (judging.getPhase() == JudgingPhase.NOT_STARTED) {
            tab.add(new Span(getTranslation("judging-admin.bos.disabled")));
            return tab;
        }

        tab.add(createBosHeader());
        tab.add(createManagePlacementsLink());
        tab.add(createBosCandidatesSection());
        tab.add(createBosPlacementsSection());
        return tab;
    }

    private Anchor createManagePlacementsLink() {
        return new Anchor(
                "competitions/" + compShortName + "/divisions/" + divShortName + "/bos",
                getTranslation("judging-admin.bos.manage-placements"));
    }

    private HorizontalLayout createBosHeader() {
        var header = new HorizontalLayout();
        header.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        var phaseBadge = new Span(getTranslation("judging-admin.bos.phase." + judging.getPhase().name()));
        phaseBadge.setId("bos-phase-badge");
        header.add(phaseBadge);

        header.add(new Span(getTranslation("judging-admin.bos.places", division.getBosPlaces())));

        var phase = judging.getPhase();
        if (phase == JudgingPhase.ACTIVE) {
            var startButton = new Button(getTranslation("judging-admin.bos.action.start"),
                    e -> openStartBosDialog());
            startButton.setId("bos-start-button");
            startButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            startButton.setEnabled(allCategoryRoundsComplete());
            if (!allCategoryRoundsComplete()) {
                startButton.setTooltipText(getTranslation("judging-admin.bos.action.start.disabled-tooltip"));
            }
            header.add(startButton);
        } else if (phase == JudgingPhase.BOS) {
            var finalizeButton = new Button(getTranslation("judging-admin.bos.action.finalize"),
                    e -> openFinalizeBosDialog());
            finalizeButton.setId("bos-finalize-button");
            finalizeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

            var resetButton = new Button(getTranslation("judging-admin.bos.action.reset"),
                    e -> openResetBosDialog());
            resetButton.setId("bos-reset-button");
            resetButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
            var placementsExist = !judgingService.findBosPlacementsForDivision(
                    division.getId(), currentUserId).isEmpty();
            resetButton.setEnabled(!placementsExist);
            if (placementsExist) {
                resetButton.setTooltipText(getTranslation("judging-admin.bos.action.reset.disabled-tooltip"));
            }
            header.add(finalizeButton, resetButton);
        } else if (phase == JudgingPhase.COMPLETE) {
            var reopenButton = new Button(getTranslation("judging-admin.bos.action.reopen"),
                    e -> openReopenBosDialog());
            reopenButton.setId("bos-reopen-button");
            reopenButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            header.add(reopenButton);
        }
        return header;
    }

    private boolean allCategoryRoundsComplete() {
        var configs = judgingService.findCategoryConfigsForDivision(division.getId(), currentUserId);
        return !configs.isEmpty()
                && configs.stream().allMatch(c -> c.getMedalRoundStatus() == MedalRoundStatus.COMPLETE);
    }

    private VerticalLayout createBosCandidatesSection() {
        var section = new VerticalLayout();
        section.setPadding(false);
        section.add(new Span(getTranslation("judging-admin.bos.candidates")));

        var goldAwards = judgingService.findGoldMedalAwardsForDivision(
                division.getId(), currentUserId);
        if (goldAwards.isEmpty()) {
            var empty = new Span(getTranslation("judging-admin.bos.candidates.empty"));
            empty.setId("bos-candidates-empty");
            section.add(empty);
            return section;
        }

        var candidatesGrid = new Grid<MedalAward>(MedalAward.class, false);
        candidatesGrid.setId("bos-candidates-grid");
        candidatesGrid.addColumn(this::formatEntryCode)
                .setHeader(getTranslation("judging-admin.bos.candidates.column.entry"));
        candidatesGrid.addColumn(this::formatEntryMeadName)
                .setHeader(getTranslation("judging-admin.bos.candidates.column.mead-name"));
        candidatesGrid.addColumn(a -> formatCategory(a.getFinalCategoryId()))
                .setHeader(getTranslation("judging-admin.bos.candidates.column.category"));
        candidatesGrid.setItems(goldAwards);
        section.add(candidatesGrid);
        return section;
    }

    private String formatEntryCode(MedalAward award) {
        try {
            return entryService.findEntryById(award.getEntryId()).getEntryCode();
        } catch (Exception e) {
            return "?";
        }
    }

    private String formatEntryMeadName(MedalAward award) {
        try {
            return entryService.findEntryById(award.getEntryId()).getMeadName();
        } catch (Exception e) {
            return "?";
        }
    }

    private VerticalLayout createBosPlacementsSection() {
        var section = new VerticalLayout();
        section.setPadding(false);
        section.add(new Span(getTranslation("judging-admin.bos.placements")));

        var placements = judgingService.findBosPlacementsForDivision(
                division.getId(), currentUserId);
        var rows = new java.util.ArrayList<BosPlacementRow>();
        for (int p = 1; p <= division.getBosPlaces(); p++) {
            final int place = p;
            var match = placements.stream()
                    .filter(bp -> bp.getPlace() == place)
                    .findFirst();
            rows.add(new BosPlacementRow(place, match.orElse(null)));
        }

        var placementsGrid = new Grid<BosPlacementRow>(BosPlacementRow.class, false);
        placementsGrid.setId("bos-placements-grid");
        placementsGrid.addColumn(BosPlacementRow::place)
                .setHeader(getTranslation("judging-admin.bos.placements.column.place"));
        placementsGrid.addColumn(this::formatPlacementEntry)
                .setHeader(getTranslation("judging-admin.bos.placements.column.entry"));
        placementsGrid.addColumn(this::formatPlacementCategory)
                .setHeader(getTranslation("judging-admin.bos.placements.column.category"));
        placementsGrid.addColumn(this::formatPlacementAwardedBy)
                .setHeader(getTranslation("judging-admin.bos.placements.column.awarded-by"));
        placementsGrid.addComponentColumn(this::createPlacementActionsCell)
                .setHeader(getTranslation("judging-admin.bos.placements.column.actions"));
        placementsGrid.setItems(rows);
        section.add(placementsGrid);
        return section;
    }

    private String formatPlacementEntry(BosPlacementRow row) {
        if (row.placement() == null) {
            return getTranslation("judging-admin.bos.placements.not-assigned");
        }
        try {
            var entry = entryService.findEntryById(row.placement().getEntryId());
            return entry.getEntryCode() + " — " + entry.getMeadName();
        } catch (Exception e) {
            return "?";
        }
    }

    private String formatPlacementCategory(BosPlacementRow row) {
        if (row.placement() == null) {
            return "";
        }
        try {
            var entry = entryService.findEntryById(row.placement().getEntryId());
            return formatCategory(entry.getFinalCategoryId());
        } catch (Exception e) {
            return "?";
        }
    }

    private String formatPlacementAwardedBy(BosPlacementRow row) {
        if (row.placement() == null) {
            return "";
        }
        try {
            return userService.findById(row.placement().getAwardedBy()).getName();
        } catch (Exception e) {
            return "?";
        }
    }

    private HorizontalLayout createPlacementActionsCell(BosPlacementRow row) {
        var actions = new HorizontalLayout();
        actions.setPadding(false);
        actions.setSpacing(false);
        boolean phaseBos = judging.getPhase() == JudgingPhase.BOS;

        if (row.placement() == null) {
            var addButton = new Button(new Icon(VaadinIcon.PLUS));
            addButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            addButton.setEnabled(phaseBos);
            addButton.setTooltipText(getTranslation("judging-admin.bos.placements.action.add"));
            addButton.addClickListener(e -> openAddBosPlacementDialog(row.place()));
            actions.add(addButton);
            return actions;
        }

        var editButton = new Button(new Icon(VaadinIcon.EDIT));
        editButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        editButton.setEnabled(phaseBos);
        editButton.setTooltipText(getTranslation("judging-admin.bos.placements.action.edit"));
        editButton.addClickListener(e -> openEditBosPlacementDialog(row.placement()));

        var deleteButton = new Button(new Icon(VaadinIcon.TRASH));
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        deleteButton.setEnabled(phaseBos);
        deleteButton.setTooltipText(getTranslation("judging-admin.bos.placements.action.delete"));
        deleteButton.addClickListener(e -> openDeleteBosPlacementDialog(row.placement()));

        actions.add(editButton, deleteButton);
        return actions;
    }

    public void openStartBosDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("judging-admin.bos.action.start.confirm.title"));
        dialog.add(new Span(getTranslation("judging-admin.bos.action.start.confirm.body")));
        var confirm = new Button(getTranslation("judging-admin.bos.action.start"), e -> {
            try {
                judgingService.startBos(division.getId(), currentUserId);
                dialog.close();
                refreshView();
                Notification.show(getTranslation("judging-admin.bos.started"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        confirm.setDisableOnClick(true);
        var cancel = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    public void openFinalizeBosDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("judging-admin.bos.action.finalize.confirm.title"));
        dialog.add(new Span(getTranslation("judging-admin.bos.action.finalize.confirm.body")));
        var confirm = new Button(getTranslation("judging-admin.bos.action.finalize"), e -> {
            try {
                judgingService.completeBos(division.getId(), currentUserId);
                dialog.close();
                refreshView();
                Notification.show(getTranslation("judging-admin.bos.finalized"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        confirm.setDisableOnClick(true);
        var cancel = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    public void openReopenBosDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("judging-admin.bos.action.reopen.confirm.title"));
        dialog.add(new Span(getTranslation("judging-admin.bos.action.reopen.confirm.body")));
        var confirm = new Button(getTranslation("judging-admin.bos.action.reopen"), e -> {
            try {
                judgingService.reopenBos(division.getId(), currentUserId);
                dialog.close();
                refreshView();
                Notification.show(getTranslation("judging-admin.bos.reopened"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        confirm.setDisableOnClick(true);
        var cancel = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    public void openResetBosDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("judging-admin.bos.action.reset.confirm.title"));
        dialog.add(new Span(getTranslation("judging-admin.bos.action.reset.confirm.body")));
        var confirm = new Button(getTranslation("judging-admin.bos.action.reset"), e -> {
            try {
                judgingService.resetBos(division.getId(), currentUserId);
                dialog.close();
                refreshView();
                Notification.show(getTranslation("judging-admin.bos.reset"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        confirm.setDisableOnClick(true);
        var cancel = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    public void openAddBosPlacementDialog(int place) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("judging-admin.bos.placements.dialog.add.title", place));

        var goldAwards = judgingService.findGoldMedalAwardsForDivision(
                division.getId(), currentUserId);
        var existingPlacements = judgingService.findBosPlacementsForDivision(
                division.getId(), currentUserId);
        var placedEntryIds = existingPlacements.stream()
                .map(BosPlacement::getEntryId)
                .collect(Collectors.toSet());
        var unplaced = goldAwards.stream()
                .filter(a -> !placedEntryIds.contains(a.getEntryId()))
                .toList();

        var entrySelect = new Select<MedalAward>();
        entrySelect.setId("bos-add-entry-select");
        entrySelect.setLabel(getTranslation("judging-admin.bos.placements.dialog.entry"));
        entrySelect.setItems(unplaced);
        entrySelect.setItemLabelGenerator(a -> a == null ? "" :
                formatEntryCode(a) + " — " + formatEntryMeadName(a));
        entrySelect.setWidthFull();

        dialog.add(entrySelect);

        var save = new Button(getTranslation("button.save"), e -> {
            var selected = entrySelect.getValue();
            if (selected == null) {
                entrySelect.setInvalid(true);
                entrySelect.setErrorMessage(getTranslation("judging-admin.bos.placements.dialog.entry.error"));
                return;
            }
            try {
                judgingService.recordBosPlacement(division.getId(),
                        selected.getEntryId(), place, currentUserId);
                dialog.close();
                refreshView();
                Notification.show(getTranslation("judging-admin.bos.placements.added"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.setDisableOnClick(true);
        var cancel = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    public void openEditBosPlacementDialog(BosPlacement placement) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("judging-admin.bos.placements.dialog.edit.title",
                placement.getPlace()));

        var placeField = new IntegerField(getTranslation("judging-admin.bos.placements.dialog.place"));
        placeField.setId("bos-edit-place-field");
        placeField.setMin(1);
        placeField.setMax(division.getBosPlaces());
        placeField.setValue(placement.getPlace());
        placeField.setStepButtonsVisible(true);
        placeField.setWidthFull();
        dialog.add(placeField);

        var save = new Button(getTranslation("button.save"), e -> {
            var newPlace = placeField.getValue();
            if (newPlace == null || newPlace < 1) {
                placeField.setInvalid(true);
                placeField.setErrorMessage(getTranslation("judging-admin.bos.placements.dialog.place.error"));
                return;
            }
            try {
                judgingService.updateBosPlacement(placement.getId(), newPlace, currentUserId);
                dialog.close();
                refreshView();
                Notification.show(getTranslation("judging-admin.bos.placements.updated"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.setDisableOnClick(true);
        var cancel = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    public void openDeleteBosPlacementDialog(BosPlacement placement) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("judging-admin.bos.placements.dialog.delete.title",
                placement.getPlace()));
        dialog.add(new Span(getTranslation("judging-admin.bos.placements.dialog.delete.body")));
        var confirm = new Button(getTranslation("button.delete"), e -> {
            try {
                judgingService.deleteBosPlacement(placement.getId(), currentUserId);
                dialog.close();
                refreshView();
                Notification.show(getTranslation("judging-admin.bos.placements.deleted"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        confirm.setDisableOnClick(true);
        var cancel = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    private void refreshView() {
        judging = judgingService.ensureJudgingExists(division.getId());
        beforeEnterRefresh();
    }

    public record BosPlacementRow(int place, BosPlacement placement) {
    }

    private UUID getCurrentUserId() {
        return authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(userDetails -> userService.findByEmail(userDetails.getUsername()).getId())
                .orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }
}
