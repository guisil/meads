package app.meads.judging;

import java.time.Instant;
import java.util.UUID;

public record BosStartedEvent(
        UUID divisionId,
        Instant startedAt) {
}
