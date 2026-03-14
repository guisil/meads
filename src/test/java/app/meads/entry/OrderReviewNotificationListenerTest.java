package app.meads.entry;

import app.meads.competition.Competition;
import app.meads.competition.CompetitionService;
import app.meads.entry.internal.OrderReviewNotificationListener;
import app.meads.identity.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class OrderReviewNotificationListenerTest {

    @Mock CompetitionService competitionService;
    @Mock EmailService emailService;
    @InjectMocks OrderReviewNotificationListener listener;

    @Test
    void shouldSendAlertToAllCompetitionAdmins() {
        var competitionId = UUID.randomUUID();
        var competition = mock(Competition.class);
        given(competition.getName()).willReturn("Test Comp");
        given(competitionService.findCompetitionById(competitionId)).willReturn(competition);
        given(competitionService.findAdminEmailsByCompetitionId(competitionId))
                .willReturn(List.of("admin1@test.com", "admin2@test.com"));

        var event = new OrderRequiresReviewEvent(
                UUID.randomUUID(), "ORD-123", "John", "john@test.com",
                Set.of(competitionId), Set.of("Profissional"), OrderStatus.NEEDS_REVIEW);

        listener.on(event);

        then(emailService).should().sendOrderReviewAlert(
                "admin1@test.com", "Test Comp", "ORD-123", "John", "Profissional");
        then(emailService).should().sendOrderReviewAlert(
                "admin2@test.com", "Test Comp", "ORD-123", "John", "Profissional");
    }

    @Test
    void shouldNotSendAnyEmailsWhenNoAdmins() {
        var competitionId = UUID.randomUUID();
        var competition = mock(Competition.class);
        given(competitionService.findCompetitionById(competitionId)).willReturn(competition);
        given(competitionService.findAdminEmailsByCompetitionId(competitionId))
                .willReturn(List.of());

        var event = new OrderRequiresReviewEvent(
                UUID.randomUUID(), "ORD-456", "Jane", "jane@test.com",
                Set.of(competitionId), Set.of("Amadora"), OrderStatus.PARTIALLY_PROCESSED);

        listener.on(event);

        then(emailService).shouldHaveNoInteractions();
    }
}
