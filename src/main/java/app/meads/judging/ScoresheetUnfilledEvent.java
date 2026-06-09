package app.meads.judging;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an already-FILLED scoresheet is edited back to DRAFT (the judge
 * changed a score or comment without re-Saving). The SCORE_BASED medal round
 * listens for this so it can clear the auto-populated (unconfirmed) medals for
 * the category: the ranking those medals came from assumed a complete FILLED
 * panel, which no longer holds. Confirmed (manual tie-resolution) awards are
 * deliberate decisions and are preserved. Medals re-populate via
 * {@link ScoresheetFilledEvent} once every sheet is FILLED again.
 */
public record ScoresheetUnfilledEvent(
        UUID scoresheetId,
        UUID entryId,
        UUID roundId,
        UUID editedByJudgeUserId,
        Instant occurredAt) {
}
