package app.meads.competition;

import java.util.UUID;

/**
 * Cross-module guard checked when updating Division.minJudgesPerRound.
 * The judging module implements this to block the update once any
 * JudgingRound for the division has progressed past NOT_STARTED.
 *
 * <p>Pattern mirrors DivisionRevertGuard / DivisionDeletionGuard /
 * JudgingCategoryDeletionGuard.</p>
 */
public interface MinJudgesPerRoundLockGuard {
    boolean isLocked(UUID divisionId);
}
