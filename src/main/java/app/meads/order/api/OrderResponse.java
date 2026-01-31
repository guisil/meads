package app.meads.order.api;

import java.util.UUID;

public record OrderResponse(
    UUID entrantId,
    int creditsAdded,
    String status,
    String message
) {
    public static OrderResponse processed(UUID entrantId, int creditsAdded) {
        return new OrderResponse(entrantId, creditsAdded, "PROCESSED", "Entry credits added successfully");
    }

    public static OrderResponse alreadyProcessed(UUID entrantId) {
        return new OrderResponse(entrantId, 0, "ALREADY_PROCESSED", "Order already processed");
    }

    public static OrderResponse pendingReview(UUID entrantId, String reason) {
        return new OrderResponse(entrantId, 0, "PENDING_REVIEW",
            "Order stored for admin review - " + reason);
    }
}
