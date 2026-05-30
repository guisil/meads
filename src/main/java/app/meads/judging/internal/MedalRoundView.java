package app.meads.judging.internal;

import app.meads.BusinessRuleException;
import app.meads.MainLayout;
import app.meads.competition.Competition;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
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
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
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
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
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
                          ScoresheetRepository scoresheetRepository,
                          AuthenticationContext authenticationContext) {
        this.competitionService = competitionService;
        this.userService = userService;
        this.judgingService = judgingService;
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

        // Judges only see the ACTIVE medal round they're working. Admins
        // retain full access at any status.
        if (!isAdmin) {
            var medalRoundForGate = judgingService.findMedalRoundByCategoryId(divisionCategoryId)
                    .orElse(null);
            if (medalRoundForGate == null
                    || medalRoundForGate.getStatus() != JudgingRoundStatus.ACTIVE) {
                event.forwardTo("");
                return;
            }
        }

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
        // A little breathing room between the title, the table/type/status row,
        // and the explanation line.
        header.getStyle().set("gap", "var(--lumo-space-s)");

        boolean editable = isAdmin && medalRound != null
                && (currentStatus() == JudgingRoundStatus.PENDING
                        || currentStatus() == JudgingRoundStatus.READY);

        if (editable) {
            header.add(createEditableConfigRow());
        } else {
            header.add(createReadOnlyConfigLines());
        }

        header.add(createExplanation());

        var actions = createActions();
        if (actions != null) {
            header.add(actions);
        }
        return header;
    }

    /**
     * Read-only info row: Table first, then a colored Type badge (matching the
     * Rounds grid), then Status — but Status is shown to admins only (a judge
     * only ever lands here on an ACTIVE round, so the status is implicit).
     */
    private HorizontalLayout createReadOnlyConfigLines() {
        var ptId = currentPhysicalTableId();
        var physicalTableLabel = ptId == null
                ? getTranslation("medal-round.physical-table.unassigned")
                : judgingService.findPhysicalTableById(ptId)
                        .map(PhysicalTable::getLabel)
                        .orElse(getTranslation("medal-round.physical-table.unassigned"));
        var physicalTableLine = new Span(getTranslation("medal-round.physical-table") + ": " + physicalTableLabel);
        physicalTableLine.setId("medal-round-physical-table-line");

        var row = new HorizontalLayout(physicalTableLine, roundTypeBadge());
        row.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        row.setSpacing(true);
        if (isAdmin) {
            var statusBadge = new Span(getTranslation("medal-round.status") + ": " + currentStatus().name());
            statusBadge.setId("medal-round-status-line");
            row.add(statusBadge);
        }
        return row;
    }

    /** Colored Type badge mirroring the Rounds grid (Score-based = success, Comparative = primary). */
    private Span roundTypeBadge() {
        var label = getTranslation("judging-admin.rounds.type.medal") + " — "
                + getTranslation(currentMode() == MedalRoundMode.SCORE_BASED
                        ? "medal-round.mode.score-based" : "medal-round.mode.comparative");
        var badge = new Span(label);
        badge.setId("medal-round-type-badge");
        badge.getElement().getThemeList().add(currentMode() == MedalRoundMode.SCORE_BASED
                ? "badge success" : "badge primary");
        return badge;
    }

    /**
     * What happens in this round — varies by medal mode. Admins get a
     * third-person variant (they observe; the judges do the scoring/comparing).
     */
    private Span createExplanation() {
        var base = currentMode() == MedalRoundMode.SCORE_BASED
                ? "round.explanation.medal-score-based"
                : "round.explanation.medal-comparative";
        var span = new Span(getTranslation(isAdmin ? base + ".admin" : base));
        span.setId("round-explanation");
        span.getStyle().set("color", "var(--lumo-secondary-text-color)");
        return span;
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

        var row = new HorizontalLayout(ptSelect, modeSelect, statusBadge);
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

    /**
     * Round-level actions. Assign Judges / Assign Entries / Start / Revert /
     * Delete all live inline on the unified Rounds grid now — this view keeps
     * only the medal-specific lifecycle: Finalize (and admin Reopen). For a
     * SCORE_BASED round the judge runs the whole scoring + Finalize end-to-end
     * (no admin hand-off); a COMPARATIVE round's Finalize stays admin-only.
     */
    private HorizontalLayout createActions() {
        var status = currentStatus();
        boolean scoreBased = currentMode() == MedalRoundMode.SCORE_BASED;
        boolean judgingActive = judgingService.ensureJudgingExists(division.getId())
                .getPhase() == JudgingPhase.ACTIVE;

        var bar = new HorizontalLayout();
        bar.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        bar.setWidthFull();

        boolean showFinalize = medalRound != null && (scoreBased || isAdmin);
        if (showFinalize) {
            var finalize = new Button(getTranslation("medal-round.action.finalize"), e -> openFinalizeDialog());
            finalize.setId("medal-round-finalize");
            finalize.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
            if (scoreBased) {
                // SCORE_BASED: only fireable once every sheet is FILLED and no
                // tie is open — the service enforces the same, but disabling +
                // a tooltip makes the gate obvious to the judge.
                boolean ready = status == JudgingRoundStatus.ACTIVE
                        && allSheetsFilled() && tiedEntryIds.isEmpty();
                finalize.setEnabled(ready);
                var wrapper = new Span(finalize);
                String tip = ready ? getTranslation("medal-round.action.finalize")
                        : !allSheetsFilled()
                                ? getTranslation("error.medal-round.cannot-finalize-unfilled")
                                : getTranslation("error.medal-round.cannot-finalize-tied");
                com.vaadin.flow.component.shared.Tooltip.forComponent(wrapper).setText(tip);
                bar.add(wrapper);
            } else {
                finalize.setEnabled(status == JudgingRoundStatus.ACTIVE);
                bar.add(finalize);
            }
        }

        if (isAdmin) {
            var reopenButton = new Button(getTranslation("medal-round.action.reopen"));
            reopenButton.setId("medal-round-reopen");
            reopenButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            reopenButton.setEnabled(status == JudgingRoundStatus.COMPLETE && judgingActive);
            reopenButton.addClickListener(e -> openReopenDialog());
            bar.add(reopenButton);
        }

        // Medal tally sits on the right of the action row, above the grid, in
        // bold so the standings are visible at a glance while finalizing.
        var summarySpan = createSummary();
        summarySpan.getStyle().set("font-weight", "bold");
        summarySpan.getStyle().set("margin-left", "auto");
        bar.add(summarySpan);
        return bar;
    }

    private boolean allSheetsFilled() {
        if (medalRound == null) {
            return false;
        }
        var sheets = scoresheetRepository.findByRoundId(medalRound.getId());
        return !sheets.isEmpty()
                && sheets.stream().allMatch(s -> s.getStatus() == ScoresheetStatus.FILLED);
    }

    private Grid<MedalRoundEntryRow> createGrid() {
        grid = new Grid<>(MedalRoundEntryRow.class, false);
        grid.setId("medal-round-grid");
        // Grow to fit all rows rather than capping at a fixed height with an
        // internal scrollbar — categories can have more entries than the
        // default viewport (matches JudgingAdminView's grids).
        grid.setAllRowsVisible(true);
        grid.setPartNameGenerator(r ->
                tiedEntryIds.contains(r.entryId()) ? "medal-round-tied-row" : null);
        // Columns are resizable + sortable so admins can lay them out how they
        // like and re-sort independently of the service's default order. The
        // sort comparators look at the underlying record fields directly so
        // numeric / null-safe ordering works (the rendered "—" placeholder
        // would otherwise sort alphabetically).
        // Anonymity rule applies on MedalRoundView too: judges assigned to an
        // ACTIVE medal round (small-category SCORE_BASED flow) DO open this
        // view. Entry # (admin cross-reference) and Mead Name (entrant brand
        // label) are admin-only; judges see just the anonymized Code column.
        if (isAdmin) {
            grid.addColumn(r -> formatEntryNumber(r.entryNumber()))
                    .setHeader(getTranslation("medal-round.column.entry-number"))
                    .setComparator(java.util.Comparator.comparingInt(MedalRoundEntryRow::entryNumber))
                    .setResizable(true).setSortable(true).setAutoWidth(true);
        }
        grid.addColumn(r -> (tiedEntryIds.contains(r.entryId()) ? "⚠ " : "")
                        + r.entryCode())
                .setHeader(getTranslation("medal-round.column.entry-code"))
                .setComparator(java.util.Comparator.comparing(MedalRoundEntryRow::entryCode))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        if (isAdmin) {
            grid.addColumn(MedalRoundEntryRow::meadName)
                    .setHeader(getTranslation("medal-round.column.mead-name"))
                    .setComparator(java.util.Comparator.comparing(MedalRoundEntryRow::meadName,
                            String.CASE_INSENSITIVE_ORDER))
                    .setResizable(true).setSortable(true).setAutoWidth(true);
        }
        grid.addColumn(r -> r.scoresheetStatus() == null ? "—" : r.scoresheetStatus().name())
                .setHeader(getTranslation("medal-round.status"))
                .setComparator(java.util.Comparator.comparing(
                        r -> r.scoresheetStatus() == null ? "" : r.scoresheetStatus().name()))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        grid.addColumn(r -> r.round1Total() == null ? "—" : r.round1Total().toString())
                .setHeader(getTranslation("medal-round.column.total"))
                .setComparator(java.util.Comparator.comparing(MedalRoundEntryRow::round1Total,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        grid.addColumn(this::medalLabel)
                .setHeader(getTranslation("medal-round.column.current-medal"))
                .setComparator(java.util.Comparator.comparing(this::medalLabel))
                .setResizable(true).setSortable(true).setAutoWidth(true);
        grid.addComponentColumn(this::createActionsCell)
                .setHeader(getTranslation("medal-round.column.actions"))
                .setResizable(true).setAutoWidth(true);
        grid.setItems(rows);
        return grid;
    }

    private HorizontalLayout createActionsCell(MedalRoundEntryRow row) {
        var cell = new HorizontalLayout();
        cell.setPadding(false);
        cell.setSpacing(true);
        cell.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        // Open scoresheet drill-in: visible whenever the entry has a sheet,
        // regardless of round status. ScoresheetView enforces its own access
        // rules (admin or assigned judge). For the small-category SCORE_BASED
        // flow this is the admin's path to view/edit sheets during scoring.
        if (row.scoresheetId() != null) {
            var openButton = new Button(new Icon(VaadinIcon.EYE));
            openButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
            openButton.setId("medal-round-open-scoresheet-" + row.scoresheetId());
            openButton.setTooltipText(getTranslation("medal-round.action.open-scoresheet"));
            openButton.addClickListener(e -> navigateToScoresheet(row.scoresheetId()));
            cell.add(openButton);
        }

        if (currentStatus() != JudgingRoundStatus.ACTIVE) {
            return cell;
        }
        if (row.entrantUserId() != null && row.entrantUserId().equals(currentUserId)) {
            var blocked = new Span("—");
            blocked.getElement().setProperty("title", getTranslation("medal-round.coi.self.tooltip"));
            cell.add(blocked);
            return cell;
        }

        // In SCORE_BASED, medals must wait for a SUBMITTED sheet — premature
        // gold/silver/bronze on unscored entries doesn't reflect reality, and
        // the @EventListener on ScoresheetSubmittedEvent re-runs autoPopulate
        // once every sheet on the round is SUBMITTED so medals appear
        // automatically. COMPARATIVE rows always have non-null totals (filter
        // upstream), so the gate is a no-op there.
        boolean scoreBasedPending = currentMode() == MedalRoundMode.SCORE_BASED
                && row.round1Total() == null;
        cell.add(medalButton("🥇", "medal-round.action.award-gold", row, Medal.GOLD,
                scoreBasedPending));
        cell.add(medalButton("🥈", "medal-round.action.award-silver", row, Medal.SILVER,
                scoreBasedPending));
        cell.add(medalButton("🥉", "medal-round.action.award-bronze", row, Medal.BRONZE,
                scoreBasedPending));

        // Withhold + Clear — sensitive actions, gated behind a ConfirmDialog.
        // Withhold records an explicit no-medal decision (audit row stays);
        // Clear deletes the audit row entirely.
        var withhold = new Button(new Icon(VaadinIcon.BAN));
        withhold.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        withhold.setTooltipText(getTranslation("medal-round.action.withhold"));
        withhold.setEnabled(!scoreBasedPending);
        withhold.addClickListener(e -> openWithholdConfirmDialog(row));
        cell.add(withhold);

        var clear = new Button(new Icon(VaadinIcon.TRASH));
        clear.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        clear.setTooltipText(getTranslation("medal-round.action.clear"));
        clear.setEnabled(row.medalAwardId() != null);
        clear.addClickListener(e -> openClearConfirmDialog(row));
        cell.add(clear);
        return cell;
    }

    /** Opens a confirmation dialog before recording an explicit withhold. */
    public void openWithholdConfirmDialog(MedalRoundEntryRow row) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("medal-round.action.withhold.confirm.title"));
        dialog.add(new Span(getTranslation("medal-round.action.withhold.confirm.body")));
        var confirm = new Button(getTranslation("medal-round.action.withhold.confirm.proceed"),
                e -> { applyMedal(row, null); dialog.close(); });
        confirm.setId("medal-round-withhold-confirm");
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        var cancel = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    /** Opens a confirmation dialog before deleting the medal award row entirely. */
    public void openClearConfirmDialog(MedalRoundEntryRow row) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("medal-round.action.clear.confirm.title"));
        dialog.add(new Span(getTranslation("medal-round.action.clear.confirm.body")));
        var confirm = new Button(getTranslation("medal-round.action.clear.confirm.proceed"),
                e -> { clearMedal(row); dialog.close(); });
        confirm.setId("medal-round-clear-confirm");
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        var cancel = new Button(getTranslation("button.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    private Button medalButton(String glyph, String tooltipKey, MedalRoundEntryRow row, Medal medal,
                                boolean pendingScoresheet) {
        var button = new Button(glyph, e -> applyMedal(row, medal));
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        button.setTooltipText(getTranslation(pendingScoresheet
                ? "medal-round.action.medals-await-scoresheet"
                : tooltipKey));
        button.setEnabled(!pendingScoresheet);
        return button;
    }

    private void navigateToScoresheet(java.util.UUID scoresheetId) {
        var url = "competitions/" + compShortName
                + "/divisions/" + divShortName
                + "/scoresheets/" + scoresheetId;
        getUI().ifPresent(ui -> ui.navigate(url));
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

    public void openFinalizeDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("medal-round.finalize.confirm.title", categoryLabel()));
        var body = new VerticalLayout();
        body.setPadding(false);
        body.add(new Span(getTranslation("medal-round.finalize.confirm.body")));
        // List the medals being committed so the admin sees the outcome. The
        // service blocks finalize if any entry in scope is still undecided.
        var awards = judgingService.findMedalAwardsForCategory(config.getDivisionCategoryId());
        long gold = awards.stream().filter(a -> a.getMedal() == Medal.GOLD).count();
        long silver = awards.stream().filter(a -> a.getMedal() == Medal.SILVER).count();
        long bronze = awards.stream().filter(a -> a.getMedal() == Medal.BRONZE).count();
        body.add(new Span(getTranslation("medal-round.finalize.confirm.summary", gold, silver, bronze)));
        if (isAdmin) {
            body.add(new Span(getTranslation("medal-round.finalize.confirm.admin-warning")));
        }
        dialog.add(body);
        var confirm = new Button(getTranslation("medal-round.action.finalize"), e -> {
            try {
                // SCORE_BASED finalize (judge-or-admin) submits the sheets and
                // completes in one step; COMPARATIVE commits the manually-awarded
                // medals via the admin-only complete path.
                if (currentMode() == MedalRoundMode.SCORE_BASED) {
                    judgingService.finalizeMedalRound(medalRound.getId(), currentUserId);
                } else {
                    judgingService.completeMedalRoundById(medalRound.getId(), currentUserId);
                }
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

    private String categoryLabel() {
        return category.getCode() + " " + category.getName();
    }

    private String formatEntryNumber(int entryNumber) {
        var prefix = division.getEntryPrefix();
        if (prefix != null && !prefix.isBlank()) {
            return prefix + "-" + entryNumber;
        }
        return String.valueOf(entryNumber);
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
