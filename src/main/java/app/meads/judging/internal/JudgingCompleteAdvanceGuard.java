package app.meads.judging.internal;

import app.meads.BusinessRuleException;
import app.meads.competition.DivisionAdvanceGuard;
import app.meads.competition.DivisionStatus;
import app.meads.judging.JudgingPhase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class JudgingCompleteAdvanceGuard implements DivisionAdvanceGuard {

    private final JudgingRepository judgingRepository;

    JudgingCompleteAdvanceGuard(JudgingRepository judgingRepository) {
        this.judgingRepository = judgingRepository;
    }

    @Override
    public void checkAdvanceAllowed(UUID divisionId, DivisionStatus fromStatus, DivisionStatus toStatus) {
        if (fromStatus != DivisionStatus.JUDGING || toStatus != DivisionStatus.DELIBERATION) {
            return;
        }
        var judging = judgingRepository.findByDivisionId(divisionId).orElse(null);
        if (judging == null || judging.getPhase() != JudgingPhase.COMPLETE) {
            log.warn("Blocked JUDGING → DELIBERATION advance for division {}: judging phase != COMPLETE", divisionId);
            throw new BusinessRuleException("error.division.cannot-deliberate-judging-incomplete");
        }
    }
}
