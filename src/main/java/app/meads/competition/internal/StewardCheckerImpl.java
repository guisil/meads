package app.meads.competition.internal;

import app.meads.BusinessRuleException;
import app.meads.StewardChecker;
import app.meads.competition.CompetitionService;
import app.meads.identity.UserService;
import org.springframework.stereotype.Component;

@Component
class StewardCheckerImpl implements StewardChecker {

    private final CompetitionService competitionService;
    private final UserService userService;

    StewardCheckerImpl(CompetitionService competitionService, UserService userService) {
        this.competitionService = competitionService;
        this.userService = userService;
    }

    @Override
    public boolean isStewardSomewhere(String email) {
        try {
            var user = userService.findByEmail(email);
            return !competitionService.findCompetitionsBySteward(user.getId()).isEmpty();
        } catch (BusinessRuleException e) {
            return false;
        }
    }
}
