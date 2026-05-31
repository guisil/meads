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
import app.meads.entry.EntryService;
import app.meads.identity.User;
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
import java.util.stream.Collectors;

/**
 * Shared judge/admin medal-round form for one JUDGING-scope category. Judges and
 * admins award Gold / Silver / Bronze (or clear an award) per entry; entries
 * left without a medal simply receive none at finalize. See design §4.E.
 */
@Route(value = "competitions/:compShortName/divisions/:divShortName/medal-rounds/:divisionCategoryId",
        layout = MainLayout.class)
@PermitAll
public class MedalRoundView extends VerticalLayout implements BeforeEnterObserver {

    private final CompetitionService competitionService;
    private final UserService userService;
    private final JudgingService judgingService;
    private final ScoresheetRepository scoresheetRepository;
    private final EntryService entryService;
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
                          EntryService entryService,
                          AuthenticationContext authenticationContext) {
        this.competitionService = competitionService;
        this.userService = userService;
        this.judgingService = judgingService;
        this.scoresheetRepository = scoresheetRepository;
        this.entryService = entryService;
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
        if (preview.tiedEntryCount() > 0) {
            add(createTiesBanner(preview.tiedEntryCount()));
        }
        add(createGrid());
        add(createBackLink());
        refreshSummary();
    }

    private Span createTiesBanner(int tiedEntryCount) {
        var banner = new Span(getTranslation("medal-round.banner.ties", tiedEntryCount));
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

        // The header is always read-only and mirrors the scoring RoundView:
        // type badge + status on the left, table on the right. All round
        // configuration (table, mode, schedule, judges) lives on the unified
        // Rounds grid's Edit / Assign Judges dialogs (§12.6).
        header.add(createReadOnlyConfigLines());

        // Admins get the assigned-judge roster spelled out — useful once the
        // round is COMPLETE and the Assign Judges dialog is locked.
        if (isAdmin && medalRound != null) {
            header.add(createJudgesLine());
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

        var categoryBadge = RoundBadges.categoryBadge(category.getCode(), category.getName(),
                getTranslation("judging-admin.rounds.column.category"));
        categoryBadge.setId("medal-round-category-badge");

        var row = new HorizontalLayout(roundTypeBadge(), categoryBadge);
        row.setWidthFull();
        row.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        row.setSpacing(true);
        if (isAdmin) {
            var statusBadge = new Span(getTranslation("medal-round.status") + ": " + currentStatus().name());
            statusBadge.setId("medal-round-status-line");
            row.add(statusBadge);
        }
        // Table info sits at the far right of the info row.
        physicalTableLine.getStyle().set("margin-left", "auto");
        row.add(physicalTableLine);
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

    /** Admin-only roster of the medal round's assigned judges (names, comma-separated). */
    private Span createJudgesLine() {
        var names = judgingService.findJudgeUserIdsForRound(medalRound.getId()).stream()
                .map(userService::findById)
                .map(User::getName)
                .sorted()
                .collect(Collectors.joining(", "));
        var line = new Span(getTranslation("round.judges") + ": "
                + (names.isEmpty() ? "—" : names));
        line.setId("medal-round-judges-line");
        // Right-aligned on its own line below the info row, under the Table info.
        line.getStyle().set("align-self", "flex-end");
        return line;
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

        // Finalize is judge-or-admin in both modes: in SCORE_BASED the judge runs
        // the whole flow; in COMPARATIVE the judges award the medals, so they can
        // commit them too (the service authorizes an assigned judge or an admin).
        boolean showFinalize = medalRound != null;
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
        boolean scoreBased = currentMode() == MedalRoundMode.SCORE_BASED;
        // Scoresheet status only means something for a SCORE_BASED round, where
        // the medal round owns the sheets (BLANK → FILLED → SUBMITTED). On a
        // COMPARATIVE round the sheets live on the prelim scoring rounds and are
        // always SUBMITTED here, so the column is noise — hide it.
        if (scoreBased) {
            grid.addColumn(r -> r.scoresheetStatus() == null ? "—" : r.scoresheetStatus().name())
                    .setHeader(getTranslation("medal-round.status"))
                    .setComparator(java.util.Comparator.comparing(
                            r -> r.scoresheetStatus() == null ? "" : r.scoresheetStatus().name()))
                    .setResizable(true).setSortable(true).setAutoWidth(true);
        }
        // Total drives the medals in SCORE_BASED (everyone sees it). In
        // COMPARATIVE, judges award medals by tasting, independently of the
        // prelim scores — so the prelim total is hidden from judges (admins keep
        // it for context / results review).
        if (scoreBased || isAdmin) {
            grid.addColumn(r -> r.round1Total() == null ? "—" : r.round1Total().toString())
                    .setHeader(getTranslation("medal-round.column.total"))
                    .setComparator(java.util.Comparator.comparing(MedalRoundEntryRow::round1Total,
                            java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                    .setResizable(true).setSortable(true).setAutoWidth(true);
        }
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

    /** Opens the read-only mead-details dialog for an entry. Public for testability
     *  (the per-row eye button lives in a Grid component column). */
    public void openMeadDetailsDialog(UUID entryId) {
        new MeadDetailsDialog(entryService.findEntryById(entryId)).open();
    }

    private HorizontalLayout createActionsCell(MedalRoundEntryRow row) {
        var cell = new HorizontalLayout();
        cell.setPadding(false);
        cell.setSpacing(true);
        cell.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        // View mead details (eye) — available to everyone on every row: the
        // entry's objective characteristics, no scores/comments, no brand name.
        // Lets a COMPARATIVE judge see what they're tasting without the prelim
        // scoresheet (which they can't open).
        var meadDetailsButton = new Button(new Icon(VaadinIcon.EYE));
        meadDetailsButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        meadDetailsButton.setId("medal-round-mead-details-" + row.entryId());
        meadDetailsButton.setTooltipText(getTranslation("round.action.mead-details"));
        meadDetailsButton.addClickListener(e -> openMeadDetailsDialog(row.entryId()));
        cell.add(meadDetailsButton);

        // Open scoresheet drill-in (pencil = edit). Admins always get it
        // (view/edit any sheet). Judges get it only in SCORE_BASED, where they
        // own + score the sheet on this very round; in COMPARATIVE the sheet
        // belongs to a prelim scoring round the judge isn't on — ScoresheetView
        // would just forward them away (and they award medals by tasting, not by
        // reading the sheet), so don't show a dead icon.
        boolean canOpenScoresheet = isAdmin || currentMode() == MedalRoundMode.SCORE_BASED;
        if (row.scoresheetId() != null && canOpenScoresheet) {
            var openButton = new Button(new Icon(VaadinIcon.PENCIL));
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

        // Clear removes a medal award — a sensitive action gated behind a
        // ConfirmDialog. (There is no Withhold: an entry with no medal is simply
        // not awarded one; finalize leaves it without a medal.)
        var clear = new Button(new Icon(VaadinIcon.TRASH));
        clear.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
        clear.setTooltipText(getTranslation("medal-round.action.clear"));
        clear.setEnabled(row.medalAwardId() != null);
        clear.addClickListener(e -> openClearConfirmDialog(row));
        cell.add(clear);
        return cell;
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

    /** Records or updates a medal for the row. */
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
        if (row.currentMedal() == null) {
            return getTranslation("medal-round.medal.none");
        }
        // Prefix the medal icon (matching the award buttons) before the label.
        return switch (row.currentMedal()) {
            case GOLD -> "🥇 " + getTranslation("medal-round.medal.gold");
            case SILVER -> "🥈 " + getTranslation("medal-round.medal.silver");
            case BRONZE -> "🥉 " + getTranslation("medal-round.medal.bronze");
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
        long noMedal = rows.size() - gold - silver - bronze;
        summary.setText(getTranslation("medal-round.summary",
                gold, silver, bronze, noMedal));
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
        // List the medals being committed so the admin sees the outcome, and
        // make the "left without a medal" count explicit — finalize no longer
        // requires every entry to be decided, so entries with no medal award
        // are committed as receiving no medal.
        var awards = judgingService.findMedalAwardsForCategory(config.getDivisionCategoryId());
        long gold = awards.stream().filter(a -> a.getMedal() == Medal.GOLD).count();
        long silver = awards.stream().filter(a -> a.getMedal() == Medal.SILVER).count();
        long bronze = awards.stream().filter(a -> a.getMedal() == Medal.BRONZE).count();
        long awarded = gold + silver + bronze;
        long noMedal = Math.max(0, rows.size() - awarded);
        body.add(new Span(getTranslation("medal-round.finalize.confirm.summary", gold, silver, bronze)));
        if (noMedal > 0) {
            var noMedalLine = new Span(getTranslation("medal-round.finalize.confirm.no-medal", noMedal));
            noMedalLine.getStyle().set("font-weight", "bold");
            body.add(noMedalLine);
        }
        if (isAdmin) {
            // Reopen is admin-only — only reassure admins about reversibility.
            body.add(new Span(getTranslation("medal-round.finalize.confirm.body")));
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
