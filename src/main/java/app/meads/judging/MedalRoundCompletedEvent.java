package app.meads.judging;

import java.time.Instant;
import java.util.UUID;

public record MedalRoundCompletedEvent(
        UUID divisionCategoryId,
        UUID divisionId,
        Instant completedAt) {
}
