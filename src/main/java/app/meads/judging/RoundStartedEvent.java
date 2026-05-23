package app.meads.judging;

import java.time.Instant;
import java.util.UUID;

public record RoundStartedEvent(
        UUID roundId,
        UUID divisionCategoryId,
        UUID divisionId,
        Instant startedAt) {
}
