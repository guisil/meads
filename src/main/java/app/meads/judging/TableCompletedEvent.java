package app.meads.judging;

import java.time.Instant;
import java.util.UUID;

public record TableCompletedEvent(
        UUID tableId,
        UUID divisionCategoryId,
        UUID divisionId,
        Instant completedAt) {
}
