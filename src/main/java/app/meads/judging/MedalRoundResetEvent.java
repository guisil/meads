package app.meads.judging;

import java.time.Instant;
import java.util.UUID;

public record MedalRoundResetEvent(
        UUID divisionCategoryId,
        UUID divisionId,
        int wipedAwardCount,
        Instant resetAt) {
}
