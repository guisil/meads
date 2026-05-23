package app.meads.judging;

import java.time.Instant;
import java.util.UUID;

public record ScoresheetRevertedEvent(
        UUID scoresheetId,
        UUID entryId,
        UUID roundId,
        Instant revertedAt) {
}
