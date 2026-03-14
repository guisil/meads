package app.meads.competition.internal;

import app.meads.MainLayout;
import app.meads.competition.Competition;
import app.meads.competition.CompetitionService;
import app.meads.identity.Role;
import app.meads.identity.UserService;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

@Route(value = "my-competitions", layout = MainLayout.class)
@PermitAll
public class MyCompetitionsView extends VerticalLayout implements BeforeEnterObserver {

    private final CompetitionService competitionService;
    private final UserService userService;
    private final transient AuthenticationContext authenticationContext;

    public MyCompetitionsView(CompetitionService competitionService,
                               UserService userService,
                               AuthenticationContext authenticationContext) {
        this.competitionService = competitionService;
        this.userService = userService;
        this.authenticationContext = authenticationContext;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent beforeEnterEvent) {
        var currentUserId = getCurrentUserId();
        var currentUser = userService.findById(currentUserId);
        var isCompAdmin = !competitionService.findCompetitionsByAdmin(currentUserId).isEmpty();
        if (currentUser.getRole() != Role.SYSTEM_ADMIN && isCompAdmin
                && !userService.hasPassword(currentUserId)) {
            Notification.show("Please set a password to access competition management. "
                    + "Check your email for the password setup link.");
            beforeEnterEvent.forwardTo("");
            return;
        }

        removeAll();
        add(new H2("My Competitions"));

        var grid = new Grid<>(Competition.class, false);
        grid.setAllRowsVisible(true);
        grid.addColumn(Competition::getName).setHeader("Name").setSortable(true);
        grid.addColumn(Competition::getStartDate).setHeader("Start Date").setSortable(true);
        grid.addColumn(Competition::getEndDate).setHeader("End Date").setSortable(true);
        grid.addColumn(comp -> comp.getLocation() != null ? comp.getLocation() : "—")
                .setHeader("Location");

        grid.addItemClickListener(e ->
                e.getSource().getUI().ifPresent(ui ->
                        ui.navigate("competitions/" + e.getItem().getShortName())));

        grid.setItems(competitionService.findCompetitionsByAdmin(currentUserId));
        add(grid);
    }

    private UUID getCurrentUserId() {
        var email = authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElseThrow();
        return userService.findByEmail(email).getId();
    }
}
