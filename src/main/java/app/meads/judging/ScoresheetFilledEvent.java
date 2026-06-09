package app.meads.judging;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a judge marks a scoresheet FILLED (the validating "Save").
 * The SCORE_BASED medal round listens for this so it can auto-populate medals
 * from the filled totals once every sheet on the round is FILLED — medals then
 * appear while the round is still ACTIVE, before the round-level Finalize
 * submits the sheets.
 */
public record ScoresheetFilledEvent(
        UUID scoresheetId,
        UUID entryId,
        UUID roundId,
        UUID filledByJudgeUserId,
        Instant filledAt) {
}
