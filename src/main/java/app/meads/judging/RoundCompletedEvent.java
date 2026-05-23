package app.meads.judging;

import java.time.Instant;
import java.util.UUID;

public record RoundCompletedEvent(
        UUID roundId,
        UUID divisionCategoryId,
        UUID divisionId,
        Instant completedAt) {
}
