package app.meads.internal;

import app.meads.CompetitionAdminChecker;
import app.meads.MainLayout;
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

    public RootView(AuthenticationContext authenticationContext,
                     CompetitionAdminChecker competitionAdminChecker) {
        this.authenticationContext = authenticationContext;
        this.competitionAdminChecker = competitionAdminChecker;
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
            event.forwardTo("my-competitions");
            return;
        }

        event.forwardTo("my-entries");
    }
}
