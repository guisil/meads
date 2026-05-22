package app.meads.judging.internal;

import app.meads.MainLayout;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionStatus;
import app.meads.entry.EntryService;
import app.meads.identity.UserService;
import app.meads.judging.JudgingService;
import app.meads.judging.JudgingTable;
import app.meads.judging.ScoresheetService;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-only steward view: the judging tables, their assigned judges, and the
 * entries on each table, for every JUDGING-or-later division of every competition
 * where the user holds the STEWARD role. Stewards use it to ferry the right coded
 * samples to the right tables. No mutating actions.
 */
@Route(value = "my-stewarding", layout = MainLayout.class)
@PermitAll
public class StewardView extends VerticalLayout implements BeforeEnterObserver {

    private final CompetitionService competitionService;
    private final UserService userService;
    private final JudgingService judgingService;
    private final ScoresheetService scoresheetService;
    private final EntryService entryService;
    private final transient AuthenticationContext authenticationContext;

    public StewardView(CompetitionService competitionService,
                       UserService userService,
                       JudgingService judgingService,
                       ScoresheetService scoresheetService,
                       EntryService entryService,
                       AuthenticationContext authenticationContext) {
        this.competitionService = competitionService;
        this.userService = userService;
        this.judgingService = judgingService;
        this.scoresheetService = scoresheetService;
        this.entryService = entryService;
        this.authenticationContext = authenticationContext;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            event.forwardTo("");
            return;
        }
        removeAll();
        add(new H2(getTranslation("steward.title")));

        var competitions = competitionService.findCompetitionsBySteward(currentUserId);
        if (competitions.isEmpty()) {
            var empty = new Span(getTranslation("steward.empty"));
            empty.setId("steward-empty");
            add(empty);
            return;
        }
        for (var competition : competitions) {
            add(new H3(competition.getName()));
            competitionService.findDivisionsByCompetition(competition.getId()).stream()
                    .filter(d -> d.getStatus().ordinal() >= DivisionStatus.JUDGING.ordinal())
                    .forEach(d -> add(createDivisionBlock(d)));
        }
    }

    private VerticalLayout createDivisionBlock(Division division) {
        var block = new VerticalLayout();
        block.setPadding(false);
        block.add(new H4(division.getName()));

        var judging = judgingService.ensureJudgingExists(division.getId());
        var tables = judgingService.findTablesByJudgingId(judging.getId());
        if (tables.isEmpty()) {
            block.add(new Span(getTranslation("steward.no-tables")));
            return block;
        }
        tables.forEach(t -> block.add(createTableCard(t)));
        return block;
    }

    private VerticalLayout createTableCard(JudgingTable table) {
        var card = new VerticalLayout();
        card.setPadding(false);
        card.setSpacing(false);
        card.getStyle().set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "var(--lumo-space-s)");

        var category = competitionService.findDivisionCategoryById(table.getDivisionCategoryId());
        var title = new Span(table.getName() + " — " + category.getCode() + " "
                + category.getName() + " (" + table.getStatus().name() + ")");
        title.getStyle().set("font-weight", "600");
        card.add(title);

        var judges = judgingService.findJudgeUserIdsForTable(table.getId()).stream()
                .map(id -> userService.findById(id).getName())
                .collect(Collectors.joining(", "));
        card.add(new Span(getTranslation("steward.judges") + ": "
                + (judges.isEmpty() ? "—" : judges)));

        for (var sheet : scoresheetService.findByTableId(table.getId())) {
            var entry = entryService.findEntryById(sheet.getEntryId());
            card.add(new Span("• " + entry.getEntryCode() + " — " + entry.getMeadName()));
        }
        return card;
    }

    private UUID getCurrentUserId() {
        return authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(userDetails -> userService.findByEmail(userDetails.getUsername()).getId())
                .orElse(null);
    }
}
