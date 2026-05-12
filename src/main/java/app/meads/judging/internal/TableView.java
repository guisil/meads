package app.meads.judging.internal;

import app.meads.BusinessRuleException;
import app.meads.MainLayout;
import app.meads.competition.Competition;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.entry.Entry;
import app.meads.entry.EntryService;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserService;
import app.meads.judging.Judging;
import app.meads.judging.JudgingService;
import app.meads.judging.JudgingTable;
import app.meads.judging.MedalRoundStatus;
import app.meads.judging.Scoresheet;
import app.meads.judging.ScoresheetService;
import app.meads.judging.ScoresheetStatus;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Nav;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Route(value = "competitions/:compShortName/divisions/:divShortName/tables/:tableId", layout = MainLayout.class)
@PermitAll
public class TableView extends VerticalLayout implements BeforeEnterObserver {

    private final CompetitionService competitionService;
    private final UserService userService;
    private final JudgingService judgingService;
    private final ScoresheetService scoresheetService;
    private final EntryService entryService;
    private final transient AuthenticationContext authenticationContext;

    private Competition competition;
    private Division division;
    private JudgingTable table;
    private String compShortName;
    private String divShortName;
    private UUID currentUserId;
    private boolean isAdmin;
    private boolean isSystemAdmin;

    private Grid<Scoresheet> scoresheetsGrid;
    private Select<String> statusFilter;
    private TextField searchField;
    private List<Scoresheet> allSheets;
    private Map<UUID, Entry> entriesById;
    private Map<UUID, User> usersById;

    public TableView(CompetitionService competitionService,
                     UserService userService,
                     JudgingService judgingService,
                     ScoresheetService scoresheetService,
                     EntryService entryService,
                     AuthenticationContext authenticationContext) {
        this.competitionService = competitionService;
        this.userService = userService;
        this.judgingService = judgingService;
        this.scoresheetService = scoresheetService;
        this.entryService = entryService;
        this.authenticationContext = authenticationContext;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        compShortName = event.getRouteParameters().get("compShortName").orElse(null);
        divShortName = event.getRouteParameters().get("divShortName").orElse(null);
        var tableIdParam = event.getRouteParameters().get("tableId").orElse(null);

        if (compShortName == null || divShortName == null || tableIdParam == null) {
            event.forwardTo("");
            return;
        }

        UUID tableId;
        try {
            tableId = UUID.fromString(tableIdParam);
        } catch (IllegalArgumentException e) {
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

        var maybeTable = judgingService.findTableById(tableId);
        if (maybeTable.isEmpty()) {
            event.forwardTo("");
            return;
        }
        Judging judging = judgingService.ensureJudgingExists(division.getId());
        if (!maybeTable.get().getJudgingId().equals(judging.getId())) {
            event.forwardTo("");
            return;
        }
        table = maybeTable.get();

        currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            event.forwardTo("");
            return;
        }
        var user = userService.findById(currentUserId);
        isSystemAdmin = user.getRole() == Role.SYSTEM_ADMIN;
        boolean isDivisionAdmin = competitionService.isAuthorizedForDivision(division.getId(), currentUserId);
        boolean isAssignedJudge = judgingService.isJudgeAssignedToTable(table.getId(), currentUserId);

        if (!isSystemAdmin && !isDivisionAdmin && !isAssignedJudge) {
            event.forwardTo("");
            return;
        }

        if (!isSystemAdmin && !userService.hasPassword(currentUserId) && !isAssignedJudge) {
            event.forwardTo("");
            return;
        }

        isAdmin = isSystemAdmin || isDivisionAdmin;

        loadScoresheetData();
        removeAll();
        add(createBreadcrumb());
        add(createHeader());
        add(createFilterBar());
        add(createScoresheetsGrid());
    }

    private void loadScoresheetData() {
        allSheets = scoresheetService.findByTableId(table.getId());
        entriesById = allSheets.stream()
                .map(s -> entryService.findEntryById(s.getEntryId()))
                .collect(Collectors.toMap(Entry::getId, e -> e, (a, b) -> a));
        usersById = allSheets.stream()
                .map(Scoresheet::getFilledByJudgeUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .map(userService::findById)
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }

    private HorizontalLayout createFilterBar() {
        statusFilter = new Select<>();
        statusFilter.setId("status-filter");
        statusFilter.setLabel(getTranslation("table.filter.status.label"));
        statusFilter.setItems(
                getTranslation("table.filter.status.option.all"),
                getTranslation("table.filter.status.option.draft"),
                getTranslation("table.filter.status.option.submitted"));
        statusFilter.setValue(getTranslation("table.filter.status.option.all"));
        statusFilter.addValueChangeListener(e -> applyFilters());

        searchField = new TextField();
        searchField.setId("search-field");
        searchField.setPlaceholder(getTranslation("table.filter.search.placeholder"));
        searchField.setValueChangeMode(ValueChangeMode.EAGER);
        searchField.setClearButtonVisible(true);
        searchField.addValueChangeListener(e -> applyFilters());
        searchField.setWidth("280px");

        var bar = new HorizontalLayout(statusFilter, searchField);
        bar.setDefaultVerticalComponentAlignment(Alignment.END);
        return bar;
    }

    private Grid<Scoresheet> createScoresheetsGrid() {
        scoresheetsGrid = new Grid<>(Scoresheet.class, false);
        scoresheetsGrid.setId("scoresheets-grid");

        scoresheetsGrid.addColumn(s -> entryCode(entriesById.get(s.getEntryId())))
                .setHeader(getTranslation("table.column.entry"));
        scoresheetsGrid.addColumn(s -> meadName(entriesById.get(s.getEntryId())))
                .setHeader(getTranslation("table.column.mead"));
        scoresheetsGrid.addColumn(s -> s.getStatus().name())
                .setHeader(getTranslation("table.column.status"));
        scoresheetsGrid.addColumn(s -> s.getTotalScore() == null ? "—" : s.getTotalScore().toString())
                .setHeader(getTranslation("table.column.total"));
        scoresheetsGrid.addColumn(s -> filledByName(s.getFilledByJudgeUserId(), usersById))
                .setHeader(getTranslation("table.column.filled-by"));
        scoresheetsGrid.addComponentColumn(this::createActionsCell)
                .setHeader(getTranslation("table.column.actions"));

        scoresheetsGrid.setItems(allSheets);
        scoresheetsGrid.asSingleSelect().addValueChangeListener(e -> {
            if (e.getValue() != null) {
                var url = "competitions/" + compShortName
                        + "/divisions/" + divShortName
                        + "/scoresheets/" + e.getValue().getId();
                getUI().ifPresent(ui -> ui.navigate(url));
            }
        });
        return scoresheetsGrid;
    }

    private void applyFilters() {
        var statusOpt = statusFilter.getValue();
        var draftLabel = getTranslation("table.filter.status.option.draft");
        var submittedLabel = getTranslation("table.filter.status.option.submitted");

        ScoresheetStatus statusFilterValue = null;
        if (draftLabel.equals(statusOpt)) {
            statusFilterValue = ScoresheetStatus.DRAFT;
        } else if (submittedLabel.equals(statusOpt)) {
            statusFilterValue = ScoresheetStatus.SUBMITTED;
        }
        final ScoresheetStatus finalStatus = statusFilterValue;

        var search = searchField.getValue();
        final String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);

        var filtered = allSheets.stream()
                .filter(s -> finalStatus == null || s.getStatus() == finalStatus)
                .filter(s -> needle.isEmpty() || matchesSearch(s, needle))
                .toList();
        scoresheetsGrid.setItems(filtered);
    }

    private HorizontalLayout createActionsCell(Scoresheet sheet) {
        var actions = new HorizontalLayout();
        actions.setPadding(false);
        actions.setSpacing(true);
        if (isAdmin && sheet.getStatus() == ScoresheetStatus.SUBMITTED) {
            var revertButton = new Button(new Icon(VaadinIcon.ARROW_BACKWARD));
            revertButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            boolean medalRoundLocks = medalRoundLocksRevert(sheet);
            revertButton.setEnabled(!medalRoundLocks);
            revertButton.setTooltipText(medalRoundLocks
                    ? getTranslation("table.revert.blocked.medal-round-active")
                    : getTranslation("table.action.revert"));
            revertButton.addClickListener(e -> openRevertDialog(sheet));
            actions.add(revertButton);
        }
        if (isAdmin && sheet.getStatus() == ScoresheetStatus.DRAFT) {
            var moveButton = new Button(new Icon(VaadinIcon.EXCHANGE));
            moveButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            moveButton.setTooltipText(getTranslation("table.action.move"));
            moveButton.addClickListener(e -> openMoveDialog(sheet));
            actions.add(moveButton);
        }
        return actions;
    }

    private boolean medalRoundLocksRevert(Scoresheet sheet) {
        return judgingService.findCategoryConfigByDivisionCategoryId(table.getDivisionCategoryId())
                .map(c -> c.getMedalRoundStatus() == MedalRoundStatus.ACTIVE
                        || c.getMedalRoundStatus() == MedalRoundStatus.COMPLETE)
                .orElse(false);
    }

    public void openMoveDialog(Scoresheet sheet) {
        var entry = entriesById.get(sheet.getEntryId());
        var entryLabel = entry == null ? "" : entry.getEntryCode();

        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("table.move.dialog.title", entryLabel));

        var form = new VerticalLayout();
        form.setPadding(false);

        var candidates = judgingService.findTablesByDivisionAndCategory(
                        division.getId(), table.getDivisionCategoryId()).stream()
                .filter(t -> !t.getId().equals(table.getId()))
                .filter(t -> t.getStatus() == app.meads.judging.JudgingTableStatus.ROUND_1)
                .toList();

        var targetSelect = new Select<JudgingTable>();
        targetSelect.setId("move-target-select");
        targetSelect.setLabel(getTranslation("table.move.target.label"));
        targetSelect.setWidthFull();
        targetSelect.setItems(candidates);
        targetSelect.setItemLabelGenerator(t -> t == null ? "" : t.getName());
        targetSelect.setHelperText(getTranslation("table.move.helper"));

        form.add(targetSelect);

        if (candidates.isEmpty()) {
            var emptyMsg = new Span(getTranslation("table.move.empty.no-other-tables"));
            form.add(emptyMsg);
        }
        dialog.add(form);

        var saveButton = new Button(getTranslation("button.save"), e -> {
            if (targetSelect.getValue() == null) {
                targetSelect.setInvalid(true);
                targetSelect.setErrorMessage(getTranslation("table.move.target.label"));
                return;
            }
            try {
                scoresheetService.moveToTable(sheet.getId(), targetSelect.getValue().getId(), currentUserId);
                dialog.close();
                loadScoresheetData();
                scoresheetsGrid.setItems(allSheets);
                Notification.show(getTranslation("table.move.success", targetSelect.getValue().getName()))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.setEnabled(!candidates.isEmpty());
        saveButton.setDisableOnClick(true);

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    public void openRevertDialog(Scoresheet sheet) {
        var entry = entriesById.get(sheet.getEntryId());
        var entryLabel = entry == null ? "" : entry.getEntryCode();

        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("table.revert.confirm.title", entryLabel));
        dialog.add(new Span(getTranslation("table.revert.confirm.body")));

        var revertButton = new Button(getTranslation("table.action.revert"), e -> {
            try {
                scoresheetService.revertToDraft(sheet.getId(), currentUserId);
                dialog.close();
                loadScoresheetData();
                scoresheetsGrid.setItems(allSheets);
                Notification.show(getTranslation("table.revert.success", entryLabel))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        revertButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        revertButton.setDisableOnClick(true);

        var cancelButton = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancelButton, revertButton);
        dialog.open();
    }

    private boolean matchesSearch(Scoresheet s, String needle) {
        var entry = entriesById.get(s.getEntryId());
        if (entry == null) {
            return false;
        }
        return (entry.getEntryCode() != null && entry.getEntryCode().toLowerCase(Locale.ROOT).contains(needle))
                || (entry.getMeadName() != null && entry.getMeadName().toLowerCase(Locale.ROOT).contains(needle));
    }

    private String entryCode(Entry entry) {
        return entry == null ? "" : entry.getEntryCode();
    }

    private String meadName(Entry entry) {
        return entry == null ? "" : entry.getMeadName();
    }

    private String filledByName(UUID userId, Map<UUID, User> usersById) {
        if (userId == null) {
            return "—";
        }
        var user = usersById.get(userId);
        return user == null ? "—" : user.getName();
    }

    private Nav createBreadcrumb() {
        var nav = new Nav();
        if (isAdmin) {
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
            nav.add(new Anchor(
                    "competitions/" + compShortName + "/divisions/" + divShortName + "/judging-admin",
                    getTranslation("judging-admin.nav.judging-admin")));
        } else {
            nav.add(new Anchor("my-judging", getTranslation("my-judging.nav.my-judging")));
        }
        nav.add(new Span(" / "));
        nav.add(new Span(getTranslation("judge-table.title", table.getName())));
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
                + " — " + getTranslation("judge-table.title", table.getName())));
        return header;
    }

    private UUID getCurrentUserId() {
        return authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(userDetails -> userService.findByEmail(userDetails.getUsername()).getId())
                .orElse(null);
    }
}
