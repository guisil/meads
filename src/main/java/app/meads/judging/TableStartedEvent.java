package app.meads.judging;

import java.time.Instant;
import java.util.UUID;

public record TableStartedEvent(
        UUID tableId,
        UUID divisionCategoryId,
        UUID divisionId,
        Instant startedAt) {
}
