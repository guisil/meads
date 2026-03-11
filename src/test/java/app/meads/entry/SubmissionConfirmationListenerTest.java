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
    void shouldSendConfirmationEmailOnSubmission() {
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

        var event = new EntriesSubmittedEvent(divisionId, userId, 3);

        listener.on(event);

        then(emailService).should().sendSubmissionConfirmation(
                eq("entrant@test.com"), eq("CHIP 2026"), eq("Amadora"),
                eq(3), contains("chip-2026/divisions/amadora/my-entries"));
    }

    @Test
    void shouldUseSingularEntryForOneEntry() {
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

        var event = new EntriesSubmittedEvent(divisionId, userId, 1);

        listener.on(event);

        then(emailService).should().sendSubmissionConfirmation(
                eq("solo@test.com"), eq("Test Comp"), eq("Pro"),
                eq(1), contains("test-comp/divisions/pro/my-entries"));
    }
}
