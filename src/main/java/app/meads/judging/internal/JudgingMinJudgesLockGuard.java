package app.meads.judging.internal;

import app.meads.competition.MinJudgesPerRoundLockGuard;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JudgingMinJudgesLockGuard implements MinJudgesPerRoundLockGuard {

    private final JudgingRepository judgingRepository;
    private final JudgingRoundRepository judgingRoundRepository;

    JudgingMinJudgesLockGuard(JudgingRepository judgingRepository,
                              JudgingRoundRepository judgingRoundRepository) {
        this.judgingRepository = judgingRepository;
        this.judgingRoundRepository = judgingRoundRepository;
    }

    @Override
    public boolean isLocked(UUID divisionId) {
        return judgingRepository.findByDivisionId(divisionId)
                .map(j -> judgingRoundRepository.existsStartedByJudgingId(j.getId()))
                .orElse(false);
    }
}
