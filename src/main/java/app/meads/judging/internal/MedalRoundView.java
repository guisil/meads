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
import app.meads.judging.JudgingService;
import app.meads.judging.Medal;
import app.meads.judging.MedalRoundEntryRow;
import app.meads.judging.MedalRoundMode;
import app.meads.judging.MedalRoundScorePreview;
import app.meads.judging.MedalRoundStatus;
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
import com.vaadin.flow.component.textfield.TextField;
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
    private final transient AuthenticationContext authenticationContext;

    private Competition competition;
    private Division division;
    private DivisionCategory category;
    private CategoryJudgingConfig config;
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
                          AuthenticationContext authenticationContext) {
        this.competitionService = competitionService;
        this.userService = userService;
        this.judgingService = judgingService;
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

    /** Re-fetches the config + rows and rebuilds the whole view. */
    private void reload() {
        config = judgingService.findCategoryConfigByDivisionCategoryId(
                config.getDivisionCategoryId()).orElse(config);
        rows = judgingService.findMedalRoundEntries(
                config.getDivisionCategoryId(), config.getMedalRoundMode());
        var preview = config.getMedalRoundMode() == MedalRoundMode.SCORE_BASED
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

        var statusLine = new Span(getTranslation("medal-round.mode") + ": "
                + config.getMedalRoundMode().name() + " · "
                + getTranslation("medal-round.status") + ": "
                + config.getMedalRoundStatus().name());
        statusLine.setId("medal-round-status-line");

        var physicalTableLabel = config.getPhysicalTableId() == null
                ? getTranslation("medal-round.physical-table.unassigned")
                : judgingService.findPhysicalTableById(config.getPhysicalTableId())
                        .map(app.meads.judging.PhysicalTable::getLabel)
                        .orElse(getTranslation("medal-round.physical-table.unassigned"));
        var physicalTableLine = new Span(getTranslation("medal-round.physical-table") + ": " + physicalTableLabel);
        physicalTableLine.setId("medal-round-physical-table-line");

        var header = new VerticalLayout(titleRow, statusLine, physicalTableLine);
        header.setPadding(false);
        header.setSpacing(false);

        if (isAdmin) {
            header.add(createAdminActions());
        }
        return header;
    }

    private HorizontalLayout createAdminActions() {
        var status = config.getMedalRoundStatus();
        boolean judgingActive = judgingService.ensureJudgingExists(division.getId())
                .getPhase() == JudgingPhase.ACTIVE;

        var resetButton = new Button(getTranslation("medal-round.action.reset"));
        resetButton.setId("medal-round-reset");
        resetButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        resetButton.setEnabled(status == MedalRoundStatus.ACTIVE && judgingActive);
        resetButton.addClickListener(e -> openResetDialog());

        var reopenButton = new Button(getTranslation("medal-round.action.reopen"));
        reopenButton.setId("medal-round-reopen");
        reopenButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        reopenButton.setEnabled(status == MedalRoundStatus.COMPLETE && judgingActive);
        reopenButton.addClickListener(e -> openReopenDialog());

        var finalizeButton = new Button(getTranslation("medal-round.action.finalize"));
        finalizeButton.setId("medal-round-finalize");
        finalizeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        finalizeButton.setEnabled(status == MedalRoundStatus.ACTIVE);
        finalizeButton.addClickListener(e -> openFinalizeDialog());

        var actions = new HorizontalLayout(resetButton, reopenButton, finalizeButton);
        actions.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        return actions;
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

        if (config.getMedalRoundStatus() != MedalRoundStatus.ACTIVE) {
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

    public void openFinalizeDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("medal-round.finalize.confirm.title", categoryLabel()));
        dialog.add(new Span(getTranslation("medal-round.finalize.confirm.body")));
        var confirm = new Button(getTranslation("medal-round.action.finalize"), e -> {
            try {
                judgingService.completeMedalRound(config.getDivisionCategoryId(), currentUserId);
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
                judgingService.reopenMedalRound(config.getDivisionCategoryId(), currentUserId);
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
                judgingService.resetMedalRound(config.getDivisionCategoryId(), currentUserId);
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

    private UUID getCurrentUserId() {
        return authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(userDetails -> userService.findByEmail(userDetails.getUsername()).getId())
                .orElse(null);
    }
}
