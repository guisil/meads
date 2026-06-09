package app.meads.entry;

import java.util.UUID;

/**
 * Fired on any entry transition that could change its medal-round eligibility:
 * status → RECEIVED (add path), status leaving RECEIVED via withdraw / revert
 * (zombie cleanup), or finalCategoryId set / changed on a RECEIVED entry. The
 * judging module's {@code MedalRoundAutoSyncListener} reconciles SCORE_BASED
 * medal rounds in response (force-all invariant: every RECEIVED entry in the
 * category must be on the round, and nothing else).
 *
 * @param triggeredByUserId the admin who performed the action that fired the
 *                          event; reused as the {@code adminUserId} for the
 *                          downstream sync call so the change stays
 *                          authorization-bound to the same user.
 */
public record EntryReceivedEvent(UUID entryId, UUID divisionId, UUID triggeredByUserId) {}
