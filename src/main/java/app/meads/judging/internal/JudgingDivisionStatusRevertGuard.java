package app.meads.judging.internal;

import app.meads.BusinessRuleException;
import app.meads.competition.DivisionRevertGuard;
import app.meads.competition.DivisionStatus;
import app.meads.judging.JudgingPhase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class JudgingDivisionStatusRevertGuard implements DivisionRevertGuard {

    private final JudgingRepository judgingRepository;
    private final JudgingTableRepository judgingTableRepository;

    JudgingDivisionStatusRevertGuard(JudgingRepository judgingRepository,
                                      JudgingTableRepository judgingTableRepository) {
        this.judgingRepository = judgingRepository;
        this.judgingTableRepository = judgingTableRepository;
    }

    @Override
    public void checkRevertAllowed(UUID divisionId, DivisionStatus fromStatus, DivisionStatus toStatus) {
        if (fromStatus != DivisionStatus.JUDGING || toStatus != DivisionStatus.REGISTRATION_CLOSED) {
            return;
        }
        var judging = judgingRepository.findByDivisionId(divisionId).orElse(null);
        if (judging == null) {
            return;
        }
        boolean hasData = judging.getPhase() != JudgingPhase.NOT_STARTED
                || judgingTableRepository.existsByJudgingId(judging.getId());
        if (hasData) {
            log.warn("Blocked division revert to REGISTRATION_CLOSED: division {} has judging data",
                    divisionId);
            throw new BusinessRuleException("error.division.cannot-revert-has-judging");
        }
    }
}
