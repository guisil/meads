package app.meads.entry;

import app.meads.competition.Competition;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.entry.internal.CreditNotificationListener;
import app.meads.identity.EmailService;
import app.meads.identity.JwtMagicLinkService;
import app.meads.identity.User;
import app.meads.identity.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CreditNotificationListenerTest {

    @Mock CompetitionService competitionService;
    @Mock UserService userService;
    @Mock EmailService emailService;
    @Mock JwtMagicLinkService jwtMagicLinkService;
    @InjectMocks CreditNotificationListener listener;

    @Test
    void shouldSendCreditNotificationWithMagicLink() {
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var competitionId = UUID.randomUUID();

        var division = mock(Division.class);
        given(division.getName()).willReturn("Home");
        given(division.getCompetitionId()).willReturn(competitionId);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);

        var competition = mock(Competition.class);
        given(competition.getName()).willReturn("CHIP 2026");
        given(competition.getContactEmail()).willReturn("admin@chip.pt");
        given(competitionService.findCompetitionById(competitionId)).willReturn(competition);

        var user = mock(User.class);
        given(user.getEmail()).willReturn("entrant@test.com");
        given(userService.findById(userId)).willReturn(user);

        given(jwtMagicLinkService.generateLink(eq("entrant@test.com"), any(Duration.class)))
                .willReturn("http://localhost:8080/login/magic?token=abc123");

        var event = new CreditsAwardedEvent(divisionId, userId, 3, "WEBHOOK");

        listener.on(event);

        then(emailService).should().sendCreditNotification(
                eq("entrant@test.com"),
                eq(3), eq("Home"), eq("CHIP 2026"),
                eq("http://localhost:8080/login/magic?token=abc123"),
                eq("admin@chip.pt"));
    }
}
