package app.meads.competition;

import java.util.UUID;

/**
 * Cross-module guard checked when updating Division.minJudgesPerTable.
 * The judging module implements this to block the update once any
 * JudgingTable for the division has progressed past NOT_STARTED.
 *
 * <p>Pattern mirrors DivisionRevertGuard / DivisionDeletionGuard /
 * JudgingCategoryDeletionGuard.</p>
 */
public interface MinJudgesPerTableLockGuard {
    boolean isLocked(UUID divisionId);
}
