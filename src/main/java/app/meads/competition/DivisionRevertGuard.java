package app.meads.competition;

import java.util.UUID;

/**
 * Guard interface for division status revert operations.
 * Modules that own data tied to division status (e.g., entries, judging)
 * implement this to block reverts that would leave data inconsistent.
 */
public interface DivisionRevertGuard {

    /**
     * Called before a division status is reverted. Throw {@link IllegalStateException}
     * to block the revert.
     */
    void checkRevertAllowed(UUID divisionId, DivisionStatus fromStatus, DivisionStatus toStatus);
}
