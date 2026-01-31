package app.meads.order.api;

import java.util.UUID;

public record OrderPendingReviewEvent(
    UUID pendingOrderId,
    String externalOrderId,
    String externalSource,
    UUID competitionId,
    String entrantEmail,
    int quantity,
    String reason
) {}
