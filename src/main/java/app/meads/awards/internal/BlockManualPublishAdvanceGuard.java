package app.meads.awards.internal;

import app.meads.BusinessRuleException;
import app.meads.competition.DivisionAdvanceGuard;
import app.meads.competition.DivisionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
class BlockManualPublishAdvanceGuard implements DivisionAdvanceGuard {

    @Override
    public void checkAdvanceAllowed(UUID divisionId, DivisionStatus fromStatus, DivisionStatus toStatus) {
        if (fromStatus != DivisionStatus.DELIBERATION || toStatus != DivisionStatus.RESULTS_PUBLISHED) {
            return;
        }
        log.warn("Blocked manual DELIBERATION → RESULTS_PUBLISHED advance for division {} (use AwardsService.publish/republish)", divisionId);
        throw new BusinessRuleException("error.division.use-publish-results-instead");
    }
}
