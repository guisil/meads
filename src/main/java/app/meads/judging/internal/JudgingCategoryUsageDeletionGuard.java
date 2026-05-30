package app.meads.judging.internal;

import app.meads.BusinessRuleException;
import app.meads.competition.JudgingCategoryDeletionGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Blocks deleting a JUDGING-scope category once it's in use by the judging
 * module — a scoring configuration ({@code category_judging_configs}), judging
 * rounds, or medal awards reference it. Without this, the delete reached the DB
 * and surfaced as a raw {@code DataIntegrityViolationException} (FK constraint)
 * instead of a clean, translated business error. Mirrors
 * {@code EntryJudgingCategoryDeletionGuard} (which covers entry references).
 */
@Slf4j
@Component
class JudgingCategoryUsageDeletionGuard implements JudgingCategoryDeletionGuard {

    private final CategoryJudgingConfigRepository configRepository;
    private final JudgingRoundRepository judgingRoundRepository;
    private final MedalAwardRepository medalAwardRepository;

    JudgingCategoryUsageDeletionGuard(CategoryJudgingConfigRepository configRepository,
                                      JudgingRoundRepository judgingRoundRepository,
                                      MedalAwardRepository medalAwardRepository) {
        this.configRepository = configRepository;
        this.judgingRoundRepository = judgingRoundRepository;
        this.medalAwardRepository = medalAwardRepository;
    }

    @Override
    public void checkDeletionAllowed(UUID categoryId) {
        boolean hasRounds = !judgingRoundRepository.findByDivisionCategoryId(categoryId).isEmpty();
        boolean hasAwards = !medalAwardRepository.findByFinalCategoryId(categoryId).isEmpty();
        boolean hasConfig = configRepository.findByDivisionCategoryId(categoryId).isPresent();
        if (hasRounds || hasAwards || hasConfig) {
            log.warn("Blocked judging category deletion: category {} is in use "
                    + "(rounds={}, awards={}, config={})", categoryId, hasRounds, hasAwards, hasConfig);
            throw new BusinessRuleException("error.category.judging-in-use");
        }
    }
}
