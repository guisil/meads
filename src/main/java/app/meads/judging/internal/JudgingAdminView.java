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
import app.meads.judging.Judging;
import app.meads.judging.JudgingService;
import app.meads.judging.JudgingTable;
import app.meads.judging.JudgingTableStatus;
import app.meads.judging.ScoresheetService;
import app.meads.judging.ScoresheetStatus;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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

    private Grid<JudgingTable> tablesGrid;

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

        removeAll();
        add(createBreadcrumb());
        add(createHeader());
        add(createTabSheet());
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
        return header;
    }

    private TabSheet createTabSheet() {
        var tabSheet = new TabSheet();
        tabSheet.setWidthFull();
        tabSheet.add(getTranslation("judging-admin.tab.tables"), createTablesTab());
        tabSheet.add(getTranslation("judging-admin.tab.medal-rounds"), createMedalRoundsTab());
        tabSheet.add(getTranslation("judging-admin.tab.bos"), createBosTab());
        return tabSheet;
    }

    private VerticalLayout createTablesTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var addButton = new Button(getTranslation("judging-admin.tables.add"),
                e -> openAddTableDialog());
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        tab.add(addButton);

        tablesGrid = new Grid<>(JudgingTable.class, false);
        tablesGrid.setId("tables-grid");
        tablesGrid.addColumn(JudgingTable::getName)
                .setHeader(getTranslation("judging-admin.tables.column.name"));
        tablesGrid.addColumn(t -> formatCategory(t.getDivisionCategoryId()))
                .setHeader(getTranslation("judging-admin.tables.column.category"));
        tablesGrid.addColumn(t -> t.getStatus().name())
                .setHeader(getTranslation("judging-admin.tables.column.status"));
        tablesGrid.addColumn(t -> t.getAssignments().size())
                .setHeader(getTranslation("judging-admin.tables.column.judges"));
        tablesGrid.addColumn(t -> t.getScheduledDate() == null ? ""
                        : t.getScheduledDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                                .withLocale(getLocale())))
                .setHeader(getTranslation("judging-admin.tables.column.scheduled"));
        tablesGrid.addColumn(this::formatScoresheetCounts)
                .setHeader(getTranslation("judging-admin.tables.column.scoresheets"));
        tablesGrid.addComponentColumn(this::createActionsCell)
                .setHeader(getTranslation("judging-admin.tables.column.actions"));

        refreshTablesGrid();
        tab.add(tablesGrid);
        return tab;
    }

    private void refreshTablesGrid() {
        tablesGrid.setItems(judgingService.findTablesByJudgingId(judging.getId()));
    }

    private String formatScoresheetCounts(JudgingTable table) {
        long drafts = scoresheetService.countByTableIdAndStatus(table.getId(), ScoresheetStatus.DRAFT);
        long submitted = scoresheetService.countByTableIdAndStatus(table.getId(), ScoresheetStatus.SUBMITTED);
        if (drafts == 0 && submitted == 0) {
            return "—";
        }
        return getTranslation("judging-admin.tables.scoresheets.format", drafts, submitted);
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

    private void openAddTableDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("judging-admin.tables.add"));

        var form = new VerticalLayout();
        form.setPadding(false);

        var nameField = new TextField(getTranslation("judging-admin.tables.dialog.name"));
        nameField.setId("add-table-name");
        nameField.setWidthFull();
        nameField.setMaxLength(120);

        var categorySelect = new Select<DivisionCategory>();
        categorySelect.setId("add-table-category");
        categorySelect.setLabel(getTranslation("judging-admin.tables.dialog.category"));
        categorySelect.setWidthFull();
        var categories = competitionService.findJudgingCategories(division.getId());
        categorySelect.setItems(categories);
        categorySelect.setItemLabelGenerator(c -> c == null ? "" : c.getCode() + " — " + c.getName());

        var datePicker = new DatePicker(getTranslation("judging-admin.tables.dialog.scheduled"));
        datePicker.setWidthFull();

        form.add(nameField, categorySelect, datePicker);
        dialog.add(form);

        var saveButton = new Button(getTranslation("button.save"), e -> {
            if (nameField.getValue() == null || nameField.getValue().isBlank()) {
                nameField.setInvalid(true);
                nameField.setErrorMessage(getTranslation("judging-admin.tables.dialog.name.error"));
                return;
            }
            if (categorySelect.getValue() == null) {
                categorySelect.setInvalid(true);
                categorySelect.setErrorMessage(getTranslation("judging-admin.tables.dialog.category.error"));
                return;
            }
            try {
                judgingService.createTable(judging.getId(), nameField.getValue().trim(),
                        categorySelect.getValue().getId(), datePicker.getValue(), currentUserId);
                dialog.close();
                refreshTablesGrid();
                Notification.show(getTranslation("judging-admin.tables.added"))
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

    private HorizontalLayout createActionsCell(JudgingTable table) {
        var editButton = new Button(new Icon(VaadinIcon.EDIT));
        editButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        editButton.setTooltipText(getTranslation("judging-admin.tables.action.edit"));
        editButton.addClickListener(e -> openEditTableDialog(table));

        var startButton = new Button(new Icon(VaadinIcon.PLAY));
        startButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        startButton.setEnabled(table.getStatus() == JudgingTableStatus.NOT_STARTED);
        startButton.setTooltipText(getTranslation("judging-admin.tables.action.start"));
        startButton.addClickListener(e -> openStartTableDialog(table));

        var assignJudgesButton = new Button(new Icon(VaadinIcon.USERS));
        assignJudgesButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        assignJudgesButton.setTooltipText(getTranslation("judging-admin.tables.action.assign-judges"));
        assignJudgesButton.addClickListener(e -> openAssignJudgesDialog(table));

        var deleteButton = new Button(new Icon(VaadinIcon.TRASH));
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        boolean canDelete = table.getStatus() == JudgingTableStatus.NOT_STARTED
                && table.getAssignments().isEmpty();
        deleteButton.setEnabled(canDelete);
        deleteButton.setTooltipText(canDelete
                ? getTranslation("judging-admin.tables.action.delete")
                : getTranslation("judging-admin.tables.action.delete.blocked"));
        deleteButton.addClickListener(e -> openDeleteTableDialog(table));

        return new HorizontalLayout(editButton, startButton, assignJudgesButton, deleteButton);
    }

    public void openEditTableDialog(JudgingTable table) {
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
                    judgingService.updateTableName(table.getId(), nameField.getValue().trim(), currentUserId);
                }
                if (!java.util.Objects.equals(datePicker.getValue(), table.getScheduledDate())) {
                    judgingService.updateTableScheduledDate(table.getId(), datePicker.getValue(), currentUserId);
                }
                dialog.close();
                refreshTablesGrid();
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

    public void openStartTableDialog(JudgingTable table) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("judging-admin.tables.action.start.confirm.title", table.getName()));
        boolean hasEntries = !entryService.findEntriesByFinalCategoryId(table.getDivisionCategoryId()).isEmpty();
        var bodyKey = hasEntries
                ? "judging-admin.tables.action.start.confirm.body"
                : "judging-admin.tables.action.start.confirm.body.empty";
        dialog.add(new Span(getTranslation(bodyKey)));

        var startButton = new Button(getTranslation("judging-admin.tables.action.start"), e -> {
            try {
                judgingService.startTable(table.getId(), currentUserId);
                dialog.close();
                refreshTablesGrid();
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

    public void openAssignJudgesDialog(JudgingTable table) {
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
                refreshTablesGrid();
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

    public void openDeleteTableDialog(JudgingTable table) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("judging-admin.tables.action.delete.confirm.title", table.getName()));
        dialog.add(new Span(getTranslation("judging-admin.tables.action.delete.confirm.body")));

        var deleteButton = new Button(getTranslation("button.delete"), e -> {
            try {
                judgingService.deleteTable(table.getId(), currentUserId);
                dialog.close();
                refreshTablesGrid();
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

    private VerticalLayout createMedalRoundsTab() {
        var tab = new VerticalLayout();
        tab.setPadding(false);

        var configs = judgingService.findCategoryConfigsForDivision(division.getId(), currentUserId);

        if (configs.isEmpty()) {
            tab.add(new Span(getTranslation("judging-admin.medal-rounds.empty")));
            return tab;
        }

        var grid = new Grid<CategoryJudgingConfig>(CategoryJudgingConfig.class, false);
        grid.setId("medal-rounds-grid");
        grid.addColumn(c -> formatCategory(c.getDivisionCategoryId()))
                .setHeader(getTranslation("judging-admin.medal-rounds.column.category"));
        grid.addColumn(c -> c.getMedalRoundMode().name())
                .setHeader(getTranslation("judging-admin.medal-rounds.column.mode"));
        grid.addColumn(c -> c.getMedalRoundStatus().name())
                .setHeader(getTranslation("judging-admin.medal-rounds.column.status"));
        grid.addColumn(this::formatTablesProgress)
                .setHeader(getTranslation("judging-admin.medal-rounds.column.tables"));
        grid.setItems(configs);
        tab.add(grid);
        return tab;
    }

    private String formatTablesProgress(CategoryJudgingConfig config) {
        var tables = judgingService.findTablesByJudgingId(judging.getId()).stream()
                .filter(t -> config.getDivisionCategoryId().equals(t.getDivisionCategoryId()))
                .toList();
        long complete = tables.stream()
                .filter(t -> t.getStatus() == JudgingTableStatus.COMPLETE)
                .count();
        return complete + " / " + tables.size();
    }

    private VerticalLayout createBosTab() {
        return new VerticalLayout();
    }

    private UUID getCurrentUserId() {
        return authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(userDetails -> userService.findByEmail(userDetails.getUsername()).getId())
                .orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }
}
