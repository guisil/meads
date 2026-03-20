package app.meads.competition;

import java.util.UUID;

/**
 * Guard interface for division deletion operations.
 * Modules that own data tied to divisions (e.g., entries, credits, product mappings)
 * implement this to block deletions that would leave data inconsistent.
 */
public interface DivisionDeletionGuard {

    /**
     * Called before a division is deleted. Throw {@link app.meads.BusinessRuleException}
     * to block the deletion.
     */
    void checkDeletionAllowed(UUID divisionId);
}
