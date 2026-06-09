package app.meads.competition;

import java.util.UUID;

/**
 * Guard interface for division status advance operations.
 * Modules implement this to block status advances that would leave the
 * competition in an invalid state (e.g., advancing to JUDGING before
 * judging categories are initialized).
 */
public interface DivisionAdvanceGuard {

    /**
     * Called before a division status is advanced. Throw
     * {@link app.meads.BusinessRuleException} to block the advance.
     */
    void checkAdvanceAllowed(UUID divisionId, DivisionStatus fromStatus, DivisionStatus toStatus);
}
