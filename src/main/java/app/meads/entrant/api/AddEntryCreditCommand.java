package app.meads.entrant.api;

import java.time.Instant;
import java.util.UUID;

public record AddEntryCreditCommand(
    UUID entrantId,
    UUID competitionId,
    int quantity,
    String externalOrderId,
    String externalSource,
    Instant purchasedAt
) {}
