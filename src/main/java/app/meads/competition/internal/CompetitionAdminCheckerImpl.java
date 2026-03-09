package app.meads.competition.internal;

import app.meads.CompetitionAdminChecker;
import app.meads.competition.CompetitionService;
import app.meads.identity.UserService;
import org.springframework.stereotype.Component;

@Component
class CompetitionAdminCheckerImpl implements CompetitionAdminChecker {

    private final CompetitionService competitionService;
    private final UserService userService;

    CompetitionAdminCheckerImpl(CompetitionService competitionService,
                                 UserService userService) {
        this.competitionService = competitionService;
        this.userService = userService;
    }

    @Override
    public boolean hasAdminCompetitions(String email) {
        try {
            var user = userService.findByEmail(email);
            return !competitionService.findCompetitionsByAdmin(user.getId()).isEmpty();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
