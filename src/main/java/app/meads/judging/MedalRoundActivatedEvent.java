package app.meads.judging;

import java.time.Instant;
import java.util.UUID;

public record MedalRoundActivatedEvent(
        UUID divisionCategoryId,
        UUID divisionId,
        MedalRoundMode mode,
        Instant activatedAt) {
}
