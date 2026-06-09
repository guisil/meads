package app.meads.judging;

import java.time.Instant;
import java.util.UUID;

public record RoundReopenedEvent(
        UUID roundId,
        UUID divisionCategoryId,
        UUID divisionId,
        Instant reopenedAt) {
}
