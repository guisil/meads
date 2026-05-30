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
import app.meads.judging.JudgingPhase;
import app.meads.judging.BosPlacement;
import app.meads.judging.Judging;
import app.meads.judging.JudgingService;
import app.meads.judging.JudgingRound;
import app.meads.judging.JudgingRoundStatus;
import app.meads.judging.RoundType;
import app.meads.judging.MedalAward;
import app.meads.judging.MedalRoundMode;
import app.meads.judging.ScoresheetService;
import app.meads.judging.ScoresheetStatus;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
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
    private CheckboxGroup<JudgingRoundStatus> roundsStatusFilter;
    private Grid<JudgingRound> resultsGrid;
    private ComboBox<RoundTypeFilter> resultsTypeFilter;
    private Span resultsEmptyCaption;

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

        if (division.getStatus().ordinal() < DivisionStatus.REGISTRATION_CLOSED.ordinal()) {
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
        tabSheet.setSelectedIndex(computeDefaultTabIndex());
        return tabSheet;
    }

    /**
     * Default tab depends on division state, so the admin lands on the most-
     * useful tab on each visit:
     * <ul>
     *   <li>No physical tables yet → Tables (set them up first).</li>
     *   <li>Tables exist + all rounds COMPLETE → Results (review final outcomes).</li>
     *   <li>Otherwise → Rounds (the workhorse tab during judging).</li>
     * </ul>
     * BOS is never the default — admin reaches it deliberately once deliberation
     * begins.
     */
    private int computeDefaultTabIndex() {
        var physicalTables = judgingService.findPhysicalTablesByDivision(division.getId());
        if (physicalTables.isEmpty()) {
            return 0; // Tables
        }
        var rounds = judgingService.findRoundsByJudgingId(judging.getId());
        if (!rounds.isEmpty()
                && rounds.stream().allMatch(r -> r.getStatus() == JudgingRoundStatus.COMPLETE)) {
            return 2; // Results
        }
        return 1; // Rounds
    }

    private Grid<app.meads.judging.PhysicalTable> physicalTablesGrid;

    private VerticalLayout createPhysicalTablesTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        if (competitionService.findCompetitionById(competition.getId()).isSharedTables()) {
            var banner = new Span(getTranslation("judging-admin.physical-tables.shared-banner"));
            banner.setId("physical-tables-shared-banner");
            banner.getStyle().set("color", "var(--lumo-primary-text-color)")
                    .set("font-weight", "600");
            tab.add(banner);
        }

        var addButton = new Button(getTranslation("judging-admin.physical-tables.add"),
                e -> openAddPhysicalTableDialog());
        addButton.setId("add-physical-table-button");
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        tab.add(addButton);

        physicalTablesGrid = new Grid<>(app.meads.judging.PhysicalTable.class, false);
        physicalTablesGrid.setId("physical-tables-grid");
        physicalTablesGrid.setAllRowsVisible(true);
        physicalTablesGrid.addColumn(app.meads.judging.PhysicalTable::getLabel)
                .setHeader(getTranslation("judging-admin.physical-tables.column.label"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        physicalTablesGrid.addComponentColumn(this::physicalTableActions)
                .setHeader(getTranslation("judging-admin.physical-tables.column.actions"))
                .setResizable(true).setAutoWidth(true).setFlexGrow(0);
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

    private String formatCategoryCode(UUID divisionCategoryId) {
        if (divisionCategoryId == null) {
            return "";
        }
        return categoriesById().getOrDefault(divisionCategoryId, null) instanceof DivisionCategory dc
                ? dc.getCode()
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

        var physicalTableSelect = new Select<app.meads.judging.PhysicalTable>();
        physicalTableSelect.setId("edit-table-physical-table");
        physicalTableSelect.setLabel(getTranslation("judging-admin.tables.dialog.physical-table"));
        physicalTableSelect.setWidthFull();
        var physicalTables = judgingService.findPhysicalTablesByDivision(division.getId());
        physicalTableSelect.setItems(physicalTables);
        physicalTableSelect.setItemLabelGenerator(pt -> pt == null ? "" : pt.getLabel());
        if (table.getPhysicalTableId() != null) {
            physicalTables.stream()
                    .filter(pt -> pt.getId().equals(table.getPhysicalTableId()))
                    .findFirst()
                    .ifPresent(physicalTableSelect::setValue);
        }
        boolean tableReassignable = table.getStatus() == JudgingRoundStatus.PENDING
                || table.getStatus() == JudgingRoundStatus.READY;
        physicalTableSelect.setEnabled(tableReassignable);
        if (!tableReassignable) {
            physicalTableSelect.setHelperText(
                    getTranslation("judging-admin.tables.dialog.physical-table.locked"));
        }

        var datePicker = new DateTimePicker(getTranslation("judging-admin.tables.dialog.scheduled"));
        datePicker.setId("edit-table-scheduled");
        datePicker.setWidthFull();
        datePicker.setValue(table.getScheduledAt());

        form.add(nameField, physicalTableSelect, datePicker);
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
                var selectedTableId = physicalTableSelect.getValue() == null ? null
                        : physicalTableSelect.getValue().getId();
                if (tableReassignable && selectedTableId != null
                        && !java.util.Objects.equals(selectedTableId, table.getPhysicalTableId())) {
                    judgingService.assignRoundToPhysicalTable(table.getId(), selectedTableId, currentUserId);
                }
                if (!java.util.Objects.equals(datePicker.getValue(), table.getScheduledAt())) {
                    judgingService.updateRoundScheduledAt(table.getId(), datePicker.getValue(), currentUserId);
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
                refreshRoundsGrid();
                Notification.show(getTranslation("judging-admin.tables.started"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            } finally {
                dialog.close();
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
        dialog.setWidth("900px");

        var availableJudges = competitionService.findUsersByRoleInCompetition(
                competition.getId(), CompetitionRole.JUDGE);
        var entriesInCategory = entryService.findEntriesByFinalCategoryId(table.getDivisionCategoryId());
        var currentlyAssigned = table.getAssignments().stream()
                .map(a -> a.getJudgeUserId())
                .collect(Collectors.toSet());

        var judgesGrid = new Grid<User>(User.class, false);
        judgesGrid.setId("assign-judges-grid");
        judgesGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        judgesGrid.setAllRowsVisible(true);
        judgesGrid.addColumn(User::getName)
                .setHeader(getTranslation("judging-admin.tables.assign.column.name"))
                .setResizable(true).setSortable(true).setAutoWidth(true).setFlexGrow(0);
        judgesGrid.addColumn(u -> u.getMeaderyName() == null ? "" : u.getMeaderyName())
                .setHeader(getTranslation("judging-admin.tables.assign.column.meadery"))
                .setResizable(true).setSortable(true).setAutoWidth(true).setFlexGrow(0);
        judgesGrid.addColumn(u -> u.getCountry() == null ? "" : u.getCountry())
                .setHeader(getTranslation("judging-admin.tables.assign.column.country"))
                .setResizable(true).setSortable(true).setAutoWidth(true).setFlexGrow(0);
        // COI column carries the longest text — let it absorb all remaining width
        // instead of equal-sharing with the data columns.
        judgesGrid.addComponentColumn(judge -> coiChips(judge, entriesInCategory))
                .setHeader(getTranslation("judging-admin.tables.assign.column.coi"))
                .setResizable(true).setFlexGrow(1);
        judgesGrid.setItems(availableJudges);
        availableJudges.stream()
                .filter(j -> currentlyAssigned.contains(j.getId()))
                .forEach(j -> judgesGrid.asMultiSelect().select(j));

        judgesGrid.asMultiSelect().addSelectionListener(event -> {
            for (var judge : event.getAddedSelection()) {
                findFirstHardCoiEntry(judge, entriesInCategory).ifPresent(entry -> {
                    judgesGrid.asMultiSelect().deselect(judge);
                    Notification.show(getTranslation("error.coi.assign-hard-block",
                                    judge.getName(),
                                    String.valueOf(entry.getEntryNumber())))
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                });
            }
        });

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

    /**
     * Per-round entry assignment dialog. Lets the admin pick which RECEIVED
     * entries (filtered to the round's category) belong to this scoring round.
     * Enforces 1:1 — an entry on another scoring round can't also be selected
     * here without first being unassigned. Available only at PENDING — once
     * the round starts, entries are locked.
     */
    public void openAssignEntriesDialog(JudgingRound round) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("judging-admin.tables.assign-entries.dialog.title", round.getName()));
        dialog.setWidth("780px");

        var allEntries = entryService.findEntriesByFinalCategoryId(round.getDivisionCategoryId()).stream()
                .filter(e -> e.getStatus() == app.meads.entry.EntryStatus.RECEIVED)
                .toList();

        // SCORE_BASED medal rounds own every RECEIVED entry automatically — show a
        // read-only preview + a Sync button rather than a multi-select (manual
        // unassign is rejected by the service for these rounds).
        if (round.getType() == RoundType.MEDAL
                && round.getMedalMode() == MedalRoundMode.SCORE_BASED) {
            var content = new VerticalLayout();
            content.setPadding(false);
            content.add(new Span(getTranslation("medal-round.assign-entries.helper.score-based")));
            if (allEntries.isEmpty()) {
                content.add(new Span(getTranslation("medal-round.assign-entries.empty.score-based")));
            }
            var preview = new Grid<>(app.meads.entry.Entry.class, false);
            preview.setId("assign-entries-grid");
            preview.setSelectionMode(Grid.SelectionMode.NONE);
            preview.setAllRowsVisible(true);
            preview.addColumn(e -> e.getEntryCode() + " — " + e.getMeadName())
                    .setHeader(getTranslation("judging-admin.tables.assign-entries.column.entry"))
                    .setResizable(true).setSortable(true).setAutoWidth(true);
            preview.addColumn(e -> {
                        var owner = userService.findById(e.getUserId());
                        return owner.getMeaderyName() == null ? "" : owner.getMeaderyName();
                    })
                    .setHeader(getTranslation("judging-admin.tables.assign-entries.column.meadery"))
                    .setResizable(true).setSortable(true).setAutoWidth(true);
            preview.setItems(allEntries);
            content.add(preview);
            dialog.add(content);

            var syncButton = new Button(getTranslation("medal-round.assign-entries.sync"), e -> {
                try {
                    judgingService.syncScoreBasedMedalRoundEntries(round.getId(), currentUserId);
                    dialog.close();
                    refreshRoundsGrid();
                    Notification.show(getTranslation("judging-admin.tables.assign-entries.saved"))
                            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                } catch (BusinessRuleException ex) {
                    Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            });
            syncButton.setId("assign-entries-sync");
            syncButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            syncButton.setDisableOnClick(true);
            var cancelSync = new Button(getTranslation("button.cancel"), e -> dialog.close());
            dialog.getFooter().add(cancelSync, syncButton);
            dialog.open();
            return;
        }

        var roundsInDivision = judgingService.findRoundsByJudgingId(judging.getId()).stream()
                .filter(r -> r.getType() == RoundType.SCORING)
                .toList();
        var currentAssignment = new java.util.HashMap<UUID, JudgingRound>();
        for (var r : roundsInDivision) {
            for (var entryId : r.getEntries()) {
                currentAssignment.put(entryId, r);
            }
        }

        var content = new VerticalLayout();
        content.setPadding(false);
        content.add(new Span(getTranslation("judging-admin.tables.assign-entries.helper")));

        if (allEntries.isEmpty()) {
            content.add(new Span(getTranslation("judging-admin.tables.assign-entries.empty")));
        }

        var entriesGrid = new Grid<>(app.meads.entry.Entry.class, false);
        entriesGrid.setId("assign-entries-grid");
        entriesGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        entriesGrid.setAllRowsVisible(true);
        entriesGrid.addColumn(e -> e.getEntryCode() + " — " + e.getMeadName())
                .setHeader(getTranslation("judging-admin.tables.assign-entries.column.entry"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        entriesGrid.addColumn(e -> {
                    var owner = userService.findById(e.getUserId());
                    return owner.getMeaderyName() == null ? "" : owner.getMeaderyName();
                })
                .setHeader(getTranslation("judging-admin.tables.assign-entries.column.meadery"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        entriesGrid.addColumn(e -> {
                    var assignedRound = currentAssignment.get(e.getId());
                    if (assignedRound == null) {
                        return getTranslation("judging-admin.tables.assign-entries.current-round.unassigned");
                    }
                    return assignedRound.getName();
                })
                .setHeader(getTranslation("judging-admin.tables.assign-entries.column.current-round"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        entriesGrid.setItems(allEntries);
        allEntries.stream()
                .filter(e -> round.getEntries().contains(e.getId()))
                .forEach(e -> entriesGrid.asMultiSelect().select(e));

        content.add(entriesGrid);
        dialog.add(content);

        var saveButton = new Button(getTranslation("button.save"), e -> {
            var selected = entriesGrid.asMultiSelect().getSelectedItems().stream()
                    .map(app.meads.entry.Entry::getId)
                    .collect(Collectors.toSet());
            var current = round.getEntries();
            try {
                for (var entryId : selected) {
                    if (!current.contains(entryId)) {
                        judgingService.assignEntryToRound(round.getId(), entryId, currentUserId);
                    }
                }
                for (var entryId : current) {
                    if (!selected.contains(entryId)) {
                        judgingService.unassignEntryFromRound(round.getId(), entryId, currentUserId);
                    }
                }
                dialog.close();
                refreshRoundsGrid();
                Notification.show(getTranslation("judging-admin.tables.assign-entries.saved"))
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

    private java.util.Optional<Entry> findFirstHardCoiEntry(User judge, List<Entry> entries) {
        return entries.stream()
                .filter(e -> coiCheckService.check(judge.getId(), e.getId()).hardBlock())
                .findFirst();
    }

    private HorizontalLayout coiChips(User judge, List<Entry> entries) {
        var layout = new HorizontalLayout();
        layout.setSpacing(false);
        // Wrap so multiple badges stack vertically within the cell instead of
        // overflowing horizontally. Grid will still horizontally scroll if a
        // single badge is wider than the column allocation.
        layout.getStyle().set("flex-wrap", "wrap").set("gap", "var(--lumo-space-xs)");
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

        roundsStatusFilter = new CheckboxGroup<>(getTranslation("judging-admin.rounds.status-filter.label"));
        roundsStatusFilter.setId("rounds-status-filter");
        roundsStatusFilter.setItems(JudgingRoundStatus.values());
        roundsStatusFilter.setItemLabelGenerator(Enum::name);
        roundsStatusFilter.select(JudgingRoundStatus.values()); // all statuses shown by default
        roundsStatusFilter.addValueChangeListener(e -> refreshRoundsGrid());
        topRow.add(roundsStatusFilter);

        tab.add(topRow);

        roundsGrid = new Grid<>(JudgingRound.class, false);
        roundsGrid.setId("rounds-grid");
        roundsGrid.setAllRowsVisible(true);
        roundsGrid.addComponentColumn(this::roundTypeBadge)
                .setHeader(getTranslation("judging-admin.rounds.column.type"))
                .setComparator(java.util.Comparator.comparing(this::roundTypeBadgeLabel))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        roundsGrid.addColumn(JudgingRound::getName)
                .setHeader(getTranslation("judging-admin.rounds.column.name"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        roundsGrid.addColumn(r -> formatCategoryCode(r.getDivisionCategoryId()))
                .setHeader(getTranslation("judging-admin.rounds.column.category"))
                .setTooltipGenerator(r -> formatCategory(r.getDivisionCategoryId()))
                .setResizable(true).setSortable(true).setAutoWidth(true).setFlexGrow(0);
        roundsGrid.addColumn(r -> {
                    if (r.getPhysicalTableId() == null) return "—";
                    return judgingService.findPhysicalTableById(r.getPhysicalTableId())
                            .map(app.meads.judging.PhysicalTable::getLabel).orElse("—");
                })
                .setHeader(getTranslation("judging-admin.rounds.column.physical-table"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        roundsGrid.addColumn(r -> r.getStatus().name())
                .setHeader(getTranslation("judging-admin.rounds.column.status"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        roundsGrid.addColumn(r -> r.getAssignments().size())
                .setHeader(getTranslation("judging-admin.rounds.column.judges"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        roundsGrid.addColumn(r -> r.getEntries().size())
                .setHeader(getTranslation("judging-admin.rounds.column.entries"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        roundsGrid.addColumn(r -> r.getScheduledAt() == null ? ""
                        : r.getScheduledAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .setHeader(getTranslation("judging-admin.rounds.column.scheduled"))
                .setResizable(true).setSortable(true).setWidth("12em").setFlexGrow(0);
        roundsGrid.addComponentColumn(this::createRoundsActionsCell)
                .setHeader(getTranslation("judging-admin.rounds.column.actions"))
                .setResizable(true).setAutoWidth(true).setFlexGrow(0);

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
        // Empty status selection is treated as "no constraint" (show all) rather
        // than "show none", so an accidental clear doesn't blank the grid.
        var statuses = (roundsStatusFilter == null || roundsStatusFilter.getValue().isEmpty())
                ? java.util.EnumSet.allOf(JudgingRoundStatus.class)
                : roundsStatusFilter.getValue();
        var filtered = allRounds.stream()
                .filter(r -> matchesRoundTypeFilter(r, filterValue))
                .filter(r -> statuses.contains(r.getStatus()))
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

    /**
     * Colored Lumo badge for the unified Rounds grid Type column: a medal round
     * reads as "just another round" but its scoring mode is visible at a glance —
     * Scoring (contrast), Medal — Comparative (primary), Medal — Score-based (success).
     */
    private Span roundTypeBadge(JudgingRound round) {
        var badge = new Span(roundTypeBadgeLabel(round));
        String theme = switch (round.getType()) {
            case SCORING -> "badge contrast";
            case MEDAL -> round.getMedalMode() == MedalRoundMode.SCORE_BASED
                    ? "badge success" : "badge primary";
        };
        badge.getElement().getThemeList().add(theme);
        return badge;
    }

    private String roundTypeBadgeLabel(JudgingRound round) {
        if (round.getType() == RoundType.SCORING) {
            return getTranslation("judging-admin.rounds.type.scoring");
        }
        String mode = getTranslation(round.getMedalMode() == MedalRoundMode.SCORE_BASED
                ? "medal-round.mode.score-based" : "medal-round.mode.comparative");
        return getTranslation("judging-admin.rounds.type.medal") + " — " + mode;
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

        // Medal mode is chosen at create time (collapses the old post-create
        // header switch). Shown only when Type = MEDAL.
        var medalModeSelect = new Select<MedalRoundMode>();
        medalModeSelect.setId("add-round-medal-mode");
        medalModeSelect.setLabel(getTranslation("judging-admin.rounds.dialog.medal-mode"));
        medalModeSelect.setWidthFull();
        medalModeSelect.setItems(MedalRoundMode.values());
        medalModeSelect.setItemLabelGenerator(m -> getTranslation(m == MedalRoundMode.SCORE_BASED
                ? "medal-round.mode.score-based" : "medal-round.mode.comparative"));
        medalModeSelect.setValue(MedalRoundMode.COMPARATIVE);
        medalModeSelect.setVisible(false);

        var categorySelect = new Select<DivisionCategory>();
        categorySelect.setId("add-round-category");
        categorySelect.setLabel(getTranslation("judging-admin.tables.dialog.category"));
        categorySelect.setWidthFull();
        // Only leaf JUDGING categories — parents (e.g. M1) can't host scoresheets/medals.
        var categories = competitionService.findLeafJudgingCategories(division.getId());
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

        var datePicker = new DateTimePicker(getTranslation("judging-admin.tables.dialog.scheduled"));
        datePicker.setId("add-round-scheduled");
        datePicker.setWidthFull();

        form.add(typeSelect, nameField, medalModeSelect, categorySelect, physicalTableSelect, datePicker);
        dialog.add(form);

        // MEDAL rounds derive their name from the category (no admin-entered name)
        // and expose the medal-mode picker; SCORING rounds hide the mode picker.
        typeSelect.addValueChangeListener(e -> {
            boolean medal = e.getValue() == RoundType.MEDAL;
            nameField.setVisible(!medal);
            medalModeSelect.setVisible(medal);
        });

        var saveButton = new Button(getTranslation("button.save"));
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.setDisableOnClick(true);
        saveButton.addClickListener(e -> {
            var type = typeSelect.getValue();
            if (type == RoundType.SCORING) {
                if (nameField.getValue() == null || nameField.getValue().isBlank()) {
                    nameField.setInvalid(true);
                    nameField.setErrorMessage(getTranslation("judging-admin.tables.dialog.name.error"));
                    saveButton.setEnabled(true);
                    return;
                }
            }
            if (categorySelect.getValue() == null) {
                categorySelect.setInvalid(true);
                categorySelect.setErrorMessage(getTranslation("judging-admin.tables.dialog.category.error"));
                saveButton.setEnabled(true);
                return;
            }
            if (physicalTableSelect.getValue() == null) {
                physicalTableSelect.setInvalid(true);
                physicalTableSelect.setErrorMessage(getTranslation("judging-admin.tables.dialog.physical-table.error"));
                saveButton.setEnabled(true);
                return;
            }
            try {
                JudgingRound created;
                if (type == RoundType.MEDAL) {
                    created = judgingService.createMedalRound(judging.getId(),
                            categorySelect.getValue().getId(), currentUserId);
                    if (medalModeSelect.getValue() != null
                            && created.getMedalMode() != medalModeSelect.getValue()) {
                        judgingService.updateMedalRoundMode(created.getId(),
                                medalModeSelect.getValue(), currentUserId);
                    }
                    if (datePicker.getValue() != null) {
                        judgingService.updateRoundScheduledAt(created.getId(),
                                datePicker.getValue(), currentUserId);
                    }
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
                saveButton.setEnabled(true);
            }
        });

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());

        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private VerticalLayout createResultsTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var topRow = new HorizontalLayout();
        topRow.setDefaultVerticalComponentAlignment(Alignment.END);
        resultsTypeFilter = new ComboBox<>(getTranslation("judging-admin.rounds.type-filter.label"));
        resultsTypeFilter.setId("results-type-filter");
        resultsTypeFilter.setItems(RoundTypeFilter.values());
        resultsTypeFilter.setItemLabelGenerator(this::roundTypeFilterLabel);
        resultsTypeFilter.setValue(RoundTypeFilter.ALL);
        resultsTypeFilter.addValueChangeListener(e -> refreshResultsGrid());
        topRow.add(resultsTypeFilter);
        tab.add(topRow);

        resultsEmptyCaption = new Span(getTranslation("judging-admin.results.empty"));
        tab.add(resultsEmptyCaption);

        resultsGrid = new Grid<>(JudgingRound.class, false);
        resultsGrid.setId("results-grid");
        resultsGrid.setAllRowsVisible(true);
        resultsGrid.addColumn(r -> roundTypeLabel(r.getType()))
                .setHeader(getTranslation("judging-admin.rounds.column.type"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        resultsGrid.addColumn(JudgingRound::getName)
                .setHeader(getTranslation("judging-admin.rounds.column.name"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        resultsGrid.addColumn(r -> formatCategory(r.getDivisionCategoryId()))
                .setHeader(getTranslation("judging-admin.rounds.column.category"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        resultsGrid.addColumn(r -> {
                    if (r.getPhysicalTableId() == null) return "—";
                    return judgingService.findPhysicalTableById(r.getPhysicalTableId())
                            .map(app.meads.judging.PhysicalTable::getLabel).orElse("—");
                })
                .setHeader(getTranslation("judging-admin.rounds.column.physical-table"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        resultsGrid.addColumn(this::formatRoundOutcome)
                .setHeader(getTranslation("judging-admin.results.column.outcome"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        resultsGrid.addComponentColumn(this::createRoundsActionsCell)
                .setHeader(getTranslation("judging-admin.rounds.column.actions"))
                .setResizable(true).setAutoWidth(true).setFlexGrow(0);
        tab.add(resultsGrid);

        refreshResultsGrid();
        return tab;
    }

    private void refreshResultsGrid() {
        if (resultsGrid == null) {
            return;
        }
        var filterValue = resultsTypeFilter == null ? RoundTypeFilter.ALL : resultsTypeFilter.getValue();
        var filter = filterValue == null ? RoundTypeFilter.ALL : filterValue;
        var rounds = judgingService.findRoundsByJudgingId(judging.getId()).stream()
                .filter(r -> r.getStatus() == JudgingRoundStatus.COMPLETE)
                .filter(r -> switch (filter) {
                    case ALL -> true;
                    case SCORING -> r.getType() == RoundType.SCORING;
                    case MEDAL -> r.getType() == RoundType.MEDAL;
                })
                .toList();
        resultsGrid.setItems(rounds);
        boolean empty = rounds.isEmpty();
        if (resultsEmptyCaption != null) {
            resultsEmptyCaption.setVisible(empty);
        }
        resultsGrid.setVisible(!empty);
    }

    private String formatRoundOutcome(JudgingRound round) {
        if (round.getType() == RoundType.MEDAL) {
            // At most one of each medal per category, so show one glyph per slot:
            // the medal icon when awarded, 🚫 when that medal was not awarded.
            var awards = judgingService.findMedalAwardsForCategory(round.getDivisionCategoryId());
            boolean gold = awards.stream().anyMatch(a -> a.getMedal() == Medal.GOLD);
            boolean silver = awards.stream().anyMatch(a -> a.getMedal() == Medal.SILVER);
            boolean bronze = awards.stream().anyMatch(a -> a.getMedal() == Medal.BRONZE);
            return (gold ? "🥇" : "🚫")
                    + "   " + (silver ? "🥈" : "🚫")
                    + "   " + (bronze ? "🥉" : "🚫");
        }
        long submitted = scoresheetService.countByRoundIdAndStatus(round.getId(), ScoresheetStatus.SUBMITTED);
        return getTranslation("judging-admin.results.outcome.scoresheets", submitted);
    }

    /** Package-public for tests — the inline action set rendered per Rounds-grid row. */
    public HorizontalLayout createRoundsActionsCell(JudgingRound round) {
        var openButton = new Button(new Icon(VaadinIcon.ARROW_RIGHT));
        openButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        openButton.setTooltipText(getTranslation("judging-admin.rounds.action.open"));
        openButton.addClickListener(e -> {
            String url = round.getType() == RoundType.MEDAL
                    ? "competitions/" + compShortName + "/divisions/" + divShortName
                        + "/medal-rounds/" + round.getDivisionCategoryId()
                    : "competitions/" + compShortName + "/divisions/" + divShortName
                        + "/rounds/" + round.getId();
            com.vaadin.flow.component.UI.getCurrent().navigate(url);
        });

        // Unified action set for SCORING and MEDAL rounds alike (decision: a
        // medal round is just another round with a different scoring mode). Type-
        // specific behavior is folded into the shared dialogs / service calls:
        // Start uses startRound (handles both), Revert branches in the dialog
        // (medal clears awards), Open routes to MedalRoundView vs RoundView.
        boolean isMedal = round.getType() == RoundType.MEDAL;

        // Once a round is COMPLETE there's nothing left to edit (name / schedule)
        // and no point reassigning judges — disable both.
        boolean roundOpenForChanges = round.getStatus() != JudgingRoundStatus.COMPLETE;

        var editButton = new Button(new Icon(VaadinIcon.EDIT));
        editButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        editButton.setEnabled(roundOpenForChanges);
        editButton.addClickListener(e -> openEditTableDialog(round));
        var editWrapper = wrapWithTooltip(editButton, roundOpenForChanges
                ? getTranslation("judging-admin.tables.action.edit")
                : getTranslation("judging-admin.tables.action.edit.disabled"));

        boolean startEnabled = round.getStatus() == JudgingRoundStatus.PENDING
                || round.getStatus() == JudgingRoundStatus.READY;
        var startButton = new Button(new Icon(VaadinIcon.PLAY));
        startButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        startButton.setEnabled(startEnabled);
        startButton.addClickListener(e -> openStartTableDialog(round));
        var startWrapper = wrapWithTooltip(startButton, startEnabled
                ? getTranslation("judging-admin.tables.action.start")
                : getTranslation("judging-admin.tables.action.start.disabled"));

        var assignJudgesButton = new Button(new Icon(VaadinIcon.USERS));
        assignJudgesButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        assignJudgesButton.setEnabled(roundOpenForChanges);
        assignJudgesButton.addClickListener(e -> openAssignJudgesDialog(round));
        var assignJudgesWrapper = wrapWithTooltip(assignJudgesButton, roundOpenForChanges
                ? getTranslation("judging-admin.tables.action.assign-judges")
                : getTranslation("judging-admin.tables.action.assign-judges.disabled"));

        boolean entryAssignmentAllowed = round.getStatus() != JudgingRoundStatus.COMPLETE;
        var assignEntriesButton = new Button(new Icon(VaadinIcon.PACKAGE));
        assignEntriesButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        assignEntriesButton.setEnabled(entryAssignmentAllowed);
        assignEntriesButton.addClickListener(e -> openAssignEntriesDialog(round));
        var assignEntriesWrapper = wrapWithTooltip(assignEntriesButton, entryAssignmentAllowed
                ? getTranslation("judging-admin.tables.action.assign-entries")
                : getTranslation("judging-admin.tables.assign-entries.disabled-tooltip"));

        boolean canRevert = round.getStatus() == JudgingRoundStatus.ACTIVE;
        var revertButton = new Button(new Icon(VaadinIcon.BACKWARDS));
        revertButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        revertButton.setEnabled(canRevert);
        revertButton.addClickListener(e -> openRevertRoundDialog(round));
        var revertWrapper = wrapWithTooltip(revertButton, canRevert
                ? getTranslation("judging-admin.tables.action.revert")
                : getTranslation("judging-admin.tables.action.revert.blocked"));

        // Delete: PENDING + no judges; a medal round additionally must have no
        // medal awards recorded yet.
        boolean canDelete = round.getStatus() == JudgingRoundStatus.PENDING
                && round.getAssignments().isEmpty()
                && (!isMedal || judgingService.findMedalAwardsForCategory(
                        round.getDivisionCategoryId()).isEmpty());
        var deleteButton = new Button(new Icon(VaadinIcon.TRASH));
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        deleteButton.setEnabled(canDelete);
        deleteButton.addClickListener(e -> openDeleteTableDialog(round));
        var deleteWrapper = wrapWithTooltip(deleteButton, canDelete
                ? getTranslation("judging-admin.tables.action.delete")
                : getTranslation(isMedal
                        ? "judging-admin.medal-rounds.action.delete.blocked"
                        : "judging-admin.tables.action.delete.blocked"));

        return new HorizontalLayout(editWrapper, assignJudgesWrapper, assignEntriesWrapper,
                startWrapper, revertWrapper, deleteWrapper, openButton);
    }

    public void openRevertRoundDialog(JudgingRound round) {
        boolean isMedal = round.getType() == RoundType.MEDAL;
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("judging-admin.tables.action.revert.confirm.title",
                round.getName()));
        dialog.add(new Span(getTranslation(isMedal
                ? "judging-admin.tables.action.revert.confirm.body.medal"
                : "judging-admin.tables.action.revert.confirm.body")));

        var confirmButton = new Button(getTranslation("judging-admin.tables.action.revert"), e -> {
            try {
                // Medal Revert returns the round to READY and clears its medal
                // awards (scoresheets are kept); scoring Revert returns to READY
                // and deletes the round's scoresheets.
                if (isMedal) {
                    judgingService.resetMedalRoundById(round.getId(), currentUserId);
                } else {
                    judgingService.revertScoringRound(round.getId(), currentUserId);
                }
                refreshRoundsGrid();
                Notification.show(getTranslation("judging-admin.tables.reverted"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            } finally {
                dialog.close();
            }
        });
        confirmButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        confirmButton.setDisableOnClick(true);

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    /**
     * Vaadin disabled buttons don't fire pointer events, so their own tooltip
     * never shows. Wrapping the button in a Span and attaching the tooltip to
     * the wrapper makes it work whether the button is enabled or not. Same
     * pattern used in DivisionEntryAdminView for registration-closed buttons.
     */
    private Span wrapWithTooltip(Button button, String tooltip) {
        var wrapper = new Span(button);
        com.vaadin.flow.component.shared.Tooltip.forComponent(wrapper).setText(tooltip);
        return wrapper;
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
            boolean canStart = allCategoryRoundsComplete();
            var startButton = new Button(getTranslation("judging-admin.bos.action.start"),
                    e -> openStartBosDialog());
            startButton.setId("bos-start-button");
            startButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            startButton.setEnabled(canStart);
            header.add(wrapWithTooltip(startButton, canStart
                    ? getTranslation("judging-admin.bos.action.start")
                    : getTranslation("judging-admin.bos.action.start.disabled-tooltip")));
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
            var resetWrapper = wrapWithTooltip(resetButton, placementsExist
                    ? getTranslation("judging-admin.bos.action.reset.disabled-tooltip")
                    : getTranslation("judging-admin.bos.action.reset"));
            header.add(finalizeButton, resetWrapper);
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
                && configs.stream().allMatch(c -> judgingService
                        .getEffectiveMedalRoundStatus(c.getDivisionCategoryId())
                        .map(s -> s == JudgingRoundStatus.COMPLETE)
                        .orElse(false));
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
        candidatesGrid.setAllRowsVisible(true);
        candidatesGrid.addColumn(this::formatEntryNumber)
                .setHeader(getTranslation("judging-admin.bos.candidates.column.entry-number"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        candidatesGrid.addColumn(this::formatEntryCode)
                .setHeader(getTranslation("judging-admin.bos.candidates.column.entry-code"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        candidatesGrid.addColumn(this::formatEntryMeadName)
                .setHeader(getTranslation("judging-admin.bos.candidates.column.mead-name"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        candidatesGrid.addColumn(a -> formatCategory(a.getFinalCategoryId()))
                .setHeader(getTranslation("judging-admin.bos.candidates.column.category"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        candidatesGrid.setItems(goldAwards);
        section.add(candidatesGrid);
        return section;
    }

    private String formatEntryNumber(MedalAward award) {
        try {
            var number = entryService.findEntryById(award.getEntryId()).getEntryNumber();
            var prefix = division.getEntryPrefix();
            return prefix != null && !prefix.isBlank() ? prefix + "-" + number : String.valueOf(number);
        } catch (Exception e) {
            return "?";
        }
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
        placementsGrid.setAllRowsVisible(true);
        placementsGrid.addColumn(BosPlacementRow::place)
                .setHeader(getTranslation("judging-admin.bos.placements.column.place"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        placementsGrid.addColumn(this::formatPlacementEntry)
                .setHeader(getTranslation("judging-admin.bos.placements.column.entry"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        placementsGrid.addColumn(this::formatPlacementCategory)
                .setHeader(getTranslation("judging-admin.bos.placements.column.category"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        placementsGrid.addColumn(this::formatPlacementAwardedBy)
                .setHeader(getTranslation("judging-admin.bos.placements.column.awarded-by"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        placementsGrid.addComponentColumn(this::createPlacementActionsCell)
                .setHeader(getTranslation("judging-admin.bos.placements.column.actions"))
                .setResizable(true).setAutoWidth(true).setFlexGrow(0);
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
