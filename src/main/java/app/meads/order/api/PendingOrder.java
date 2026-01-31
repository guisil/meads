package app.meads.order.api;

import java.time.Instant;
import java.util.UUID;

public record PendingOrder(
    UUID id,
    String externalOrderId,
    String externalSource,
    UUID competitionId,
    UUID entrantId,
    String reason,
    String status,
    String resolvedBy,
    String resolutionNotes,
    Instant createdAt,
    Instant resolvedAt
) {}
