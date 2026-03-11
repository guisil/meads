package app.meads.entry;

import java.util.Set;
import java.util.UUID;

public record OrderRequiresReviewEvent(
        UUID orderId,
        String jumpsellerOrderId,
        String customerName,
        String customerEmail,
        Set<UUID> affectedCompetitionIds,
        OrderStatus status
) {}
