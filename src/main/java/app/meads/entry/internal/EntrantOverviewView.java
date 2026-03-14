package app.meads.entry.internal;

import app.meads.MainLayout;
import app.meads.entry.EntrantDivisionOverview;
import app.meads.entry.EntryService;
import app.meads.identity.UserService;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.stream.Collectors;

@Route(value = "my-entries", layout = MainLayout.class)
@PermitAll
public class EntrantOverviewView extends VerticalLayout implements BeforeEnterObserver {

    private final EntryService entryService;
    private final UserService userService;
    private final transient AuthenticationContext authenticationContext;

    public EntrantOverviewView(EntryService entryService,
                                UserService userService,
                                AuthenticationContext authenticationContext) {
        this.entryService = entryService;
        this.userService = userService;
        this.authenticationContext = authenticationContext;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var email = authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElseThrow();

        java.util.List<EntrantDivisionOverview> overviews;
        try {
            var userId = userService.findByEmail(email).getId();
            overviews = entryService.findEntrantDivisionOverviews(userId);
        } catch (IllegalArgumentException e) {
            overviews = java.util.List.of();
        }

        // Auto-redirect to single division
        if (overviews.size() == 1) {
            var o = overviews.getFirst();
            event.forwardTo("competitions/" + o.competitionShortName()
                    + "/divisions/" + o.divisionShortName() + "/my-entries");
            return;
        }

        removeAll();
        add(new H2("My Entries"));

        if (overviews.isEmpty()) {
            add(new Span("You have no entries in any competition."));
            return;
        }

        // Group by competition
        var byCompetition = overviews.stream()
                .collect(Collectors.groupingBy(
                        EntrantDivisionOverview::competitionName,
                        java.util.LinkedHashMap::new,
                        Collectors.toList()));

        for (var entry : byCompetition.entrySet()) {
            add(new H3(entry.getKey()));
            for (var overview : entry.getValue()) {
                add(createDivisionRow(overview));
            }
        }
    }

    private HorizontalLayout createDivisionRow(EntrantDivisionOverview overview) {
        var row = new HorizontalLayout();
        row.setWidthFull();
        row.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        var link = new Anchor(
                "competitions/" + overview.competitionShortName()
                        + "/divisions/" + overview.divisionShortName()
                        + "/my-entries",
                overview.divisionName());

        var credits = new Span("Credits: " + overview.creditBalance());
        var entries = new Span("Entries: " + overview.activeEntryCount());

        row.add(link, credits, entries);
        row.setFlexGrow(1, link);
        return row;
    }
}
