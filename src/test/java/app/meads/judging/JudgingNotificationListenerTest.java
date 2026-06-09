package app.meads.judging;

import app.meads.competition.Competition;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import app.meads.entry.Entry;
import app.meads.entry.EntryService;
import app.meads.identity.EmailService;
import app.meads.identity.JwtMagicLinkService;
import app.meads.identity.User;
import app.meads.identity.UserService;
import app.meads.judging.RoundType;
import app.meads.judging.internal.JudgeAssignment;
import app.meads.judging.internal.JudgingNotificationListener;
import app.meads.judging.internal.JudgingRoundRepository;
import app.meads.judging.internal.ScoresheetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class JudgingNotificationListenerTest {

    @Mock JudgingRoundRepository judgingRoundRepository;
    @Mock ScoresheetRepository scoresheetRepository;
    @Mock CompetitionService competitionService;
    @Mock EntryService entryService;
    @Mock UserService userService;
    @Mock EmailService emailService;
    @Mock JwtMagicLinkService jwtMagicLinkService;
    @InjectMocks JudgingNotificationListener listener;

    @Test
    void shouldEmailAssignedJudgesWhenTableStarted() {
        var roundId = UUID.randomUUID();
        var divisionCategoryId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var competitionId = UUID.randomUUID();
        var judge1Id = UUID.randomUUID();
        var judge2Id = UUID.randomUUID();

        var assignment1 = mock(JudgeAssignment.class);
        given(assignment1.getJudgeUserId()).willReturn(judge1Id);
        var assignment2 = mock(JudgeAssignment.class);
        given(assignment2.getJudgeUserId()).willReturn(judge2Id);

        var table = mock(JudgingRound.class);
        given(table.getName()).willReturn("Table 1");
        given(table.getAssignments()).willReturn(List.of(assignment1, assignment2));
        given(judgingRoundRepository.findById(roundId)).willReturn(Optional.of(table));

        var division = mock(Division.class);
        given(division.getName()).willReturn("Home");
        given(division.getCompetitionId()).willReturn(competitionId);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);

        var competition = mock(Competition.class);
        given(competition.getName()).willReturn("CHIP 2026");
        given(competitionService.findCompetitionById(competitionId)).willReturn(competition);

        var category = mock(DivisionCategory.class);
        given(category.getCode()).willReturn("M1A");
        given(category.getName(any())).willReturn("Dry Mead");
        given(competitionService.findDivisionCategoryById(divisionCategoryId)).willReturn(category);

        var judge1 = mock(User.class);
        given(judge1.getEmail()).willReturn("judge1@test.com");
        given(userService.findById(judge1Id)).willReturn(judge1);
        var judge2 = mock(User.class);
        given(judge2.getEmail()).willReturn("judge2@test.com");
        given(userService.findById(judge2Id)).willReturn(judge2);

        given(jwtMagicLinkService.generateLink(anyString(), any(Duration.class)))
                .willReturn("http://localhost:8080/login/magic?token=abc");

        listener.on(new RoundStartedEvent(roundId, divisionCategoryId, divisionId, Instant.now()));

        then(emailService).should().sendJudgingTableReady(
                eq("judge1@test.com"), eq("Table 1"), eq("M1A — Dry Mead"),
                eq("CHIP 2026"), eq("Home"), anyString(), any(Locale.class));
        then(emailService).should().sendJudgingTableReady(
                eq("judge2@test.com"), eq("Table 1"), eq("M1A — Dry Mead"),
                eq("CHIP 2026"), eq("Home"), anyString(), any(Locale.class));
    }

    @Test
    void shouldEmailJudgeWhenScoresheetReverted() {
        var scoresheetId = UUID.randomUUID();
        var entryId = UUID.randomUUID();
        var roundId = UUID.randomUUID();
        var judgeId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var competitionId = UUID.randomUUID();

        var scoresheet = mock(Scoresheet.class);
        given(scoresheet.getFilledByJudgeUserId()).willReturn(judgeId);
        given(scoresheetRepository.findById(scoresheetId)).willReturn(Optional.of(scoresheet));

        var entry = mock(Entry.class);
        given(entry.getEntryCode()).willReturn("A-042");
        given(entry.getDivisionId()).willReturn(divisionId);
        given(entryService.findEntryById(entryId)).willReturn(entry);

        var division = mock(Division.class);
        given(division.getName()).willReturn("Home");
        given(division.getCompetitionId()).willReturn(competitionId);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);

        var competition = mock(Competition.class);
        given(competition.getName()).willReturn("CHIP 2026");
        given(competitionService.findCompetitionById(competitionId)).willReturn(competition);

        var judge = mock(User.class);
        given(judge.getEmail()).willReturn("judge@test.com");
        given(userService.findById(judgeId)).willReturn(judge);

        given(jwtMagicLinkService.generateLink(anyString(), any(Duration.class)))
                .willReturn("http://localhost:8080/login/magic?token=abc");

        listener.on(new ScoresheetRevertedEvent(scoresheetId, entryId, roundId, Instant.now()));

        then(emailService).should().sendScoresheetReverted(
                eq("judge@test.com"), eq("A-042"), eq("CHIP 2026"), eq("Home"),
                anyString(), any(Locale.class));
    }

    @Test
    void shouldNotEmailWhenRevertedScoresheetWasNeverFilled() {
        var scoresheetId = UUID.randomUUID();
        var entryId = UUID.randomUUID();
        var roundId = UUID.randomUUID();

        var scoresheet = mock(Scoresheet.class);
        given(scoresheet.getFilledByJudgeUserId()).willReturn(null);
        given(scoresheetRepository.findById(scoresheetId)).willReturn(Optional.of(scoresheet));

        listener.on(new ScoresheetRevertedEvent(scoresheetId, entryId, roundId, Instant.now()));

        then(emailService).shouldHaveNoInteractions();
    }

    @Test
    void shouldEmailOnlyTheMedalRoundJudgesNotPrelimScoringJudges() {
        // Real split-category scenario: prelim panels have different judges from
        // the medal round. Only the MEDAL round's judges must be notified.
        var divisionCategoryId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var competitionId = UUID.randomUUID();
        var judge1Id = UUID.randomUUID();
        var judge2Id = UUID.randomUUID();
        var judge4Id = UUID.randomUUID();
        var judge5Id = UUID.randomUUID();

        // Prelim scoring panels — judge4 + judge5 here must NOT be notified.
        var panelA = mock(JudgingRound.class);
        given(panelA.getType()).willReturn(RoundType.SCORING);
        var panelB = mock(JudgingRound.class);
        given(panelB.getType()).willReturn(RoundType.SCORING);
        var a4 = mock(JudgeAssignment.class);
        lenient().when(a4.getJudgeUserId()).thenReturn(judge4Id);
        var a5 = mock(JudgeAssignment.class);
        lenient().when(a5.getJudgeUserId()).thenReturn(judge5Id);
        lenient().when(panelA.getAssignments()).thenReturn(List.of(a4));
        lenient().when(panelB.getAssignments()).thenReturn(List.of(a5));

        // The medal round — only judge1 + judge2.
        var a1 = mock(JudgeAssignment.class);
        given(a1.getJudgeUserId()).willReturn(judge1Id);
        var a2 = mock(JudgeAssignment.class);
        given(a2.getJudgeUserId()).willReturn(judge2Id);
        var medalRound = mock(JudgingRound.class);
        given(medalRound.getType()).willReturn(RoundType.MEDAL);
        given(medalRound.getAssignments()).willReturn(List.of(a1, a2));

        given(judgingRoundRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(List.of(panelA, panelB, medalRound));

        var division = mock(Division.class);
        given(division.getName()).willReturn("Home");
        given(division.getCompetitionId()).willReturn(competitionId);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);

        var competition = mock(Competition.class);
        given(competition.getName()).willReturn("CHIP 2026");
        given(competitionService.findCompetitionById(competitionId)).willReturn(competition);

        var category = mock(DivisionCategory.class);
        given(category.getCode()).willReturn("M1A");
        given(category.getName(any())).willReturn("Dry Mead");
        given(competitionService.findDivisionCategoryById(divisionCategoryId)).willReturn(category);

        var judge1 = mock(User.class);
        given(judge1.getEmail()).willReturn("judge1@test.com");
        given(userService.findById(judge1Id)).willReturn(judge1);
        var judge2 = mock(User.class);
        given(judge2.getEmail()).willReturn("judge2@test.com");
        given(userService.findById(judge2Id)).willReturn(judge2);

        given(jwtMagicLinkService.generateLink(anyString(), any(Duration.class)))
                .willReturn("http://localhost:8080/login/magic?token=abc");

        listener.on(new MedalRoundActivatedEvent(divisionCategoryId, divisionId,
                MedalRoundMode.COMPARATIVE, Instant.now()));

        then(emailService).should().sendMedalRoundReady(
                eq("judge1@test.com"), eq("M1A — Dry Mead"), eq("CHIP 2026"), eq("Home"),
                anyString(), any(Locale.class));
        then(emailService).should().sendMedalRoundReady(
                eq("judge2@test.com"), eq("M1A — Dry Mead"), eq("CHIP 2026"), eq("Home"),
                anyString(), any(Locale.class));
        then(emailService).should(never()).sendMedalRoundReady(
                eq("judge4@test.com"), any(), any(), any(), any(), any());
        then(emailService).should(never()).sendMedalRoundReady(
                eq("judge5@test.com"), any(), any(), any(), any(), any());
    }
}
