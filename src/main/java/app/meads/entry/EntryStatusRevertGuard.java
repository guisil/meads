package app.meads.entry;

import java.util.UUID;

/**
 * Guard interface for entry status reverts (RECEIVED → SUBMITTED via
 * {@code revertEntryStatus}, any → WITHDRAWN via {@code withdrawEntry}).
 * Modules implement this to block reverts that would leave data inconsistent
 * — for instance, the judging module blocks reverting a RECEIVED entry
 * whose scoresheet already exists on a judging table.
 */
public interface EntryStatusRevertGuard {

    /**
     * Called before an entry's status is reverted or it is withdrawn. Throw
     * {@link app.meads.BusinessRuleException} to block.
     */
    void checkRevertAllowed(UUID entryId, UUID divisionId);
}
