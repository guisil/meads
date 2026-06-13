package app.meads.awards.internal;

import app.meads.BusinessRuleException;
import app.meads.MainLayout;
import app.meads.awards.AwardsService;
import app.meads.awards.EntrantResultRow;
import app.meads.competition.Competition;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionStatus;
import app.meads.identity.UserService;
import app.meads.judging.AnonymizationLevel;
import app.meads.judging.Medal;
import app.meads.judging.ScoresheetPdfService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.ByteArrayInputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Route(value = "competitions/:compShortName/divisions/:divShortName/my-results", layout = MainLayout.class)
@PermitAll
public class MyResultsView extends VerticalLayout implements BeforeEnterObserver {

    private final AwardsService awardsService;
    private final CompetitionService competitionService;
    private final UserService userService;
    private final ScoresheetPdfService scoresheetPdfService;
    private final transient AuthenticationContext authenticationContext;

    private Grid<EntrantResultRow> grid;
    private List<EntrantResultRow> allRows = List.of();

    public MyResultsView(AwardsService awardsService,
                         CompetitionService competitionService,
                         UserService userService,
                         ScoresheetPdfService scoresheetPdfService,
                         AuthenticationContext authenticationContext) {
        this.awardsService = awardsService;
        this.competitionService = competitionService;
        this.userService = userService;
        this.scoresheetPdfService = scoresheetPdfService;
        this.authenticationContext = authenticationContext;
        setSizeFull();
        setPadding(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var compShortName = event.getRouteParameters().get("compShortName").orElse(null);
        var divShortName = event.getRouteParameters().get("divShortName").orElse(null);
        if (compShortName == null || divShortName == null) {
            event.forwardTo("");
            return;
        }
        Competition competition;
        Division division;
        try {
            competition = competitionService.findCompetitionByShortName(compShortName);
            division = competitionService.findDivisionByShortName(competition.getId(), divShortName);
        } catch (BusinessRuleException e) {
            event.forwardTo("");
            return;
        }
        if (division.getStatus() != DivisionStatus.RESULTS_PUBLISHED) {
            event.forwardTo("competitions/" + compShortName + "/divisions/" + divShortName + "/my-entries");
            return;
        }
        var currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            event.forwardTo("login");
            return;
        }
        try {
            var rows = awardsService.getResultsForEntrant(currentUserId, division.getId());
            render(competition, division, rows, compShortName, divShortName, currentUserId);
        } catch (BusinessRuleException e) {
            event.forwardTo("competitions/" + compShortName + "/divisions/" + divShortName + "/my-entries");
        }
    }

    private void render(Competition competition, Division division, List<EntrantResultRow> rows,
                        String compShortName, String divShortName, UUID currentUserId) {
        removeAll();
        allRows = rows;

        var heading = new H2(competition.getName() + " — " + division.getName()
                + " — " + getTranslation("my-results.title"));
        heading.setId("my-results-heading");
        var header = new HorizontalLayout();
        header.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        if (competition.hasLogo()) {
            var logo = new Image(logoDataUri(competition), competition.getName() + " logo");
            logo.setId("my-results-logo");
            logo.setHeight("64px");
            header.add(logo);
        }
        header.add(heading);
        add(header);

        var search = new TextField();
        search.setId("my-results-search");
        search.setPlaceholder(getTranslation("my-results.search.placeholder"));
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.EAGER);
        search.setWidth("280px");
        search.addValueChangeListener(e -> applyFilter(e.getValue()));

        var spacer = new Span();
        var toolbar = new HorizontalLayout(search, spacer, downloadAllComponent(rows, currentUserId));
        toolbar.setWidthFull();
        toolbar.setDefaultVerticalComponentAlignment(Alignment.END);
        toolbar.setFlexGrow(1, spacer);
        add(toolbar);

        grid = new Grid<>();
        grid.setId("my-results-grid");
        grid.setAllRowsVisible(true);

        grid.addColumn(EntrantResultRow::entryNumber)
                .setHeader(getTranslation("my-results.column.entry"))
                .setResizable(true).setSortable(true)
                .setWidth("110px").setFlexGrow(0);
        // Mead Name takes the slack so long names fit.
        grid.addColumn(EntrantResultRow::meadName)
                .setHeader(getTranslation("my-results.column.mead-name"))
                .setResizable(true).setSortable(true)
                .setTooltipGenerator(EntrantResultRow::meadName)
                .setFlexGrow(1);
        // Final category: code only, with the full name in a hover tooltip.
        grid.addColumn(EntrantResultRow::categoryCode)
                .setHeader(getTranslation("my-results.column.category"))
                .setResizable(true).setSortable(true)
                .setTooltipGenerator(EntrantResultRow::categoryName)
                .setWidth("160px").setFlexGrow(0);
        grid.addColumn(r -> r.round1Total() == null ? "—" : (r.round1Total() + " / 100"))
                .setHeader(getTranslation("my-results.column.score"))
                .setResizable(true)
                .setComparator(Comparator.comparing(EntrantResultRow::round1Total,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .setWidth("120px").setFlexGrow(0);
        grid.addComponentColumn(this::advancedCell)
                .setHeader(getTranslation("my-results.column.advanced"))
                .setResizable(true).setWidth("130px").setFlexGrow(0);
        grid.addColumn(r -> formatMedal(r.medal()))
                .setHeader(getTranslation("my-results.column.medal"))
                .setResizable(true)
                .setComparator(Comparator.comparingInt(r ->
                        r.medal() == null ? Integer.MAX_VALUE : r.medal().ordinal()))
                .setTooltipGenerator(r -> medalTooltip(r.medal()))
                .setWidth("110px").setFlexGrow(0);
        grid.addColumn(r -> formatBosPlace(r.bosPlace()))
                .setHeader(getTranslation("my-results.column.bos"))
                .setResizable(true)
                .setComparator(Comparator.comparing(EntrantResultRow::bosPlace,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .setTooltipGenerator(r -> bosTooltip(r.bosPlace()))
                .setWidth("130px").setFlexGrow(0);
        grid.addComponentColumn(r -> actionsCell(r, currentUserId))
                .setHeader(getTranslation("my-results.column.actions"))
                .setWidth("110px").setFlexGrow(0);

        grid.setItems(rows);
        add(grid);
    }

    private com.vaadin.flow.component.Component downloadAllComponent(List<EntrantResultRow> rows, UUID userId) {
        var ids = rows.stream()
                .filter(r -> r.scoresheetId() != null)
                .map(EntrantResultRow::scoresheetId)
                .toList();
        var btn = new Button(getTranslation("my-results.download-all"), new Icon(VaadinIcon.DOWNLOAD_ALT));
        btn.setId("my-results-download-all");
        if (ids.isEmpty()) {
            btn.setEnabled(false);
            return btn;
        }
        var locale = getLocale();
        var resource = new StreamResource("scoresheets.pdf",
                () -> new ByteArrayInputStream(scoresheetPdfService.generateBatchPdf(
                        ids, userId, AnonymizationLevel.ANONYMIZED, locale)));
        var anchor = new Anchor(resource, "");
        anchor.add(btn);
        anchor.getElement().setAttribute("download", true);
        return anchor;
    }

    private void applyFilter(String needle) {
        var q = needle == null ? "" : needle.trim().toLowerCase(Locale.ROOT);
        grid.setItems(allRows.stream()
                .filter(r -> q.isEmpty()
                        || (r.meadName() != null && r.meadName().toLowerCase(Locale.ROOT).contains(q)))
                .toList());
    }

    private Span advancedCell(EntrantResultRow row) {
        if (!row.advancedToMedalRound()) {
            return new Span("—");
        }
        var icon = VaadinIcon.CHECK.create();
        icon.setColor("var(--lumo-success-color)");
        var wrap = new Span(icon);
        wrap.getElement().setProperty("title", getTranslation("my-results.advanced.yes"));
        return wrap;
    }

    private HorizontalLayout actionsCell(EntrantResultRow row, UUID currentUserId) {
        var cell = new HorizontalLayout();
        cell.setPadding(false);
        cell.setSpacing(true);
        cell.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        if (row.scoresheetId() == null) {
            cell.add(new Span("—"));
            return cell;
        }
        var view = new Button(new Icon(VaadinIcon.EYE));
        view.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE,
                ButtonVariant.LUMO_SMALL);
        view.setId("my-results-view-scoresheet-" + row.entryId());
        view.setTooltipText(getTranslation("my-results.view-scoresheet"));
        view.addClickListener(e -> openScoresheetDialog(row.scoresheetId(), currentUserId));
        cell.add(view);
        cell.add(downloadAnchor(row.scoresheetId(), row.entryNumber(), currentUserId));
        return cell;
    }

    /**
     * Opens the read-only entrant scoresheet dialog for the given scoresheet.
     * Public so the per-row eye button (which lives in a Grid component column
     * Karibu can't click directly) is reachable from tests.
     */
    public void openScoresheetDialog(UUID scoresheetId, UUID userId) {
        if (scoresheetId == null) {
            return;
        }
        try {
            var view = awardsService.getAnonymizedScoresheet(scoresheetId, userId);
            new EntrantScoresheetDialog(view).open();
        } catch (BusinessRuleException e) {
            Notification.show(getTranslation(e.getMessage()), 4000, Notification.Position.MIDDLE);
        }
    }

    private static String logoDataUri(Competition competition) {
        return "data:" + competition.getLogoContentType() + ";base64,"
                + java.util.Base64.getEncoder().encodeToString(competition.getLogo());
    }

    private Anchor downloadAnchor(UUID scoresheetId, String entryNumber, UUID userId) {
        var locale = getLocale();
        var resource = new StreamResource("scoresheet_" + entryNumber + ".pdf",
                () -> new ByteArrayInputStream(scoresheetPdfService.generatePdf(
                        scoresheetId, userId, AnonymizationLevel.ANONYMIZED, locale)));
        var anchor = new Anchor(resource, "");
        anchor.setId("my-results-download-scoresheet-" + scoresheetId);
        // Small inline icon button, matching the entry grid's download-label action.
        var icon = new Button(new Icon(VaadinIcon.DOWNLOAD_ALT));
        icon.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE,
                ButtonVariant.LUMO_SMALL);
        icon.setTooltipText(getTranslation("my-results.download-scoresheet"));
        anchor.add(icon);
        anchor.getElement().setAttribute("download", true);
        return anchor;
    }

    private String formatMedal(Medal medal) {
        if (medal == null) {
            return "—";
        }
        return switch (medal) {
            case GOLD -> "🥇";
            case SILVER -> "🥈";
            case BRONZE -> "🥉";
        };
    }

    private String formatBosPlace(Integer place) {
        return place == null ? "—" : "🏆 " + place;
    }

    private String bosTooltip(Integer place) {
        return place == null ? "" : getTranslation("my-results.bos.tooltip", place);
    }

    private String medalTooltip(Medal medal) {
        if (medal == null) {
            return "";
        }
        return switch (medal) {
            case GOLD -> getTranslation("my-results.medal.gold");
            case SILVER -> getTranslation("my-results.medal.silver");
            case BRONZE -> getTranslation("my-results.medal.bronze");
        };
    }

    private UUID getCurrentUserId() {
        return authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(u -> userService.findByEmail(u.getUsername()).getId())
                .orElse(null);
    }
}
