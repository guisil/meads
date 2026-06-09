package app.meads.judging.internal;

import app.meads.BusinessRuleException;
import app.meads.JudgeAssignmentChecker;
import app.meads.identity.UserService;
import app.meads.judging.JudgingService;
import org.springframework.stereotype.Component;

@Component
class JudgeAssignmentCheckerImpl implements JudgeAssignmentChecker {

    private final JudgingService judgingService;
    private final UserService userService;

    JudgeAssignmentCheckerImpl(JudgingService judgingService,
                                UserService userService) {
        this.judgingService = judgingService;
        this.userService = userService;
    }

    @Override
    public boolean hasAnyJudgeAssignment(String email) {
        try {
            var user = userService.findByEmail(email);
            return judgingService.hasAnyJudgeAssignment(user.getId());
        } catch (BusinessRuleException e) {
            return false;
        }
    }
}
