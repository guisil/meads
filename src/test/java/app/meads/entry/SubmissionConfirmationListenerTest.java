package app.meads.entry;

import app.meads.competition.Competition;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.entry.internal.SubmissionConfirmationListener;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionConfirmationListenerTest {

    @Mock CompetitionService competitionService;
    @Mock UserService userService;
    @Mock EmailService emailService;
    @Mock JwtMagicLinkService jwtMagicLinkService;
    @InjectMocks SubmissionConfirmationListener listener;

    @Test
    void shouldSendSummaryEmailWithMagicLink() {
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var competitionId = UUID.randomUUID();

        var division = mock(Division.class);
        given(division.getName()).willReturn("Amadora");
        given(division.getCompetitionId()).willReturn(competitionId);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);

        var competition = mock(Competition.class);
        given(competition.getName()).willReturn("CHIP 2026");
        given(competitionService.findCompetitionById(competitionId)).willReturn(competition);

        var user = mock(User.class);
        given(user.getEmail()).willReturn("entrant@test.com");
        given(userService.findById(userId)).willReturn(user);

        given(jwtMagicLinkService.generateLink(eq("entrant@test.com"), any(Duration.class)))
                .willReturn("http://localhost:8080/login/magic?token=abc123");

        var details = List.of(
                new EntryDetail(1, "My Mead", "M1A", "Traditional Mead (Dry)"),
                new EntryDetail(2, "Berry Mead", "M2C", "Berry Melomel"));
        var event = new EntriesSubmittedEvent(divisionId, userId, details);

        listener.on(event);

        then(emailService).should().sendSubmissionConfirmation(
                eq("entrant@test.com"), eq("CHIP 2026"), eq("Amadora"),
                argThat(lines -> lines.stream().anyMatch(l -> l.contains("My Mead"))),
                eq("http://localhost:8080/login/magic?token=abc123"),
                any(Locale.class));
    }

    @Test
    void shouldFormatEntryDetailsInSummary() {
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var competitionId = UUID.randomUUID();

        var division = mock(Division.class);
        given(division.getName()).willReturn("Pro");
        given(division.getCompetitionId()).willReturn(competitionId);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);

        var competition = mock(Competition.class);
        given(competition.getName()).willReturn("Test Comp");
        given(competitionService.findCompetitionById(competitionId)).willReturn(competition);

        var user = mock(User.class);
        given(user.getEmail()).willReturn("solo@test.com");
        given(userService.findById(userId)).willReturn(user);

        given(jwtMagicLinkService.generateLink(eq("solo@test.com"), any(Duration.class)))
                .willReturn("http://localhost:8080/login/magic?token=xyz");

        var details = List.of(
                new EntryDetail(1, "Solo Mead", "M4B", "Historical Mead"));
        var event = new EntriesSubmittedEvent(divisionId, userId, details);

        listener.on(event);

        @SuppressWarnings("unchecked")
        var linesCaptor = org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        then(emailService).should().sendSubmissionConfirmation(
                eq("solo@test.com"), eq("Test Comp"), eq("Pro"),
                linesCaptor.capture(),
                eq("http://localhost:8080/login/magic?token=xyz"),
                any(Locale.class));
        var lines = linesCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(lines).hasSize(1);
        org.assertj.core.api.Assertions.assertThat((String) lines.getFirst())
                .contains("#1")
                .contains("Solo Mead")
                .contains("M4B")
                .contains("Historical Mead");
    }
}
