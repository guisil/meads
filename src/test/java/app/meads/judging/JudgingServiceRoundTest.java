package app.meads.judging;

import app.meads.BusinessRuleException;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.ScoringSystem;
import app.meads.entry.Entry;
import app.meads.entry.EntryService;
import app.meads.judging.internal.BosPlacementRepository;
import app.meads.judging.internal.CategoryJudgingConfigRepository;
import app.meads.judging.internal.JudgingRepository;
import app.meads.judging.internal.JudgingServiceImpl;
import app.meads.judging.internal.JudgingRoundRepository;
import app.meads.judging.internal.MedalAwardRepository;
import app.meads.judging.internal.ScoresheetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class JudgingServiceRoundTest {

    @InjectMocks
    JudgingServiceImpl service;

    @Mock
    JudgingRepository judgingRepository;

    @Mock
    JudgingRoundRepository judgingRoundRepository;

    @Mock
    ScoresheetRepository scoresheetRepository;

    @Mock
    CategoryJudgingConfigRepository categoryConfigRepository;

    @Mock
    MedalAwardRepository medalAwardRepository;

    @Mock
    CompetitionService competitionService;

    @Mock
    JudgeProfileService judgeProfileService;

    @Mock
    ScoresheetService scoresheetService;

    @Mock
    BosPlacementRepository bosPlacementRepository;

    @Mock
    EntryService entryService;

    @Mock
    CoiCheckService coiCheckService;

    @Mock
    ApplicationEventPublisher eventPublisher;

    UUID divisionId;
    UUID adminUserId;
    UUID judgeUserId;
    UUID divisionCategoryId;
    Division division;

    @BeforeEach
    void setUp() {
        divisionId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();
        judgeUserId = UUID.randomUUID();
        divisionCategoryId = UUID.randomUUID();
        division = new Division(UUID.randomUUID(), "Amateur", "amateur",
                ScoringSystem.MJP,
                LocalDateTime.of(2026, 6, 1, 23, 59),
                "Europe/Lisbon");
        lenient().when(competitionService.findDivisionById(any())).thenReturn(division);
    }

    @Test
    void shouldCreateJudgingWhenAbsentOnEnsureJudgingExists() {
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.empty());
        given(judgingRepository.save(any(Judging.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var judging = service.ensureJudgingExists(divisionId);

        assertThat(judging.getDivisionId()).isEqualTo(divisionId);
        assertThat(judging.getPhase()).isEqualTo(JudgingPhase.NOT_STARTED);
    }

    @Test
    void shouldReturnExistingJudgingOnEnsureJudgingExists() {
        var existing = new Judging(divisionId);
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(existing));

        var judging = service.ensureJudgingExists(divisionId);

        assertThat(judging).isSameAs(existing);
        then(judgingRepository).should(never()).save(any(Judging.class));
    }

    @Test
    void shouldCreateTableWhenAuthorized() {
        var judging = new Judging(divisionId);
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var table = service.createRound(judging.getId(), "Table 1",
                divisionCategoryId, LocalDate.of(2026, 7, 1), adminUserId);

        assertThat(table.getName()).isEqualTo("Table 1");
        assertThat(table.getDivisionCategoryId()).isEqualTo(divisionCategoryId);
        assertThat(table.getStatus()).isEqualTo(JudgingRoundStatus.PENDING);
    }

    @Test
    void shouldRejectCreateTableWhenNotAuthorized() {
        var judging = new Judging(divisionId);
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(false);

        assertThatThrownBy(() -> service.createRound(judging.getId(), "Table 1",
                divisionCategoryId, LocalDate.of(2026, 7, 1), adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.auth.unauthorized");

        then(judgingRoundRepository).should(never()).save(any(JudgingRound.class));
    }

    @Test
    void shouldUpdateTableNameWhenAuthorized() {
        var judging = new Judging(divisionId);
        var table = new JudgingRound(judging.getId(), "Old Name", divisionCategoryId, null);
        given(judgingRoundRepository.findById(table.getId())).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.updateRoundName(table.getId(), "New Name", adminUserId);

        assertThat(table.getName()).isEqualTo("New Name");
    }

    @Test
    void shouldUpdateTableScheduledDateWhenAuthorized() {
        var judging = new Judging(divisionId);
        var table = new JudgingRound(judging.getId(), "Table 1", divisionCategoryId, null);
        given(judgingRoundRepository.findById(table.getId())).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.updateRoundScheduledDate(table.getId(), LocalDate.of(2026, 7, 5), adminUserId);

        assertThat(table.getScheduledDate()).isEqualTo(LocalDate.of(2026, 7, 5));
    }

    @Test
    void shouldDeleteTableWhenNotStartedAndNoAssignments() {
        var judging = new Judging(divisionId);
        var table = new JudgingRound(judging.getId(), "Table 1", divisionCategoryId, null);
        given(judgingRoundRepository.findById(table.getId())).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);

        service.deleteRound(table.getId(), adminUserId);

        then(judgingRoundRepository).should().delete(table);
    }

    @Test
    void shouldRejectDeleteTableWhenStatusNotNotStarted() {
        var judging = new Judging(divisionId);
        var table = new JudgingRound(judging.getId(), "Table 1", divisionCategoryId, null);
        table.start();
        given(judgingRoundRepository.findById(table.getId())).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);

        assertThatThrownBy(() -> service.deleteRound(table.getId(), adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.judging-table.cannot-delete-started");

        then(judgingRoundRepository).should(never()).delete(any(JudgingRound.class));
    }

    @Test
    void shouldRejectDeleteTableWhenAssignmentsExist() {
        var judging = new Judging(divisionId);
        var table = new JudgingRound(judging.getId(), "Table 1", divisionCategoryId, null);
        table.assignJudge(judgeUserId);
        given(judgingRoundRepository.findById(table.getId())).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);

        assertThatThrownBy(() -> service.deleteRound(table.getId(), adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.judging-table.has-assignments");
    }

    @Test
    void shouldAssignJudgeAndEnsureProfile() {
        var judging = new Judging(divisionId);
        var table = new JudgingRound(judging.getId(), "Table 1", divisionCategoryId, null);
        given(judgingRoundRepository.findById(table.getId())).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.assignJudge(table.getId(), judgeUserId, adminUserId);

        assertThat(table.getAssignments()).hasSize(1);
        assertThat(table.getAssignments().get(0).getJudgeUserId()).isEqualTo(judgeUserId);
        then(judgeProfileService).should().ensureProfileForJudge(judgeUserId);
    }

    @Test
    void shouldRejectAssignJudgeWhenJudgeOwnsEntryInRoundCategory() {
        var judging = new Judging(divisionId);
        var table = new JudgingRound(judging.getId(), "Table 1", divisionCategoryId, null);
        var conflictingEntry = mock(Entry.class);
        var conflictingEntryId = UUID.randomUUID();
        given(conflictingEntry.getId()).willReturn(conflictingEntryId);
        given(conflictingEntry.getEntryNumber()).willReturn(7);
        given(judgingRoundRepository.findById(table.getId())).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(entryService.findEntriesByFinalCategoryId(divisionCategoryId))
                .willReturn(List.of(conflictingEntry));
        given(coiCheckService.check(judgeUserId, conflictingEntryId))
                .willReturn(CoiCheckService.CoiResult.blocking());

        assertThatThrownBy(() -> service.assignJudge(table.getId(), judgeUserId, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.coi.assign-hard-block");

        assertThat(table.getAssignments()).isEmpty();
        then(judgeProfileService).should(never()).ensureProfileForJudge(any());
        then(judgingRoundRepository).should(never()).save(any(JudgingRound.class));
    }

    @Test
    void shouldBeIdempotentOnAssignJudge() {
        var judging = new Judging(divisionId);
        var table = new JudgingRound(judging.getId(), "Table 1", divisionCategoryId, null);
        table.assignJudge(judgeUserId);
        given(judgingRoundRepository.findById(table.getId())).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.assignJudge(table.getId(), judgeUserId, adminUserId);

        assertThat(table.getAssignments()).hasSize(1);
    }

    @Test
    void shouldRemoveJudgeFromTableWhenNotStarted() {
        var judging = new Judging(divisionId);
        var table = new JudgingRound(judging.getId(), "Table 1", divisionCategoryId, null);
        table.assignJudge(judgeUserId);
        given(judgingRoundRepository.findById(table.getId())).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.removeJudge(table.getId(), judgeUserId, adminUserId);

        assertThat(table.getAssignments()).isEmpty();
    }

    @Test
    void shouldRejectRemoveJudgeWhenWouldDropBelowMinJudgesAndStarted() {
        var judging = new Judging(divisionId);
        var table = new JudgingRound(judging.getId(), "Table 1", divisionCategoryId, null);
        var judge2 = UUID.randomUUID();
        table.assignJudge(judgeUserId);
        table.assignJudge(judge2);
        table.start();
        given(judgingRoundRepository.findById(table.getId())).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);

        assertThatThrownBy(() -> service.removeJudge(table.getId(), judgeUserId, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.judge-assignment.below-minimum");

        assertThat(table.getAssignments()).hasSize(2);
    }

    @Test
    void shouldAllowRemoveJudgeWhenStartedButStaysAboveMinimum() {
        var judging = new Judging(divisionId);
        var table = new JudgingRound(judging.getId(), "Table 1", divisionCategoryId, null);
        table.assignJudge(judgeUserId);
        table.assignJudge(UUID.randomUUID());
        table.assignJudge(UUID.randomUUID());
        table.start();
        given(judgingRoundRepository.findById(table.getId())).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.removeJudge(table.getId(), judgeUserId, adminUserId);

        assertThat(table.getAssignments()).hasSize(2);
    }

    @Test
    void shouldFindTablesByJudging() {
        var judging = new Judging(divisionId);
        var t1 = new JudgingRound(judging.getId(), "T1", divisionCategoryId, null);
        var t2 = new JudgingRound(judging.getId(), "T2", divisionCategoryId, null);
        given(judgingRoundRepository.findByJudgingId(judging.getId()))
                .willReturn(List.of(t1, t2));

        var result = service.findRoundsByJudgingId(judging.getId());

        assertThat(result).containsExactly(t1, t2);
    }

    @Test
    void shouldAssignEntryToRound() {
        var judging = new Judging(divisionId);
        var round = new JudgingRound(judging.getId(), "T1", divisionCategoryId, null);
        var entryId = UUID.randomUUID();
        given(judgingRoundRepository.findById(round.getId())).willReturn(Optional.of(round));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.assignEntryToRound(round.getId(), entryId, adminUserId);

        assertThat(round.getEntries()).contains(entryId);
    }

    @Test
    void shouldUnassignEntryFromRound() {
        var judging = new Judging(divisionId);
        var round = new JudgingRound(judging.getId(), "T1", divisionCategoryId, null);
        var entryId = UUID.randomUUID();
        round.assignEntry(entryId);
        given(judgingRoundRepository.findById(round.getId())).willReturn(Optional.of(round));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.unassignEntryFromRound(round.getId(), entryId, adminUserId);

        assertThat(round.getEntries()).doesNotContain(entryId);
    }
}
