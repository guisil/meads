package app.meads.judging;

import java.time.Instant;
import java.util.UUID;

public record MedalRoundReopenedEvent(
        UUID divisionCategoryId,
        UUID divisionId,
        Instant reopenedAt) {
}
