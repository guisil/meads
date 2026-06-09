package app.meads.judging;

import java.time.Instant;
import java.util.UUID;

public record BosReopenedEvent(
        UUID divisionId,
        Instant reopenedAt) {
}
