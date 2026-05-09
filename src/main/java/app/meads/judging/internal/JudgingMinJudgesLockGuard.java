package app.meads.judging.internal;

import app.meads.competition.MinJudgesPerTableLockGuard;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JudgingMinJudgesLockGuard implements MinJudgesPerTableLockGuard {

    private final JudgingRepository judgingRepository;
    private final JudgingTableRepository judgingTableRepository;

    JudgingMinJudgesLockGuard(JudgingRepository judgingRepository,
                              JudgingTableRepository judgingTableRepository) {
        this.judgingRepository = judgingRepository;
        this.judgingTableRepository = judgingTableRepository;
    }

    @Override
    public boolean isLocked(UUID divisionId) {
        return judgingRepository.findByDivisionId(divisionId)
                .map(j -> judgingTableRepository.existsStartedByJudgingId(j.getId()))
                .orElse(false);
    }
}
