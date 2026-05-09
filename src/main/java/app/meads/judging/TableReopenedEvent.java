package app.meads.judging;

import java.time.Instant;
import java.util.UUID;

public record TableReopenedEvent(
        UUID tableId,
        UUID divisionCategoryId,
        UUID divisionId,
        Instant reopenedAt) {
}
