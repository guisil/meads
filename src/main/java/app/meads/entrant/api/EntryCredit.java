package app.meads.entrant.api;

import java.time.Instant;
import java.util.UUID;

public record EntryCredit(
    UUID id,
    UUID entrantId,
    UUID competitionId,
    int quantity,
    int usedCount,
    String externalOrderId,
    String externalSource,
    String status,
    Instant purchasedAt
) {
    public int availableCredits() {
        return quantity - usedCount;
    }
}
