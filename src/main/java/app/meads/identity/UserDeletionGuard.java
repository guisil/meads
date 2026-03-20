package app.meads.identity;

import java.util.UUID;

/**
 * Guard interface for user deletion operations.
 * Modules that own data tied to users (e.g., participants, entries, credits)
 * implement this to block deletions that would leave data inconsistent.
 */
public interface UserDeletionGuard {

    /**
     * Called before a user is hard-deleted. Throw {@link app.meads.BusinessRuleException}
     * to block the deletion.
     */
    void checkDeletionAllowed(UUID userId);
}
