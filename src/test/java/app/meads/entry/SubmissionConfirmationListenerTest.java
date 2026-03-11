package app.meads.entry;

import app.meads.competition.Competition;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.entry.internal.SubmissionConfirmationListener;
import app.meads.identity.EmailService;
import app.meads.identity.User;
import app.meads.identity.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionConfirmationListenerTest {

    @Mock CompetitionService competitionService;
    @Mock UserService userService;
    @Mock EmailService emailService;
    @InjectMocks SubmissionConfirmationListener listener;

    @Test
    void shouldSendSummaryEmailWithEntryDetails() {
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var competitionId = UUID.randomUUID();

        var division = mock(Division.class);
        given(division.getName()).willReturn("Amadora");
        given(division.getCompetitionId()).willReturn(competitionId);
        given(division.getShortName()).willReturn("amadora");
        given(competitionService.findDivisionById(divisionId)).willReturn(division);

        var competition = mock(Competition.class);
        given(competition.getName()).willReturn("CHIP 2026");
        given(competition.getShortName()).willReturn("chip-2026");
        given(competitionService.findCompetitionById(competitionId)).willReturn(competition);

        var user = mock(User.class);
        given(user.getEmail()).willReturn("entrant@test.com");
        given(userService.findById(userId)).willReturn(user);

        var details = List.of(
                new EntryDetail(1, "My Mead", "M1A", "Traditional Mead (Dry)"),
                new EntryDetail(2, "Berry Mead", "M2C", "Berry Melomel"));
        var event = new EntriesSubmittedEvent(divisionId, userId, details);

        listener.on(event);

        then(emailService).should().sendSubmissionConfirmation(
                eq("entrant@test.com"), eq("CHIP 2026"), eq("Amadora"),
                contains("My Mead"),
                contains("chip-2026/divisions/amadora/my-entries"));
    }

    @Test
    void shouldFormatEntryDetailsInSummary() {
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var competitionId = UUID.randomUUID();

        var division = mock(Division.class);
        given(division.getName()).willReturn("Pro");
        given(division.getCompetitionId()).willReturn(competitionId);
        given(division.getShortName()).willReturn("pro");
        given(competitionService.findDivisionById(divisionId)).willReturn(division);

        var competition = mock(Competition.class);
        given(competition.getName()).willReturn("Test Comp");
        given(competition.getShortName()).willReturn("test-comp");
        given(competitionService.findCompetitionById(competitionId)).willReturn(competition);

        var user = mock(User.class);
        given(user.getEmail()).willReturn("solo@test.com");
        given(userService.findById(userId)).willReturn(user);

        var details = List.of(
                new EntryDetail(1, "Solo Mead", "M4B", "Historical Mead"));
        var event = new EntriesSubmittedEvent(divisionId, userId, details);

        listener.on(event);

        var summaryCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        then(emailService).should().sendSubmissionConfirmation(
                eq("solo@test.com"), eq("Test Comp"), eq("Pro"),
                summaryCaptor.capture(),
                contains("test-comp/divisions/pro/my-entries"));
        var summary = summaryCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(summary)
                .contains("#1")
                .contains("Solo Mead")
                .contains("M4B")
                .contains("Historical Mead");
    }
}
