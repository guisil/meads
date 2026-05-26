package app.meads.judging.internal;

import app.meads.BusinessRuleException;
import app.meads.MainLayout;
import app.meads.competition.Competition;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import app.meads.entry.Entry;
import app.meads.entry.EntryService;
import app.meads.entry.EntryStatus;
import app.meads.identity.Role;
import app.meads.judging.CategoryJudgingConfig;
import app.meads.judging.JudgingPhase;
import app.meads.judging.JudgingRound;
import app.meads.judging.JudgingRoundStatus;
import app.meads.judging.JudgingService;
import app.meads.judging.Medal;
import app.meads.judging.ScoresheetStatus;
import app.meads.judging.MedalRoundEntryRow;
import app.meads.judging.MedalRoundMode;
import app.meads.judging.MedalRoundScorePreview;
import app.meads.identity.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Nav;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import app.meads.judging.PhysicalTable;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Shared judge/admin medal-round form for one JUDGING-scope category. Judges and
 * admins award Gold / Silver / Bronze (or withhold) per entry; admins also get
 * Finalize / Reopen / Reset in the header. See design §4.E.
 */
@Route(value = "competitions/:compShortName/divisions/:divShortName/medal-rounds/:divisionCategoryId",
        layout = MainLayout.class)
@PermitAll
public class MedalRoundView extends VerticalLayout implements BeforeEnterObserver {

    private final CompetitionService competitionService;
    private final UserService userService;
    private final JudgingService judgingService;
    private final EntryService entryService;
    private final ScoresheetRepository scoresheetRepository;
    private final transient AuthenticationContext authenticationContext;

    private Competition competition;
    private Division division;
    private DivisionCategory category;
    private CategoryJudgingConfig config;
    private JudgingRound medalRound;
    private String compShortName;
    private String divShortName;
    private UUID currentUserId;
    private boolean isSystemAdmin;
    private boolean isAdmin;

    private Grid<MedalRoundEntryRow> grid;
    private Span summary;
    private List<MedalRoundEntryRow> rows;
    private Set<UUID> tiedEntryIds = Set.of();

    public MedalRoundView(CompetitionService competitionService,
                          UserService userService,
                          JudgingService judgingService,
                          EntryService entryService,
                          ScoresheetRepository scoresheetRepository,
                          AuthenticationContext authenticationContext) {
        this.competitionService = competitionService;
        this.userService = userService;
        this.judgingService = judgingService;
        this.entryService = entryService;
        this.scoresheetRepository = scoresheetRepository;
        this.authenticationContext = authenticationContext;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        compShortName = event.getRouteParameters().get("compShortName").orElse(null);
        divShortName = event.getRouteParameters().get("divShortName").orElse(null);
        var categoryIdParam = event.getRouteParameters().get("divisionCategoryId").orElse(null);

        if (compShortName == null || divShortName == null || categoryIdParam == null) {
            event.forwardTo("");
            return;
        }

        UUID divisionCategoryId;
        try {
            divisionCategoryId = UUID.fromString(categoryIdParam);
        } catch (IllegalArgumentException e) {
            event.forwardTo("");
            return;
        }

        try {
            competition = competitionService.findCompetitionByShortName(compShortName);
            division = competitionService.findDivisionByShortName(competition.getId(), divShortName);
            category = competitionService.findDivisionCategoryById(divisionCategoryId);
        } catch (BusinessRuleException e) {
            event.forwardTo("");
            return;
        }
        if (!category.getDivisionId().equals(division.getId())) {
            event.forwardTo("");
            return;
        }

        var maybeConfig = judgingService.findCategoryConfigByDivisionCategoryId(divisionCategoryId);
        if (maybeConfig.isEmpty()) {
            event.forwardTo("");
            return;
        }
        config = maybeConfig.get();

        currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            event.forwardTo("");
            return;
        }
        var user = userService.findById(currentUserId);
        isSystemAdmin = user.getRole() == Role.SYSTEM_ADMIN;
        boolean isDivisionAdmin = competitionService.isAuthorizedForDivision(division.getId(), currentUserId);
        boolean isAssignedJudge = judgingService.findRoundsByJudgeUserId(currentUserId).stream()
                .anyMatch(t -> t.getDivisionCategoryId().equals(divisionCategoryId));

        if (!isSystemAdmin && !isDivisionAdmin && !isAssignedJudge) {
            event.forwardTo("");
            return;
        }
        if (!isSystemAdmin && !userService.hasPassword(currentUserId) && !isAssignedJudge) {
            event.forwardTo("");
            return;
        }
        isAdmin = isSystemAdmin || isDivisionAdmin;

        reload();
    }

    /** Re-fetches the config + medal round + rows and rebuilds the whole view. */
    private void reload() {
        config = judgingService.findCategoryConfigByDivisionCategoryId(
                config.getDivisionCategoryId()).orElse(config);
        medalRound = judgingService.findMedalRoundByCategoryId(
                config.getDivisionCategoryId()).orElse(null);
        var mode = currentMode();
        rows = judgingService.findMedalRoundEntries(config.getDivisionCategoryId(), mode);
        var preview = mode == MedalRoundMode.SCORE_BASED
                ? judgingService.recomputeScorePreview(config.getDivisionCategoryId())
                : new MedalRoundScorePreview(0, Set.of());
        tiedEntryIds = preview.tiedEntryIds();

        removeAll();
        add(createBreadcrumb());
        add(createHeader());
        if (preview.tiedSlotCount() > 0) {
            add(createTiesBanner(preview.tiedSlotCount()));
        }
        add(createGrid());
        add(createSummary());
        add(createBackLink());
        refreshSummary();
    }

    private Span createTiesBanner(int tiedSlotCount) {
        var banner = new Span(getTranslation("medal-round.banner.ties", tiedSlotCount));
        banner.setId("medal-round-ties-banner");
        banner.getStyle().set("color", "var(--lumo-error-text-color)")
                .set("font-weight", "600");
        return banner;
    }

    private Nav createBreadcrumb() {
        var nav = new Nav();
        if (isAdmin) {
            nav.add(new Anchor(isSystemAdmin ? "competitions" : "my-competitions",
                    isSystemAdmin ? getTranslation("nav.competitions")
                            : getTranslation("nav.my-competitions")));
            nav.add(new Span(" / "));
            nav.add(new Anchor("competitions/" + compShortName, competition.getName()));
            nav.add(new Span(" / "));
            nav.add(new Anchor("competitions/" + compShortName + "/divisions/" + divShortName,
                    division.getName()));
            nav.add(new Span(" / "));
            nav.add(new Anchor("competitions/" + compShortName + "/divisions/" + divShortName
                    + "/judging-admin", getTranslation("judging-admin.nav.judging-admin")));
        } else {
            nav.add(new Anchor("my-judging", getTranslation("my-judging.nav.my-judging")));
        }
        nav.add(new Span(" / "));
        nav.add(new Span(getTranslation("medal-round.title", categoryLabel())));
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
                + " — " + getTranslation("medal-round.title", categoryLabel())));

        var header = new VerticalLayout(titleRow);
        header.setPadding(false);
        header.setSpacing(false);

        boolean editable = isAdmin && medalRound != null
                && (currentStatus() == JudgingRoundStatus.PENDING
                        || currentStatus() == JudgingRoundStatus.READY);

        if (editable) {
            header.add(createEditableConfigRow());
        } else {
            header.add(createReadOnlyConfigLines());
        }

        if (isAdmin) {
            header.add(createAdminActions());
        }
        return header;
    }

    private VerticalLayout createReadOnlyConfigLines() {
        var statusLine = new Span(getTranslation("medal-round.mode") + ": "
                + currentMode().name() + " · "
                + getTranslation("medal-round.status") + ": "
                + currentStatus().name());
        statusLine.setId("medal-round-status-line");

        var ptId = currentPhysicalTableId();
        var physicalTableLabel = ptId == null
                ? getTranslation("medal-round.physical-table.unassigned")
                : judgingService.findPhysicalTableById(ptId)
                        .map(PhysicalTable::getLabel)
                        .orElse(getTranslation("medal-round.physical-table.unassigned"));
        var physicalTableLine = new Span(getTranslation("medal-round.physical-table") + ": " + physicalTableLabel);
        physicalTableLine.setId("medal-round-physical-table-line");

        var lines = new VerticalLayout(statusLine, physicalTableLine);
        lines.setPadding(false);
        lines.setSpacing(false);
        return lines;
    }

    /**
     * At PENDING / READY admins can change both the medal-round mode and the
     * physical table. Particularly important for cascade-auto-created medal
     * rounds, which inherit COMPARATIVE and have no physical table assigned.
     */
    private HorizontalLayout createEditableConfigRow() {
        var modeSelect = new Select<MedalRoundMode>();
        modeSelect.setId("medal-round-mode-select");
        modeSelect.setLabel(getTranslation("medal-round.mode"));
        modeSelect.setItems(MedalRoundMode.values());
        modeSelect.setItemLabelGenerator(this::modeLabel);
        modeSelect.setValue(currentMode());
        modeSelect.addValueChangeListener(e -> {
            if (e.getValue() == null || e.getValue() == e.getOldValue()) {
                return;
            }
            try {
                judgingService.updateMedalRoundMode(medalRound.getId(), e.getValue(), currentUserId);
                reload();
                Notification.show(getTranslation("medal-round.mode.updated"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                modeSelect.setValue(e.getOldValue());
            }
        });

        var ptSelect = new Select<PhysicalTable>();
        ptSelect.setId("medal-round-physical-table-select");
        ptSelect.setLabel(getTranslation("medal-round.physical-table"));
        var tables = judgingService.findPhysicalTablesByDivision(division.getId());
        ptSelect.setItems(tables);
        ptSelect.setItemLabelGenerator(pt -> pt == null ? "" : pt.getLabel());
        if (currentPhysicalTableId() != null) {
            tables.stream()
                    .filter(pt -> pt.getId().equals(currentPhysicalTableId()))
                    .findFirst()
                    .ifPresent(ptSelect::setValue);
        }
        if (tables.isEmpty()) {
            ptSelect.setHelperText(getTranslation("medal-round.physical-table.none-defined"));
        }
        ptSelect.addValueChangeListener(e -> {
            if (e.getValue() == null || e.getValue().equals(e.getOldValue())) {
                return;
            }
            try {
                judgingService.assignRoundToPhysicalTable(medalRound.getId(),
                        e.getValue().getId(), currentUserId);
                reload();
                Notification.show(getTranslation("medal-round.physical-table.updated"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                ptSelect.setValue(e.getOldValue());
            }
        });

        var statusBadge = new Span(getTranslation("medal-round.status") + ": " + currentStatus().name());
        statusBadge.setId("medal-round-status-line");

        var row = new HorizontalLayout(modeSelect, ptSelect, statusBadge);
        row.setDefaultVerticalComponentAlignment(Alignment.END);
        row.setSpacing(true);
        return row;
    }

    private String modeLabel(MedalRoundMode mode) {
        return switch (mode) {
            case COMPARATIVE -> getTranslation("medal-round.mode.comparative");
            case SCORE_BASED -> getTranslation("medal-round.mode.score-based");
        };
    }

    private HorizontalLayout createAdminActions() {
        var status = currentStatus();
        boolean judgingActive = judgingService.ensureJudgingExists(division.getId())
                .getPhase() == JudgingPhase.ACTIVE;

        var assignJudgesButton = new Button(getTranslation("medal-round.action.assign-judges"));
        assignJudgesButton.setId("medal-round-assign-judges");
        assignJudgesButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        // Reassignable through PENDING / READY / ACTIVE; only locked at COMPLETE.
        // Removing a judge mid-ACTIVE doesn't undo past medal awards (those carry
        // their own awardedBy) — it just stops further awards from that judge.
        assignJudgesButton.setEnabled(medalRound != null
                && status != JudgingRoundStatus.COMPLETE);
        assignJudgesButton.addClickListener(e -> openAssignJudgesDialog());

        var assignEntriesButton = new Button(getTranslation("medal-round.action.assign-entries"));
        assignEntriesButton.setId("medal-round-assign-entries");
        assignEntriesButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        // Editable through PENDING / READY / ACTIVE; locked at COMPLETE.
        // The cascade populated the initial set per mode; admins can refine
        // (e.g. add a late-arriving entry that a judge forgot to advance, or
        // remove one that shouldn't compete for medals).
        assignEntriesButton.setEnabled(medalRound != null
                && status != JudgingRoundStatus.COMPLETE);
        assignEntriesButton.addClickListener(e -> openAssignEntriesDialog());

        var startButton = new Button(getTranslation("medal-round.action.start"));
        startButton.setId("medal-round-start");
        startButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        startButton.setEnabled(status == JudgingRoundStatus.READY && judgingActive
                && medalRound != null && medalRound.getPhysicalTableId() != null);
        if (status == JudgingRoundStatus.READY
                && (medalRound == null || medalRound.getPhysicalTableId() == null)) {
            startButton.setTooltipText(getTranslation("medal-round.action.start.no-table"));
        }
        startButton.addClickListener(e -> openStartDialog());

        var resetButton = new Button(getTranslation("medal-round.action.reset"));
        resetButton.setId("medal-round-reset");
        resetButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        resetButton.setEnabled(status == JudgingRoundStatus.ACTIVE && judgingActive);
        resetButton.addClickListener(e -> openResetDialog());

        var reopenButton = new Button(getTranslation("medal-round.action.reopen"));
        reopenButton.setId("medal-round-reopen");
        reopenButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        reopenButton.setEnabled(status == JudgingRoundStatus.COMPLETE && judgingActive);
        reopenButton.addClickListener(e -> openReopenDialog());

        var finalizeButton = new Button(getTranslation("medal-round.action.finalize"));
        finalizeButton.setId("medal-round-finalize");
        finalizeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        finalizeButton.setEnabled(status == JudgingRoundStatus.ACTIVE);
        finalizeButton.addClickListener(e -> openFinalizeDialog());

        var actions = new HorizontalLayout(assignJudgesButton, assignEntriesButton,
                startButton, resetButton, reopenButton, finalizeButton);
        actions.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        return actions;
    }

    public void openStartDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("medal-round.start.confirm.title", categoryLabel()));
        var body = currentMode() == MedalRoundMode.SCORE_BASED
                ? getTranslation("medal-round.start.confirm.body.score-based")
                : getTranslation("medal-round.start.confirm.body.comparative");
        dialog.add(new Span(body));
        var confirm = new Button(getTranslation("medal-round.action.start"), e -> {
            try {
                judgingService.startRound(medalRound.getId(), currentUserId);
                dialog.close();
                reload();
                Notification.show(getTranslation("medal-round.started"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        confirm.setId("medal-round-start-confirm");
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        confirm.setDisableOnClick(true);
        var cancel = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    private Grid<MedalRoundEntryRow> createGrid() {
        grid = new Grid<>(MedalRoundEntryRow.class, false);
        grid.setId("medal-round-grid");
        grid.setPartNameGenerator(r ->
                tiedEntryIds.contains(r.entryId()) ? "medal-round-tied-row" : null);
        grid.addColumn(r -> (tiedEntryIds.contains(r.entryId()) ? "⚠ " : "")
                        + r.entryCode() + " — " + r.meadName())
                .setHeader(getTranslation("medal-round.column.entry"));
        grid.addColumn(r -> r.round1Total() == null ? "—" : r.round1Total().toString())
                .setHeader(getTranslation("medal-round.column.total"));
        grid.addColumn(r -> r.advancedToMedalRound() ? "✓" : "—")
                .setHeader(getTranslation("medal-round.column.advanced"));
        grid.addColumn(this::medalLabel)
                .setHeader(getTranslation("medal-round.column.current-medal"));
        grid.addComponentColumn(this::createActionsCell)
                .setHeader(getTranslation("medal-round.column.actions"));
        grid.setItems(rows);
        return grid;
    }

    private HorizontalLayout createActionsCell(MedalRoundEntryRow row) {
        var cell = new HorizontalLayout();
        cell.setPadding(false);
        cell.setSpacing(true);
        cell.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        if (currentStatus() != JudgingRoundStatus.ACTIVE) {
            return cell;
        }
        if (row.entrantUserId() != null && row.entrantUserId().equals(currentUserId)) {
            var blocked = new Span("—");
            blocked.getElement().setProperty("title", getTranslation("medal-round.coi.self.tooltip"));
            cell.add(blocked);
            return cell;
        }

        cell.add(medalButton("🥇", "medal-round.action.award-gold", row, Medal.GOLD));
        cell.add(medalButton("🥈", "medal-round.action.award-silver", row, Medal.SILVER));
        cell.add(medalButton("🥉", "medal-round.action.award-bronze", row, Medal.BRONZE));

        var more = new MenuBar();
        more.addThemeVariants();
        var moreItem = more.addItem(getTranslation("medal-round.action.more"));
        moreItem.getSubMenu().addItem(getTranslation("medal-round.action.withhold"),
                e -> applyMedal(row, null));
        moreItem.getSubMenu().addItem(getTranslation("medal-round.action.clear"),
                e -> clearMedal(row));
        cell.add(more);
        return cell;
    }

    private Button medalButton(String glyph, String tooltipKey, MedalRoundEntryRow row, Medal medal) {
        var button = new Button(glyph, e -> applyMedal(row, medal));
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        button.setTooltipText(getTranslation(tooltipKey));
        return button;
    }

    /** Records or updates a medal for the row. A {@code null} medal records a withhold. */
    public void applyMedal(MedalRoundEntryRow row, Medal medal) {
        try {
            if (row.medalAwardId() == null) {
                judgingService.recordMedal(row.entryId(), medal, currentUserId);
            } else {
                judgingService.updateMedal(row.medalAwardId(), medal, currentUserId);
            }
            reload();
        } catch (BusinessRuleException ex) {
            Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    /** Deletes the medal award row, returning the entry to the "no row" state. */
    public void clearMedal(MedalRoundEntryRow row) {
        if (row.medalAwardId() == null) {
            return;
        }
        try {
            judgingService.deleteMedalAward(row.medalAwardId(), currentUserId);
            reload();
        } catch (BusinessRuleException ex) {
            Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private String medalLabel(MedalRoundEntryRow row) {
        if (row.medalAwardId() == null) {
            return getTranslation("medal-round.medal.none");
        }
        if (row.currentMedal() == null) {
            return getTranslation("medal-round.medal.withheld");
        }
        return switch (row.currentMedal()) {
            case GOLD -> getTranslation("medal-round.medal.gold");
            case SILVER -> getTranslation("medal-round.medal.silver");
            case BRONZE -> getTranslation("medal-round.medal.bronze");
        };
    }

    private Span createSummary() {
        summary = new Span();
        summary.setId("medal-round-summary");
        return summary;
    }

    private void refreshSummary() {
        long gold = rows.stream().filter(r -> r.currentMedal() == Medal.GOLD).count();
        long silver = rows.stream().filter(r -> r.currentMedal() == Medal.SILVER).count();
        long bronze = rows.stream().filter(r -> r.currentMedal() == Medal.BRONZE).count();
        long withheld = rows.stream()
                .filter(r -> r.medalAwardId() != null && r.currentMedal() == null).count();
        long unset = rows.stream().filter(r -> r.medalAwardId() == null).count();
        summary.setText(getTranslation("medal-round.summary",
                gold, silver, bronze, withheld, unset));
    }

    private Anchor createBackLink() {
        var target = isAdmin
                ? "competitions/" + compShortName + "/divisions/" + divShortName + "/judging-admin"
                : "my-judging";
        return new Anchor(target, getTranslation("medal-round.action.back"));
    }

    /**
     * Per-medal-round judge picker. Medal-round judges are independent of the
     * scoring judges for the same category (redesign decision #5) — could be
     * the same panel, could be different (e.g. head judges only). Editable
     * only at PENDING / READY (before the round transitions to ACTIVE).
     */
    public void openAssignJudgesDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("medal-round.action.assign-judges"));
        dialog.setWidth("640px");

        var availableJudges = competitionService.findUsersByRoleInCompetition(
                competition.getId(), app.meads.competition.CompetitionRole.JUDGE);
        var currentlyAssigned = judgingService.findJudgeUserIdsForRound(medalRound.getId())
                .stream().collect(java.util.stream.Collectors.toSet());

        var judgesGrid = new com.vaadin.flow.component.grid.Grid<>(app.meads.identity.User.class, false);
        judgesGrid.setId("medal-round-assign-judges-grid");
        judgesGrid.setSelectionMode(com.vaadin.flow.component.grid.Grid.SelectionMode.MULTI);
        judgesGrid.setAllRowsVisible(true);
        judgesGrid.addColumn(app.meads.identity.User::getName)
                .setHeader(getTranslation("judging-admin.tables.assign.column.name"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        judgesGrid.addColumn(u -> u.getMeaderyName() == null ? "" : u.getMeaderyName())
                .setHeader(getTranslation("judging-admin.tables.assign.column.meadery"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        judgesGrid.addColumn(u -> u.getCountry() == null ? "" : u.getCountry())
                .setHeader(getTranslation("judging-admin.tables.assign.column.country"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        judgesGrid.setItems(availableJudges);
        availableJudges.stream()
                .filter(j -> currentlyAssigned.contains(j.getId()))
                .forEach(j -> judgesGrid.asMultiSelect().select(j));

        dialog.add(judgesGrid);

        var save = new Button(getTranslation("button.save"), e -> {
            var selected = judgesGrid.asMultiSelect().getSelectedItems().stream()
                    .map(app.meads.identity.User::getId)
                    .collect(java.util.stream.Collectors.toSet());
            try {
                for (var judgeId : selected) {
                    if (!currentlyAssigned.contains(judgeId)) {
                        judgingService.assignJudge(medalRound.getId(), judgeId, currentUserId);
                    }
                }
                for (var judgeId : currentlyAssigned) {
                    if (!selected.contains(judgeId)) {
                        judgingService.removeJudge(medalRound.getId(), judgeId, currentUserId);
                    }
                }
                dialog.close();
                reload();
                Notification.show(getTranslation("judging-admin.tables.assign.saved"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.setId("medal-round-assign-judges-save");
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.setDisableOnClick(true);
        var cancel = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    /**
     * Edits the explicit `round.entries` set on the medal round. Pool = all
     * RECEIVED entries in this category with a SUBMITTED scoresheet (the
     * "eligible for medals" set). Cascade auto-population fills this set on
     * scoring-round completion; this dialog lets admins override (e.g. add a
     * late-arriving entry that a judge forgot to advance, or remove one that
     * shouldn't compete for medals).
     */
    public void openAssignEntriesDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("medal-round.assign-entries.dialog.title", categoryLabel()));
        dialog.setWidth("780px");

        // SCORE_BASED medal rounds may run without a preceding scoring round
        // (small-category flow) — in that case the medal round owns the
        // scoresheets, so eligibility doesn't require an existing SUBMITTED
        // sheet. COMPARATIVE keeps the original "must have a SUBMITTED prelim
        // sheet" filter because it picks from advance-flagged sheets only.
        boolean modeIsScoreBased = medalRound != null
                && medalRound.getMedalMode() == MedalRoundMode.SCORE_BASED;
        var eligibleEntries = entryService.findEntriesByFinalCategoryId(category.getId()).stream()
                .filter(e -> e.getStatus() == EntryStatus.RECEIVED)
                .filter(e -> modeIsScoreBased
                        || scoresheetRepository.findByEntryId(e.getId())
                                .map(s -> s.getStatus() == ScoresheetStatus.SUBMITTED)
                                .orElse(false))
                .toList();

        var content = new VerticalLayout();
        content.setPadding(false);
        content.add(new Span(getTranslation("medal-round.assign-entries.helper")));

        if (eligibleEntries.isEmpty()) {
            content.add(new Span(getTranslation("medal-round.assign-entries.empty")));
        }

        var entriesGrid = new Grid<>(Entry.class, false);
        entriesGrid.setId("medal-round-assign-entries-grid");
        entriesGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        entriesGrid.setAllRowsVisible(true);
        entriesGrid.addColumn(e -> e.getEntryCode() + " — " + e.getMeadName())
                .setHeader(getTranslation("medal-round.assign-entries.column.entry"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        entriesGrid.addColumn(e -> {
                    var owner = userService.findById(e.getUserId());
                    return owner.getMeaderyName() == null ? "" : owner.getMeaderyName();
                })
                .setHeader(getTranslation("medal-round.assign-entries.column.meadery"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        entriesGrid.addColumn(e -> {
                    var sheetOpt = scoresheetRepository.findByEntryId(e.getId());
                    return sheetOpt.map(s -> String.valueOf(s.getTotalScore())).orElse("—");
                })
                .setHeader(getTranslation("medal-round.assign-entries.column.total"))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        entriesGrid.setItems(eligibleEntries);
        eligibleEntries.stream()
                .filter(e -> medalRound.getEntries().contains(e.getId()))
                .forEach(e -> entriesGrid.asMultiSelect().select(e));

        content.add(entriesGrid);
        dialog.add(content);

        var save = new Button(getTranslation("button.save"), e -> {
            var selected = entriesGrid.asMultiSelect().getSelectedItems().stream()
                    .map(Entry::getId)
                    .collect(java.util.stream.Collectors.toSet());
            var current = medalRound.getEntries();
            try {
                for (var entryId : selected) {
                    if (!current.contains(entryId)) {
                        judgingService.assignEntryToRound(medalRound.getId(), entryId, currentUserId);
                    }
                }
                for (var entryId : current) {
                    if (!selected.contains(entryId)) {
                        judgingService.unassignEntryFromRound(medalRound.getId(), entryId, currentUserId);
                    }
                }
                dialog.close();
                reload();
                Notification.show(getTranslation("medal-round.assign-entries.saved"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.setId("medal-round-assign-entries-save");
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.setDisableOnClick(true);
        var cancel = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    public void openFinalizeDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("medal-round.finalize.confirm.title", categoryLabel()));
        dialog.add(new Span(getTranslation("medal-round.finalize.confirm.body")));
        var confirm = new Button(getTranslation("medal-round.action.finalize"), e -> {
            try {
                judgingService.completeMedalRoundById(medalRound.getId(), currentUserId);
                dialog.close();
                reload();
                Notification.show(getTranslation("medal-round.finalized"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        confirm.setId("medal-round-finalize-confirm");
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        confirm.setDisableOnClick(true);
        var cancel = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    public void openReopenDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("medal-round.reopen.confirm.title", categoryLabel()));
        dialog.add(new Span(getTranslation("medal-round.reopen.confirm.body")));
        var confirm = new Button(getTranslation("medal-round.action.reopen"), e -> {
            try {
                judgingService.reopenMedalRoundById(medalRound.getId(), currentUserId);
                dialog.close();
                reload();
                Notification.show(getTranslation("medal-round.reopened"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        confirm.setId("medal-round-reopen-confirm");
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        confirm.setDisableOnClick(true);
        var cancel = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    public void openResetDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("medal-round.reset.confirm.title", categoryLabel()));
        var awards = judgingService.findMedalAwardsForCategory(config.getDivisionCategoryId());
        dialog.add(new Span(getTranslation("medal-round.reset.confirm.body", awards.size())));
        var confirmField = new TextField(getTranslation("medal-round.reset.confirm.label"));
        confirmField.setId("medal-round-reset-field");
        confirmField.setWidthFull();
        dialog.add(confirmField);
        var confirm = new Button(getTranslation("medal-round.action.reset"), e -> {
            if (!"RESET".equals(confirmField.getValue())) {
                confirmField.setInvalid(true);
                confirmField.setErrorMessage(getTranslation("medal-round.reset.confirm.error"));
                return;
            }
            try {
                judgingService.resetMedalRoundById(medalRound.getId(), currentUserId);
                dialog.close();
                reload();
                Notification.show(getTranslation("medal-round.reset.done"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (BusinessRuleException ex) {
                Notification.show(getTranslation(ex.getMessageKey(), ex.getParams()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        confirm.setId("medal-round-reset-confirm");
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        var cancel = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    private String categoryLabel() {
        return category.getCode() + " " + category.getName();
    }

    /**
     * Effective medal mode: prefer the medal {@link JudgingRound}'s mode;
     * fall back to the category-level mode in {@link CategoryJudgingConfig}
     * when a medal round hasn't been created yet (no entries for the category
     * have been judged).
     */
    private MedalRoundMode currentMode() {
        if (medalRound != null && medalRound.getMedalMode() != null) {
            return medalRound.getMedalMode();
        }
        return config.getMedalRoundMode();
    }

    private JudgingRoundStatus currentStatus() {
        return medalRound != null ? medalRound.getStatus() : JudgingRoundStatus.PENDING;
    }

    private UUID currentPhysicalTableId() {
        return medalRound != null ? medalRound.getPhysicalTableId() : null;
    }

    private UUID getCurrentUserId() {
        return authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(userDetails -> userService.findByEmail(userDetails.getUsername()).getId())
                .orElse(null);
    }
}
