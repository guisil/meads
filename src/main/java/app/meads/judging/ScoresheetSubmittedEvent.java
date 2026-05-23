package app.meads.judging;

import java.time.Instant;
import java.util.UUID;

public record ScoresheetSubmittedEvent(
        UUID scoresheetId,
        UUID entryId,
        UUID roundId,
        int totalScore,
        Instant submittedAt) {
}
