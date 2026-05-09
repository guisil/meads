package app.meads.judging;

import java.time.Instant;
import java.util.UUID;

public record ScoresheetSubmittedEvent(
        UUID scoresheetId,
        UUID entryId,
        UUID tableId,
        int totalScore,
        Instant submittedAt) {
}
