package app.meads.judging.internal;

import app.meads.MainLayout;
import app.meads.competition.CompetitionService;
import app.meads.identity.UserService;
import app.meads.judging.JudgingRound;
import app.meads.judging.JudgingService;
import app.meads.judging.RoundType;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

/**
 * Judge default landing during JUDGING. A judge has at most one ACTIVE round
 * at any time (enforced by {@code JudgingService.assignJudge}'s
 * active-conflict check). This view either forwards directly to that round,
 * or — when there is no active round — shows a bare "nothing to do right
 * now" stub. There is no hub of all assignments; non-active rounds are
 * intentionally not reachable by judges.
 */
@Route(value = "my-judging", layout = MainLayout.class)
@PermitAll
public class MyJudgingView extends VerticalLayout implements BeforeEnterObserver {

    private final UserService userService;
    private final CompetitionService competitionService;
    private final JudgingService judgingService;
    private final transient AuthenticationContext authenticationContext;

    public MyJudgingView(UserService userService,
                         CompetitionService competitionService,
                         JudgingService judgingService,
                         AuthenticationContext authenticationContext) {
        this.userService = userService;
        this.competitionService = competitionService;
        this.judgingService = judgingService;
        this.authenticationContext = authenticationContext;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var userId = getCurrentUserId();
        if (userId == null) {
            event.forwardTo("");
            return;
        }

        var activeRound = judgingService.findActiveRoundForJudge(userId).orElse(null);
        if (activeRound != null) {
            event.forwardTo(roundUrl(activeRound));
            return;
        }

        removeAll();
        add(new H2(getTranslation("my-judging.title")));
        var empty = new Span(getTranslation("my-judging.empty.no-active-round"));
        empty.setId("my-judging-empty");
        add(empty);
    }

    private String roundUrl(JudgingRound round) {
        var category = competitionService.findDivisionCategoryById(round.getDivisionCategoryId());
        var division = competitionService.findDivisionById(category.getDivisionId());
        var competition = competitionService.findCompetitionById(division.getCompetitionId());
        var prefix = "competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName();
        return round.getType() == RoundType.MEDAL
                ? prefix + "/medal-rounds/" + category.getId()
                : prefix + "/tables/" + round.getId();
    }

    private UUID getCurrentUserId() {
        return authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(userDetails -> userService.findByEmail(userDetails.getUsername()).getId())
                .orElse(null);
    }
}
