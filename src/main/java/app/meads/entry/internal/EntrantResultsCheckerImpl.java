package app.meads.entry.internal;

import app.meads.BusinessRuleException;
import app.meads.EntrantResultsChecker;
import app.meads.competition.DivisionStatus;
import app.meads.entry.EntryService;
import app.meads.identity.UserService;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class EntrantResultsCheckerImpl implements EntrantResultsChecker {

    private final EntryService entryService;
    private final UserService userService;

    EntrantResultsCheckerImpl(EntryService entryService, UserService userService) {
        this.entryService = entryService;
        this.userService = userService;
    }

    @Override
    public Optional<String> resultsLandingPath(String email) {
        try {
            var userId = userService.findByEmail(email).getId();
            var published = entryService.findEntrantDivisionOverviews(userId).stream()
                    .filter(o -> o.status() == DivisionStatus.RESULTS_PUBLISHED)
                    .toList();
            if (published.isEmpty()) {
                return Optional.empty();
            }
            if (published.size() == 1) {
                var o = published.getFirst();
                return Optional.of("competitions/" + o.competitionShortName()
                        + "/divisions/" + o.divisionShortName() + "/my-results");
            }
            // Several published divisions — send them to the hub, which lists a
            // per-division "View results" link.
            return Optional.of("my-entries");
        } catch (BusinessRuleException e) {
            return Optional.empty();
        }
    }
}
