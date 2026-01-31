package app.meads.notification.internal;

import app.meads.entrant.api.EntryCreditAddedEvent;
import app.meads.order.api.OrderPendingReviewEvent;
import app.meads.shared.api.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationHandlerTest {

    @Mock
    private EmailService emailService;

    private EntryCreditNotificationHandler creditHandler;
    private PendingOrderNotificationHandler pendingOrderHandler;

    @BeforeEach
    void setUp() {
        creditHandler = new EntryCreditNotificationHandler(emailService);
        pendingOrderHandler = new PendingOrderNotificationHandler(emailService);
        ReflectionTestUtils.setField(pendingOrderHandler, "adminEmail", "admin@meads.app");
    }

    @Test
    void shouldSendEmailOnEntryCreditAdded() {
        var event = new EntryCreditAddedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            3,
            "meadmaker@example.com",
            "John Brewer"
        );

        creditHandler.onEntryCreditAdded(event);

        var subjectCaptor = ArgumentCaptor.forClass(String.class);
        var bodyCaptor = ArgumentCaptor.forClass(String.class);

        verify(emailService).sendEmail(
            eq("meadmaker@example.com"),
            subjectCaptor.capture(),
            bodyCaptor.capture()
        );

        assertThat(subjectCaptor.getValue()).contains("Entry Credits Added");
        assertThat(bodyCaptor.getValue())
            .contains("John Brewer")
            .contains("3 entry credit(s)");
    }

    @Test
    void shouldSendEmailToAdminOnPendingOrder() {
        var event = new OrderPendingReviewEvent(
            UUID.randomUUID(),
            "ORDER-123",
            "jumpseller",
            UUID.randomUUID(),
            "entrant@example.com",
            2,
            "COMPETITION_EXCLUSIVITY"
        );

        pendingOrderHandler.onOrderPendingReview(event);

        // Should send to admin
        verify(emailService).sendEmail(
            eq("admin@meads.app"),
            anyString(),
            anyString()
        );

        // Should also send to entrant
        verify(emailService).sendEmail(
            eq("entrant@example.com"),
            anyString(),
            anyString()
        );
    }

    @Test
    void shouldIncludeOrderDetailsInAdminEmail() {
        var event = new OrderPendingReviewEvent(
            UUID.randomUUID(),
            "ORDER-456",
            "jumpseller",
            UUID.randomUUID(),
            "test@example.com",
            5,
            "COMPETITION_EXCLUSIVITY"
        );

        pendingOrderHandler.onOrderPendingReview(event);

        var bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmail(
            eq("admin@meads.app"),
            anyString(),
            bodyCaptor.capture()
        );

        assertThat(bodyCaptor.getValue())
            .contains("ORDER-456")
            .contains("jumpseller")
            .contains("COMPETITION_EXCLUSIVITY")
            .contains("5");
    }

    @Test
    void shouldNotSendEntrantEmailWhenEmailIsNull() {
        var event = new OrderPendingReviewEvent(
            UUID.randomUUID(),
            "ORDER-789",
            "jumpseller",
            UUID.randomUUID(),
            null, // No entrant email
            1,
            "COMPETITION_EXCLUSIVITY"
        );

        pendingOrderHandler.onOrderPendingReview(event);

        // Should only send one email (to admin)
        verify(emailService, times(1)).sendEmail(anyString(), anyString(), anyString());
        verify(emailService).sendEmail(eq("admin@meads.app"), anyString(), anyString());
    }

    @Test
    void shouldFormatExclusivityReasonForEntrant() {
        var event = new OrderPendingReviewEvent(
            UUID.randomUUID(),
            "ORDER-EXCL",
            "jumpseller",
            UUID.randomUUID(),
            "exclusive@example.com",
            1,
            "COMPETITION_EXCLUSIVITY"
        );

        pendingOrderHandler.onOrderPendingReview(event);

        var bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(2)).sendEmail(anyString(), anyString(), bodyCaptor.capture());

        // One of the emails (to entrant) should have user-friendly message
        var entrantBody = bodyCaptor.getAllValues().stream()
            .filter(body -> body.contains("home or commercial"))
            .findFirst();

        assertThat(entrantBody).isPresent();
    }
}
