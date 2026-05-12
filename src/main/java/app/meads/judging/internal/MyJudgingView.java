package app.meads.judging.internal;

import app.meads.MainLayout;
import app.meads.competition.Competition;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.identity.Role;
import app.meads.identity.UserService;
import app.meads.judging.CategoryJudgingConfig;
import app.meads.judging.JudgingService;
import app.meads.judging.JudgingTable;
import app.meads.judging.ScoresheetService;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Route(value = "my-judging", layout = MainLayout.class)
@PermitAll
public class MyJudgingView extends VerticalLayout implements BeforeEnterObserver {

    private final UserService userService;
    private final CompetitionService competitionService;
    private final JudgingService judgingService;
    private final ScoresheetService scoresheetService;
    private final transient AuthenticationContext authenticationContext;

    public MyJudgingView(UserService userService,
                         CompetitionService competitionService,
                         JudgingService judgingService,
                         ScoresheetService scoresheetService,
                         AuthenticationContext authenticationContext) {
        this.userService = userService;
        this.competitionService = competitionService;
        this.judgingService = judgingService;
        this.scoresheetService = scoresheetService;
        this.authenticationContext = authenticationContext;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var userId = getCurrentUserId();
        if (userId == null) {
            event.forwardTo("");
            return;
        }

        removeAll();
        add(new H2(getTranslation("my-judging.title")));

        scoresheetService.findNextDraftForJudge(userId)
                .flatMap(scoresheetService::findById)
                .ifPresent(sheet -> add(createResumeBar(sheet)));

        var tables = judgingService.findTablesByJudgeUserId(userId);
        if (tables.isEmpty()) {
            add(createEmptyState());
        } else {
            add(createTablesSection(tables));
        }

        var activeConfigs = judgingService.findActiveCategoryConfigsForJudge(userId);
        if (!activeConfigs.isEmpty()) {
            add(createMedalRoundsSection(activeConfigs));
        }
    }

    private VerticalLayout createMedalRoundsSection(List<CategoryJudgingConfig> configs) {
        var section = new VerticalLayout();
        section.setPadding(false);
        section.add(new H3(getTranslation("my-judging.medal-rounds.section")));
        for (var config : configs) {
            var category = competitionService.findDivisionCategoryById(config.getDivisionCategoryId());
            var division = competitionService.findDivisionById(category.getDivisionId());
            var competition = competitionService.findCompetitionById(division.getCompetitionId());
            var row = new VerticalLayout();
            row.setPadding(false);
            row.add(new Span(category.getCode() + " — " + category.getName()));
            var url = "competitions/" + competition.getShortName()
                    + "/divisions/" + division.getShortName()
                    + "/medal-rounds/" + category.getId();
            row.add(new Anchor(url, getTranslation("my-judging.medal-rounds.open")));
            section.add(row);
        }
        return section;
    }

    private VerticalLayout createResumeBar(app.meads.judging.Scoresheet sheet) {
        var bar = new VerticalLayout();
        bar.setPadding(false);
        var table = judgingService.findTableById(sheet.getTableId()).orElseThrow();
        var category = competitionService.findDivisionCategoryById(table.getDivisionCategoryId());
        var division = competitionService.findDivisionById(category.getDivisionId());
        var competition = competitionService.findCompetitionById(division.getCompetitionId());
        var url = "competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName()
                + "/scoresheets/" + sheet.getId();
        var anchor = new Anchor(url, getTranslation("my-judging.resume"));
        bar.add(anchor);
        return bar;
    }

    private VerticalLayout createTablesSection(List<JudgingTable> tables) {
        var section = new VerticalLayout();
        section.setPadding(false);

        var divisionByCategoryId = new LinkedHashMap<UUID, Division>();
        for (var table : tables) {
            divisionByCategoryId.computeIfAbsent(table.getDivisionCategoryId(), catId -> {
                var category = competitionService.findDivisionCategoryById(catId);
                return competitionService.findDivisionById(category.getDivisionId());
            });
        }

        var byCompetition = new LinkedHashMap<UUID, List<JudgingTable>>();
        for (var table : tables) {
            var division = divisionByCategoryId.get(table.getDivisionCategoryId());
            byCompetition.computeIfAbsent(division.getCompetitionId(),
                    k -> new java.util.ArrayList<>()).add(table);
        }

        for (var entry : byCompetition.entrySet()) {
            Competition competition = competitionService.findCompetitionById(entry.getKey());
            var group = new VerticalLayout();
            group.setPadding(false);
            group.add(new H3(competition.getName()));
            for (var table : entry.getValue()) {
                var division = divisionByCategoryId.get(table.getDivisionCategoryId());
                var row = new VerticalLayout();
                row.setPadding(false);
                row.add(new Span(division.getName()));
                row.add(new Span(table.getName()));
                row.add(new Anchor(
                        "competitions/" + competition.getShortName()
                                + "/divisions/" + division.getShortName()
                                + "/tables/" + table.getId(),
                        getTranslation("my-judging.table.open")));
                group.add(row);
            }
            section.add(group);
        }
        return section;
    }

    private VerticalLayout createEmptyState() {
        var layout = new VerticalLayout();
        layout.setPadding(false);
        layout.add(new Span(getTranslation("my-judging.empty")));
        layout.add(new Anchor("profile", getTranslation("my-judging.empty.cta-profile")));
        layout.add(new Anchor(
                isCurrentUserSystemAdmin() ? "competitions" : "my-competitions",
                getTranslation("my-judging.empty.cta-competitions")));
        return layout;
    }

    private boolean isCurrentUserSystemAdmin() {
        var userId = getCurrentUserId();
        if (userId == null) {
            return false;
        }
        return userService.findById(userId).getRole() == Role.SYSTEM_ADMIN;
    }

    private UUID getCurrentUserId() {
        return authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(userDetails -> userService.findByEmail(userDetails.getUsername()).getId())
                .orElse(null);
    }
}
