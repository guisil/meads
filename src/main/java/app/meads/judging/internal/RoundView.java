package app.meads.judging.internal;

import app.meads.BusinessRuleException;
import app.meads.MainLayout;
import app.meads.competition.CategoryDisplay;
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
import app.meads.judging.JudgingRound;
import app.meads.judging.JudgingRoundStatus;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Route(value = "competitions/:compShortName/divisions/:divShortName/rounds/:roundId", layout = MainLayout.class)
@PermitAll
public class RoundView extends VerticalLayout implements BeforeEnterObserver {

    private final CompetitionService competitionService;
    private final UserService userService;
    private final JudgingService judgingService;
    private final ScoresheetService scoresheetService;
    private final EntryService entryService;
    private final transient AuthenticationContext authenticationContext;

    private Competition competition;
    private Division division;
    private JudgingRound table;
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
    private Map<UUID, Integer> runningTotals;

    public RoundView(CompetitionService competitionService,
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
        var tableIdParam = event.getRouteParameters().get("roundId").orElse(null);

        if (compShortName == null || divShortName == null || tableIdParam == null) {
            event.forwardTo("");
            return;
        }

        UUID roundId;
        try {
            roundId = UUID.fromString(tableIdParam);
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

        var maybeTable = judgingService.findRoundById(roundId);
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
        boolean isAssignedJudge = judgingService.isJudgeAssignedToRound(table.getId(), currentUserId);

        if (!isSystemAdmin && !isDivisionAdmin && !isAssignedJudge) {
            event.forwardTo("");
            return;
        }

        if (!isSystemAdmin && !userService.hasPassword(currentUserId) && !isAssignedJudge) {
            event.forwardTo("");
            return;
        }

        isAdmin = isSystemAdmin || isDivisionAdmin;

        // Judges only see the ACTIVE round they're working. Admins retain
        // full access to rounds in any state.
        if (!isAdmin && table.getStatus() != JudgingRoundStatus.ACTIVE) {
            event.forwardTo("");
            return;
        }

        renderView();
    }

    private void renderView() {
        loadScoresheetData();
        removeAll();
        add(createBreadcrumb());
        add(createHeader());
        add(createRoundActionBar());
        add(createFilterBar());
        add(createScoresheetsGrid());
    }

    private void loadScoresheetData() {
        allSheets = scoresheetService.findByRoundId(table.getId());
        entriesById = allSheets.stream()
                .map(s -> entryService.findEntryById(s.getEntryId()))
                .collect(Collectors.toMap(Entry::getId, e -> e, (a, b) -> a));
        usersById = allSheets.stream()
                .map(Scoresheet::getFilledByJudgeUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .map(userService::findById)
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        // Precompute per-sheet totals while the service is still inside its
        // read transaction. Sheet.fields is lazy and would otherwise blow up
        // when the Vaadin grid cell tries to access it.
        runningTotals = scoresheetService.runningTotalsByRoundId(table.getId());
    }

    private HorizontalLayout createFilterBar() {
        statusFilter = new Select<>();
        statusFilter.setId("status-filter");
        statusFilter.setLabel(getTranslation("table.filter.status.label"));
        statusFilter.setItems(
                getTranslation("table.filter.status.option.all"),
                getTranslation("table.filter.status.option.blank"),
                getTranslation("table.filter.status.option.draft"),
                getTranslation("table.filter.status.option.submitted"));
        statusFilter.setValue(getTranslation("table.filter.status.option.all"));
        statusFilter.addValueChangeListener(e -> applyFilters());

        searchField = new TextField();
        searchField.setId("search-field");
        // Anonymity rule: judges can search by code only (mead name is hidden +
        // unsearchable to prevent de-anonymizing a coded row by typing a known
        // brand fragment). Admins keep the dual-field search.
        searchField.setPlaceholder(getTranslation(isAdmin
                ? "table.filter.search.placeholder"
                : "table.filter.search.placeholder.judge"));
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

        if (isAdmin) {
            scoresheetsGrid.addColumn(s -> entryNumberLabel(entriesById.get(s.getEntryId())))
                    .setHeader(getTranslation("table.column.entry-number"))
                    .setComparator(Comparator.comparingInt(
                            (Scoresheet s) -> {
                                var e = entriesById.get(s.getEntryId());
                                return e == null ? Integer.MAX_VALUE : e.getEntryNumber();
                            }))
                    .setResizable(true).setSortable(true);
        }
        scoresheetsGrid.addColumn(s -> entryCode(entriesById.get(s.getEntryId())))
                .setHeader(getTranslation("table.column.entry-code"))
                .setResizable(true).setSortable(true);
        // Mead name is the entrant's brand label. Anonymity rule: judges judge
        // to style, not to a brand. Same rule already gates the mead name on
        // ScoresheetView (see shouldShowEntryCodeButNotMeadNameToAssignedJudge).
        if (isAdmin) {
            scoresheetsGrid.addColumn(s -> meadName(entriesById.get(s.getEntryId())))
                    .setHeader(getTranslation("table.column.mead-name"))
                    .setResizable(true).setSortable(true);
        }
        scoresheetsGrid.addColumn(s -> s.getStatus().name())
                .setHeader(getTranslation("table.column.status"))
                .setResizable(true).setSortable(true);
        scoresheetsGrid.addColumn(this::formatTotalCell)
                .setHeader(getTranslation("table.column.total"))
                .setComparator(Comparator.comparing(this::sortableTotal,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .setResizable(true).setSortable(true);
        scoresheetsGrid.addColumn(s -> s.isAdvancedToMedalRound() ? "✓" : "—")
                .setHeader(getTranslation("table.column.advances"))
                .setResizable(true).setSortable(true);
        scoresheetsGrid.addColumn(s -> filledByName(s.getFilledByJudgeUserId(), usersById))
                .setHeader(getTranslation("table.column.filled-by"))
                .setResizable(true).setSortable(true);
        scoresheetsGrid.addComponentColumn(this::createActionsCell)
                .setHeader(getTranslation("table.column.actions"))
                .setResizable(true).setFlexGrow(0);

        scoresheetsGrid.setItems(allSheets);
        // Single-select (with toggle-off) so clicking a row just highlights it —
        // useful to keep track of which row's icons you're clicking in a large grid.
        // Opening a scoresheet stays on the explicit ✏ icon (no row-click navigation),
        // since the row already carries dedicated 👁 mead-details + ✏ Open icons.
        scoresheetsGrid.setSelectionMode(Grid.SelectionMode.SINGLE);
        return scoresheetsGrid;
    }

    private void applyFilters() {
        var statusOpt = statusFilter.getValue();
        var blankLabel = getTranslation("table.filter.status.option.blank");
        var draftLabel = getTranslation("table.filter.status.option.draft");
        var submittedLabel = getTranslation("table.filter.status.option.submitted");

        ScoresheetStatus statusFilterValue = null;
        if (blankLabel.equals(statusOpt)) {
            statusFilterValue = ScoresheetStatus.BLANK;
        } else if (draftLabel.equals(statusOpt)) {
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

    private void navigateToScoresheet(Scoresheet sheet) {
        var url = "competitions/" + compShortName
                + "/divisions/" + divShortName
                + "/scoresheets/" + sheet.getId();
        getUI().ifPresent(ui -> ui.navigate(url));
    }

    /**
     * Round-level action bar: a single Finalize/Submit for ACTIVE rounds
     * (judge + admin; enabled only when every scoresheet is FILLED) and an
     * admin-only Reopen for COMPLETE rounds. Replaces the per-row judge Submit.
     */
    private HorizontalLayout createRoundActionBar() {
        var bar = new HorizontalLayout();
        bar.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        if (table.getStatus() == JudgingRoundStatus.ACTIVE) {
            boolean allFilled = !allSheets.isEmpty()
                    && allSheets.stream().allMatch(s -> s.getStatus() == ScoresheetStatus.FILLED);
            var finalize = new Button(getTranslation("table.action.finalize"), e -> openFinalizeDialog());
            finalize.setId("round-finalize-button");
            finalize.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
            finalize.setEnabled(allFilled);
            var wrapper = new Span(finalize);
            com.vaadin.flow.component.shared.Tooltip.forComponent(wrapper).setText(allFilled
                    ? getTranslation("table.finalize.tooltip")
                    : getTranslation("table.finalize.disabled"));
            bar.add(wrapper);
        } else if (table.getStatus() == JudgingRoundStatus.COMPLETE && isAdmin) {
            var reopen = new Button(getTranslation("table.action.reopen"), e -> openReopenDialog());
            reopen.setId("round-reopen-button");
            reopen.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            bar.add(reopen);
        }
        return bar;
    }

    public void openFinalizeDialog() {
        long advancing = allSheets.stream().filter(Scoresheet::isAdvancedToMedalRound).count();
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("table.finalize.confirm.title", table.getName()));
        var body = new VerticalLayout();
        body.setPadding(false);
        body.add(new Span(getTranslation("table.finalize.confirm.body", allSheets.size(), advancing)));
        if (advancing == 0) {
            var warn = new Span(getTranslation("table.finalize.confirm.zero-advancing"));
            warn.getElement().getThemeList().add("badge error");
            body.add(warn);
        }
        if (isAdmin) {
            body.add(new Span(getTranslation("table.finalize.confirm.admin-warning")));
        }
        dialog.add(body);
        var confirm = new Button(getTranslation("table.action.finalize"), e -> {
            try {
                scoresheetService.finalizeScoringRound(table.getId(), getCurrentUserId());
                dialog.close();
                Notification.show(getTranslation("table.finalized"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                table = judgingService.findRoundById(table.getId()).orElseThrow();
                renderView();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                e.getSource().setEnabled(true);
            }
        });
        confirm.setId("round-finalize-confirm");
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        confirm.setDisableOnClick(true);
        var cancel = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    public void openReopenDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("table.reopen.confirm.title", table.getName()));
        dialog.add(new Span(getTranslation("table.reopen.confirm.body")));
        var confirm = new Button(getTranslation("table.action.reopen"), e -> {
            try {
                scoresheetService.reopenScoringRound(table.getId(), getCurrentUserId());
                dialog.close();
                Notification.show(getTranslation("table.reopened"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                table = judgingService.findRoundById(table.getId()).orElseThrow();
                renderView();
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                e.getSource().setEnabled(true);
            }
        });
        confirm.setId("round-reopen-confirm");
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        confirm.setDisableOnClick(true);
        var cancel = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    /**
     * Total-column cell. SUBMITTED sheets show the locked total score.
     * BLANK/DRAFT/FILLED sheets show the live running sum of whatever has been
     * entered so far, so admins can see judging progress at a glance without
     * drilling into each scoresheet. Returns "—" for sheets with no scores
     * yet (pristine BLANK or DRAFT with all fields still null).
     */
    private String formatTotalCell(Scoresheet sheet) {
        if (sheet.getTotalScore() != null) {
            return sheet.getTotalScore().toString();
        }
        Integer running = runningTotals.get(sheet.getId());
        if (running == null || running == 0) {
            return "—";
        }
        return running.toString();
    }

    /** Numeric total used for column sorting; null (sorts last) when the cell shows "—". */
    private Integer sortableTotal(Scoresheet sheet) {
        if (sheet.getTotalScore() != null) {
            return sheet.getTotalScore();
        }
        Integer running = runningTotals.get(sheet.getId());
        return (running == null || running == 0) ? null : running;
    }

    /** Opens the read-only mead-details dialog for an entry. Public for testability
     *  (the per-row eye button lives in a Grid component column). */
    public void openMeadDetailsDialog(UUID entryId) {
        new MeadDetailsDialog(entryService.findEntryById(entryId)).open();
    }

    private HorizontalLayout createActionsCell(Scoresheet sheet) {
        var actions = new HorizontalLayout();
        actions.setPadding(false);
        actions.setSpacing(true);
        // View mead details (eye) — judges + admins, every row: the entry's
        // objective characteristics, no scores/comments, no brand name.
        var meadDetailsButton = new Button(new Icon(VaadinIcon.EYE));
        meadDetailsButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        meadDetailsButton.setId("mead-details-" + sheet.getEntryId());
        meadDetailsButton.setTooltipText(getTranslation("round.action.mead-details"));
        meadDetailsButton.addClickListener(e -> openMeadDetailsDialog(sheet.getEntryId()));
        actions.add(meadDetailsButton);

        // Open scoresheet (pencil = edit) for judges AND admins. The per-row judge
        // Submit is gone: judges Save each sheet (-> FILLED), then the round-level
        // Finalize submits them all at once.
        var openButton = new Button(new Icon(VaadinIcon.PENCIL));
        openButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        openButton.setId("open-" + sheet.getId());
        openButton.setTooltipText(getTranslation("table.action.open"));
        openButton.addClickListener(e -> navigateToScoresheet(sheet));
        actions.add(openButton);
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
        if (isAdmin && (sheet.getStatus() == ScoresheetStatus.DRAFT
                || sheet.getStatus() == ScoresheetStatus.FILLED)) {
            var moveButton = new Button(new Icon(VaadinIcon.EXCHANGE));
            moveButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            moveButton.setTooltipText(getTranslation("table.action.move"));
            moveButton.addClickListener(e -> openMoveDialog(sheet));
            actions.add(moveButton);
        }
        if (isAdmin) {
            var deleteButton = new Button(new Icon(VaadinIcon.TRASH));
            deleteButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
            boolean medalRoundLocks = medalRoundLocksRevert(sheet);
            deleteButton.setEnabled(!medalRoundLocks);
            deleteButton.setTooltipText(medalRoundLocks
                    ? getTranslation("table.delete.blocked.medal-round-active")
                    : getTranslation("table.action.delete"));
            deleteButton.addClickListener(e -> openDeleteScoresheetDialog(sheet));
            actions.add(deleteButton);
        }
        return actions;
    }

    private void openDeleteScoresheetDialog(Scoresheet sheet) {
        var entry = entriesById.get(sheet.getEntryId());
        var entryLabel = entry == null ? "" : entry.getEntryCode();
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("table.delete.dialog.title", entryLabel));
        dialog.add(new Span(getTranslation("table.delete.dialog.body", entryLabel)));
        var confirm = new Button(getTranslation("button.delete"), e -> {
            try {
                scoresheetService.deleteScoresheet(sheet.getId(), getCurrentUserId());
                dialog.close();
                loadScoresheetData();
                scoresheetsGrid.setItems(allSheets);
                Notification.show(getTranslation("table.delete.success", entryLabel))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                e.getSource().setEnabled(true);
            }
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
        confirm.setDisableOnClick(true);
        var cancel = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    private boolean medalRoundLocksRevert(Scoresheet sheet) {
        return judgingService.getEffectiveMedalRoundStatus(table.getDivisionCategoryId())
                .map(s -> s == JudgingRoundStatus.ACTIVE || s == JudgingRoundStatus.COMPLETE)
                .orElse(false);
    }

    public void openMoveDialog(Scoresheet sheet) {
        var entry = entriesById.get(sheet.getEntryId());
        var entryLabel = entry == null ? "" : entry.getEntryCode();

        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("table.move.dialog.title", entryLabel));

        var form = new VerticalLayout();
        form.setPadding(false);

        var candidates = judgingService.findRoundsByDivisionAndCategory(
                        division.getId(), table.getDivisionCategoryId()).stream()
                .filter(t -> !t.getId().equals(table.getId()))
                .filter(t -> t.getStatus() == JudgingRoundStatus.ACTIVE)
                .toList();

        var targetSelect = new Select<JudgingRound>();
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
                scoresheetService.moveToRound(sheet.getId(), targetSelect.getValue().getId(), currentUserId);
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
        if (entry.getEntryCode() != null && entry.getEntryCode().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        // Anonymity rule: judges can't search by mead name (see search-field
        // placeholder gate in createFilterBar).
        return isAdmin && entry.getMeadName() != null
                && entry.getMeadName().toLowerCase(Locale.ROOT).contains(needle);
    }

    private String entryCode(Entry entry) {
        return entry == null ? "" : entry.getEntryCode();
    }

    private String meadName(Entry entry) {
        return entry == null ? "" : entry.getMeadName();
    }

    private String entryNumberLabel(Entry entry) {
        if (entry == null) {
            return "";
        }
        var prefix = division.getEntryPrefix();
        if (prefix != null && !prefix.isBlank()) {
            return prefix + "-" + entry.getEntryNumber();
        }
        return String.valueOf(entry.getEntryNumber());
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

    private VerticalLayout createHeader() {
        var titleRow = new HorizontalLayout();
        titleRow.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        if (competition.hasLogo()) {
            var dataUri = "data:" + competition.getLogoContentType() + ";base64,"
                    + Base64.getEncoder().encodeToString(competition.getLogo());
            var logo = new Image(dataUri, competition.getName() + " logo");
            logo.setHeight("64px");
            titleRow.add(logo);
        }

        titleRow.add(new H2(competition.getName() + " — " + division.getName()
                + " — " + getTranslation("judge-table.title", table.getName())));

        var header = new VerticalLayout(titleRow, createInfoRow());
        // Admins get the assigned-judge roster spelled out — useful once the
        // round is COMPLETE and the Assign Judges dialog is locked, so there's
        // no other way to see who judged.
        if (isAdmin) {
            header.add(createJudgesLine());
        }
        header.add(createExplanation());
        header.setPadding(false);
        header.setSpacing(false);
        // A little breathing room between the title, the info row, and the
        // explanation — matches MedalRoundView's header.
        header.getStyle().set("gap", "var(--lumo-space-s)");
        return header;
    }

    /** Admin-only roster of the round's assigned judges (names, comma-separated). */
    private Span createJudgesLine() {
        var names = judgingService.findJudgeUserIdsForRound(table.getId()).stream()
                .map(userService::findById)
                .map(User::getName)
                .sorted()
                .collect(Collectors.joining(", "));
        var line = new Span(getTranslation("round.judges") + ": "
                + (names.isEmpty() ? "—" : names));
        line.setId("round-judges-line");
        // Right-aligned on its own line below the info row, under the Table info.
        line.getStyle().set("align-self", "flex-end");
        return line;
    }

    /**
     * Uniform info row mirroring the medal round: Table first, then a colored
     * Type badge, then Status — Status shown to admins only (a judge only ever
     * lands here on an ACTIVE round, so its status is implicit).
     */
    private HorizontalLayout createInfoRow() {
        var tableLabel = table.getPhysicalTableId() == null
                ? getTranslation("medal-round.physical-table.unassigned")
                : judgingService.findPhysicalTableById(table.getPhysicalTableId())
                        .map(app.meads.judging.PhysicalTable::getLabel)
                        .orElse(getTranslation("medal-round.physical-table.unassigned"));
        var tableLine = new Span(getTranslation("medal-round.physical-table") + ": " + tableLabel);
        tableLine.setId("round-physical-table-line");

        var badge = new Span(getTranslation("judging-admin.rounds.type.scoring"));
        badge.setId("round-type-badge");
        badge.getElement().getThemeList().add("badge contrast");

        var category = competitionService.findDivisionCategoryById(table.getDivisionCategoryId());
        var categoryBadge = RoundBadges.categoryBadge(category.getCode(),
                CategoryDisplay.name(category, getLocale(), this::getTranslation),
                getTranslation("judging-admin.rounds.column.category"));
        categoryBadge.setId("round-category-badge");

        var row = new HorizontalLayout(badge, categoryBadge);
        row.setWidthFull();
        row.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        row.setSpacing(true);
        if (isAdmin) {
            var statusBadge = new Span(getTranslation("medal-round.status") + ": " + table.getStatus().name());
            statusBadge.setId("round-status-line");
            row.add(statusBadge);
        }
        // Table info sits at the far right of the info row.
        tableLine.getStyle().set("margin-left", "auto");
        row.add(tableLine);
        return row;
    }

    private Span createExplanation() {
        // Admins get a third-person variant (they observe; the judges score).
        var span = new Span(getTranslation(isAdmin
                ? "round.explanation.scoring.admin" : "round.explanation.scoring"));
        span.setId("round-explanation");
        span.getStyle().set("color", "var(--lumo-secondary-text-color)");
        return span;
    }

    private UUID getCurrentUserId() {
        return authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(userDetails -> userService.findByEmail(userDetails.getUsername()).getId())
                .orElse(null);
    }
}
