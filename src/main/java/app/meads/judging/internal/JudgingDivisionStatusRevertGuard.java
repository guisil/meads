package app.meads.judging.internal;

import app.meads.BusinessRuleException;
import app.meads.competition.DivisionRevertGuard;
import app.meads.competition.DivisionStatus;
import app.meads.judging.JudgingRoundStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class JudgingDivisionStatusRevertGuard implements DivisionRevertGuard {

    private final JudgingRepository judgingRepository;
    private final JudgingRoundRepository judgingRoundRepository;

    JudgingDivisionStatusRevertGuard(JudgingRepository judgingRepository,
                                      JudgingRoundRepository judgingRoundRepository) {
        this.judgingRepository = judgingRepository;
        this.judgingRoundRepository = judgingRoundRepository;
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
        // Rounds that are still PENDING/READY haven't been started yet — they
        // represent setup work that survives the revert (the admin can flip
        // the division back to JUDGING later without redoing it). ACTIVE or
        // COMPLETE rounds carry real judging work (DRAFT/SUBMITTED scoresheets,
        // medal awards, BOS placements) that revert would orphan.
        boolean hasInFlightOrCompleteRound = judgingRoundRepository.findByJudgingId(judging.getId())
                .stream()
                .anyMatch(r -> r.getStatus() == JudgingRoundStatus.ACTIVE
                            || r.getStatus() == JudgingRoundStatus.COMPLETE);
        if (hasInFlightOrCompleteRound) {
            log.warn("Blocked division revert to REGISTRATION_CLOSED: division {} has active or complete rounds",
                    divisionId);
            throw new BusinessRuleException("error.division.cannot-revert-has-judging");
        }
    }
}
