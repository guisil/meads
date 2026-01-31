package app.meads.notification.internal;

import app.meads.order.api.OrderPendingReviewEvent;
import app.meads.shared.api.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class PendingOrderNotificationHandler {

    private final EmailService emailService;

    @Value("${meads.admin.email:admin@meads.app}")
    private String adminEmail;

    @ApplicationModuleListener
    void onOrderPendingReview(OrderPendingReviewEvent event) {
        log.info("Handling OrderPendingReviewEvent for order: {}", event.externalOrderId());

        notifyAdmin(event);
        if (event.entrantEmail() != null) {
            notifyEntrant(event);
        }
    }

    private void notifyAdmin(OrderPendingReviewEvent event) {
        var subject = "Order Pending Review - MEADS";
        var body = String.format("""
            A new order requires review.

            Order ID: %s
            Source: %s
            Reason: %s
            Competition ID: %s
            Entrant Email: %s
            Quantity: %d

            Please review this order in the admin panel.
            """,
            event.externalOrderId(),
            event.externalSource(),
            event.reason(),
            event.competitionId(),
            event.entrantEmail() != null ? event.entrantEmail() : "N/A",
            event.quantity()
        );

        emailService.sendEmail(adminEmail, subject, body);
    }

    private void notifyEntrant(OrderPendingReviewEvent event) {
        var subject = "Your Order Requires Review - MEADS";
        var body = String.format("""
            Hello,

            Your recent order for MEADS competition entry credits requires manual review.

            Reason: %s

            Our team will review your order and contact you if additional information is needed.

            Order Reference: %s

            Thank you for your patience.

            Best regards,
            The MEADS Team
            """,
            formatReasonForEntrant(event.reason()),
            event.externalOrderId()
        );

        emailService.sendEmail(event.entrantEmail(), subject, body);
    }

    private String formatReasonForEntrant(String reason) {
        return switch (reason) {
            case "COMPETITION_EXCLUSIVITY" ->
                "You already have entry credits for a different competition type. " +
                "Entrants can only participate in one competition type (home or commercial) per event.";
            default -> "Your order requires additional verification.";
        };
    }
}
