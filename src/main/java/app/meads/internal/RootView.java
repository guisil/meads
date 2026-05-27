package app.meads.internal;

import app.meads.CompetitionAdminChecker;
import app.meads.JudgeAssignmentChecker;
import app.meads.MainLayout;
import app.meads.identity.UserService;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.spring.security.AuthenticationContext;

@Route(value = "", layout = MainLayout.class)
@AnonymousAllowed
public class RootView extends VerticalLayout implements BeforeEnterObserver {

    private final transient AuthenticationContext authenticationContext;
    private final CompetitionAdminChecker competitionAdminChecker;
    private final JudgeAssignmentChecker judgeAssignmentChecker;
    private final UserService userService;

    public RootView(AuthenticationContext authenticationContext,
                     CompetitionAdminChecker competitionAdminChecker,
                     JudgeAssignmentChecker judgeAssignmentChecker,
                     UserService userService) {
        this.authenticationContext = authenticationContext;
        this.competitionAdminChecker = competitionAdminChecker;
        this.judgeAssignmentChecker = judgeAssignmentChecker;
        this.userService = userService;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!authenticationContext.isAuthenticated()) {
            event.forwardTo("login");
            return;
        }

        if (authenticationContext.hasRole("SYSTEM_ADMIN")) {
            event.forwardTo("competitions");
            return;
        }

        var email = authenticationContext.getPrincipalName().orElse("");
        if (competitionAdminChecker.hasAdminCompetitions(email)) {
            var user = userService.findByEmail(email);
            if (userService.hasPassword(user.getId())) {
                event.forwardTo("my-competitions");
                return;
            }
        }

        // Judges with any assignment default to /my-judging — which itself
        // forwards to the ACTIVE round (or shows "no active round" when
        // nothing is live). Keeps the "log in → see what you need to do"
        // UX without RootView knowing about round state.
        if (judgeAssignmentChecker.hasAnyJudgeAssignment(email)) {
            event.forwardTo("my-judging");
            return;
        }

        event.forwardTo("my-entries");
    }
}
