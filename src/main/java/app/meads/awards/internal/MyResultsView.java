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
import app.meads.judging.Medal;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

@Route(value = "competitions/:compShortName/divisions/:divShortName/my-results", layout = MainLayout.class)
@PermitAll
public class MyResultsView extends VerticalLayout implements BeforeEnterObserver {

    private final AwardsService awardsService;
    private final CompetitionService competitionService;
    private final UserService userService;
    private final transient AuthenticationContext authenticationContext;

    public MyResultsView(AwardsService awardsService,
                         CompetitionService competitionService,
                         UserService userService,
                         AuthenticationContext authenticationContext) {
        this.awardsService = awardsService;
        this.competitionService = competitionService;
        this.userService = userService;
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
            render(competition, division, rows, compShortName, divShortName);
        } catch (BusinessRuleException e) {
            event.forwardTo("competitions/" + compShortName + "/divisions/" + divShortName + "/my-entries");
        }
    }

    private void render(Competition competition, Division division,
                        java.util.List<EntrantResultRow> rows,
                        String compShortName, String divShortName) {
        removeAll();
        var heading = new H2(competition.getName() + " — " + division.getName()
                + " — " + getTranslation("my-results.title"));
        heading.setId("my-results-heading");
        add(heading);

        var grid = new Grid<EntrantResultRow>();
        grid.setId("my-results-grid");
        grid.addColumn(EntrantResultRow::entryNumber)
                .setHeader(getTranslation("my-results.column.entry"));
        grid.addColumn(EntrantResultRow::meadName)
                .setHeader(getTranslation("my-results.column.mead-name"));
        grid.addColumn(r -> r.categoryCode() + " — " + r.categoryName())
                .setHeader(getTranslation("my-results.column.category"));
        grid.addColumn(r -> r.round1Total() == null ? "—" : (r.round1Total() + " / 100"))
                .setHeader(getTranslation("my-results.column.score"));
        grid.addColumn(r -> r.advancedToMedalRound()
                        ? getTranslation("my-results.advanced.yes")
                        : getTranslation("my-results.advanced.no"))
                .setHeader(getTranslation("my-results.column.advanced"));
        grid.addColumn(r -> formatMedal(r.medal()))
                .setHeader(getTranslation("my-results.column.medal"));
        grid.addColumn(r -> r.bosPlace() == null ? "—" : String.valueOf(r.bosPlace()))
                .setHeader(getTranslation("my-results.column.bos"));
        grid.addComponentColumn(r -> {
                    if (r.scoresheetId() == null) {
                        return new com.vaadin.flow.component.html.Span("—");
                    }
                    var btn = new Button(getTranslation("my-results.view-scoresheet"));
                    btn.setId("my-results-view-scoresheet-" + r.entryId());
                    btn.addClickListener(e -> UI.getCurrent().navigate(
                            "competitions/" + compShortName + "/divisions/" + divShortName
                                    + "/my-entries/" + r.entryId() + "/scoresheet"));
                    return btn;
                })
                .setHeader(getTranslation("my-results.column.actions"));
        grid.setItems(rows);
        grid.setAllRowsVisible(true);
        add(grid);
    }

    private String formatMedal(Medal medal) {
        if (medal == null) {
            return "—";
        }
        return getTranslation("my-results.medal." + medal.name().toLowerCase());
    }

    private UUID getCurrentUserId() {
        return authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(u -> userService.findByEmail(u.getUsername()).getId())
                .orElse(null);
    }
}
