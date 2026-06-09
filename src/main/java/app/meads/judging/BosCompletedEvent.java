package app.meads.judging;

import java.time.Instant;
import java.util.UUID;

public record BosCompletedEvent(
        UUID divisionId,
        int placementsCount,
        Instant completedAt) {
}
