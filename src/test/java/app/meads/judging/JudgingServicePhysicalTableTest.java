package app.meads.judging;

import app.meads.BusinessRuleException;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import app.meads.competition.ScoringSystem;
import app.meads.entry.EntryService;
import app.meads.judging.internal.BosPlacementRepository;
import app.meads.judging.internal.CategoryJudgingConfigRepository;
import app.meads.judging.internal.JudgingRepository;
import app.meads.judging.internal.JudgingRoundRepository;
import app.meads.judging.internal.JudgingServiceImpl;
import app.meads.judging.internal.MedalAwardRepository;
import app.meads.judging.internal.PhysicalTableRepository;
import app.meads.judging.internal.ScoresheetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Focused tests for the PhysicalTable CRUD + the round-side validations that
 * landed in batches 2 and 3 of the JudgingRound/PhysicalTable refactor —
 * back-fill of the rejection-path coverage the rest of the suite had only
 * implicit via happy-path integration tests.
 */
@ExtendWith(MockitoExtension.class)
class JudgingServicePhysicalTableTest {

    @InjectMocks JudgingServiceImpl service;

    @Mock JudgingRepository judgingRepository;
    @Mock JudgingRoundRepository judgingRoundRepository;
    @Mock PhysicalTableRepository physicalTableRepository;
    @Mock ScoresheetRepository scoresheetRepository;
    @Mock CategoryJudgingConfigRepository categoryConfigRepository;
    @Mock MedalAwardRepository medalAwardRepository;
    @Mock CompetitionService competitionService;
    @Mock JudgeProfileService judgeProfileService;
    @Mock ScoresheetService scoresheetService;
    @Mock BosPlacementRepository bosPlacementRepository;
    @Mock EntryService entryService;
    @Mock CoiCheckService coiCheckService;
    @Mock ApplicationEventPublisher eventPublisher;

    UUID divisionId;
    UUID adminUserId;
    Division division;
    Judging judging;

    @BeforeEach
    void setUp() {
        divisionId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();
        division = new Division(UUID.randomUUID(), "Amateur", "amateur",
                ScoringSystem.MJP,
                LocalDateTime.of(2026, 6, 1, 23, 59),
                "Europe/Lisbon");
        judging = new Judging(divisionId);
    }

    // === createPhysicalTable ===

    @Test
    void shouldRejectCreatePhysicalTableWithBlankLabel() {
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);

        assertThatThrownBy(() -> service.createPhysicalTable(divisionId, "   ", adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.physical-table.label-required");
        then(physicalTableRepository).should(never()).save(any());
    }

    @Test
    void shouldRejectCreatePhysicalTableWithDuplicateLabel() {
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(physicalTableRepository.existsByDivisionIdAndLabel(divisionId, "Table 1")).willReturn(true);

        assertThatThrownBy(() -> service.createPhysicalTable(divisionId, "Table 1", adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.physical-table.label-duplicate");
    }

    @Test
    void shouldRejectCreatePhysicalTableWhenUnauthorized() {
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(false);

        assertThatThrownBy(() -> service.createPhysicalTable(divisionId, "Table 1", adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.auth.unauthorized");
    }

    // === updatePhysicalTableLabel ===

    @Test
    void shouldRejectUpdatePhysicalTableLabelWithBlankLabel() {
        var pt = new PhysicalTable(divisionId, "Table 1");
        given(physicalTableRepository.findById(pt.getId())).willReturn(Optional.of(pt));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);

        assertThatThrownBy(() -> service.updatePhysicalTableLabel(pt.getId(), "  ", adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.physical-table.label-required");
    }

    @Test
    void shouldRejectUpdatePhysicalTableLabelWithDuplicate() {
        var pt = new PhysicalTable(divisionId, "Table 1");
        given(physicalTableRepository.findById(pt.getId())).willReturn(Optional.of(pt));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(physicalTableRepository.existsByDivisionIdAndLabel(divisionId, "Table 2")).willReturn(true);

        assertThatThrownBy(() -> service.updatePhysicalTableLabel(pt.getId(), "Table 2", adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.physical-table.label-duplicate");
    }

    // === deletePhysicalTable ===

    @Test
    void shouldRejectDeletePhysicalTableWhenInUseByRound() {
        var pt = new PhysicalTable(divisionId, "Table 1");
        var round = new JudgingRound(judging.getId(), pt.getId(), "R1", UUID.randomUUID(), null);
        given(physicalTableRepository.findById(pt.getId())).willReturn(Optional.of(pt));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(judgingRoundRepository.findAll()).willReturn(List.of(round));

        assertThatThrownBy(() -> service.deletePhysicalTable(pt.getId(), adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.physical-table.in-use-by-round");
        then(physicalTableRepository).should(never()).delete(any());
    }

    @Test
    void shouldRejectDeletePhysicalTableWhenInUseByMedalRound() {
        var pt = new PhysicalTable(divisionId, "Table 1");
        var config = new app.meads.judging.CategoryJudgingConfig(UUID.randomUUID());
        config.assignToPhysicalTable(pt.getId());
        given(physicalTableRepository.findById(pt.getId())).willReturn(Optional.of(pt));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(judgingRoundRepository.findAll()).willReturn(List.of());
        given(categoryConfigRepository.findAll()).willReturn(List.of(config));

        assertThatThrownBy(() -> service.deletePhysicalTable(pt.getId(), adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.physical-table.in-use-by-medal-round");
    }

    @Test
    void shouldDeletePhysicalTableWhenNotInUse() {
        var pt = new PhysicalTable(divisionId, "Table 1");
        given(physicalTableRepository.findById(pt.getId())).willReturn(Optional.of(pt));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(judgingRoundRepository.findAll()).willReturn(List.of());
        given(categoryConfigRepository.findAll()).willReturn(List.of());

        assertThatNoException().isThrownBy(() -> service.deletePhysicalTable(pt.getId(), adminUserId));
        then(physicalTableRepository).should().delete(pt);
    }

    // === assignRoundToPhysicalTable ===

    @Test
    void shouldRejectAssignRoundToPhysicalTableAfterRoundStarted() {
        var pt = new PhysicalTable(divisionId, "Table 1");
        var round = new JudgingRound(judging.getId(), "R1", UUID.randomUUID(), null);
        round.startRound1(); // now ROUND_1
        given(judgingRoundRepository.findById(round.getId())).willReturn(Optional.of(round));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);

        assertThatThrownBy(() -> service.assignRoundToPhysicalTable(round.getId(), pt.getId(), adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.round.cannot-reassign-physical-table-after-start");
    }

    @Test
    void shouldRejectAssignRoundToPhysicalTableFromAnotherDivision() {
        var otherDivisionId = UUID.randomUUID();
        var pt = new PhysicalTable(otherDivisionId, "Other Table");
        var round = new JudgingRound(judging.getId(), "R1", UUID.randomUUID(), null);
        given(judgingRoundRepository.findById(round.getId())).willReturn(Optional.of(round));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(physicalTableRepository.findById(pt.getId())).willReturn(Optional.of(pt));

        assertThatThrownBy(() -> service.assignRoundToPhysicalTable(round.getId(), pt.getId(), adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.physical-table.wrong-division");
    }

    // === startRound validations (Batch 3) ===

    @Test
    void shouldRejectStartRoundWithoutPhysicalTable() {
        var round = new JudgingRound(judging.getId(), "R1", UUID.randomUUID(), null);
        round.assignJudge(UUID.randomUUID());
        round.assignJudge(UUID.randomUUID());
        given(judgingRoundRepository.findById(round.getId())).willReturn(Optional.of(round));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);

        assertThatThrownBy(() -> service.startRound(round.getId(), adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.round.physical-table-required");
        assertThat(round.getStatus()).isEqualTo(JudgingRoundStatus.NOT_STARTED);
    }

    @Test
    void shouldRejectStartRoundWhenPhysicalTableIsBusyWithAnotherActiveRound() {
        var ptId = UUID.randomUUID();
        var otherRound = new JudgingRound(judging.getId(), ptId, "R-Other", UUID.randomUUID(), null);
        otherRound.startRound1(); // already ACTIVE at the same physical table
        var newRound = new JudgingRound(judging.getId(), ptId, "R-New", UUID.randomUUID(), null);
        newRound.assignJudge(UUID.randomUUID());
        newRound.assignJudge(UUID.randomUUID());
        given(judgingRoundRepository.findById(newRound.getId())).willReturn(Optional.of(newRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(judgingRoundRepository.findByJudgingId(judging.getId()))
                .willReturn(List.of(otherRound, newRound));

        assertThatThrownBy(() -> service.startRound(newRound.getId(), adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.round.physical-table-busy");
    }

    @Test
    void shouldRejectStartRoundWhenJudgeIsOnAnotherActiveRound() {
        var ptIdA = UUID.randomUUID();
        var ptIdB = UUID.randomUUID();
        var conflictingJudgeId = UUID.randomUUID();
        var otherActiveRound = new JudgingRound(judging.getId(), ptIdA, "R-Other", UUID.randomUUID(), null);
        otherActiveRound.assignJudge(conflictingJudgeId);
        otherActiveRound.startRound1();
        var newRound = new JudgingRound(judging.getId(), ptIdB, "R-New", UUID.randomUUID(), null);
        newRound.assignJudge(conflictingJudgeId);
        newRound.assignJudge(UUID.randomUUID());
        given(judgingRoundRepository.findById(newRound.getId())).willReturn(Optional.of(newRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(judgingRoundRepository.findByJudgingId(judging.getId()))
                .willReturn(List.of(otherActiveRound, newRound));
        given(judgingRoundRepository.findAll()).willReturn(List.of(otherActiveRound, newRound));

        assertThatThrownBy(() -> service.startRound(newRound.getId(), adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.round.judge-active-conflict");
    }

    @Test
    void shouldRejectAssignJudgeToActiveRoundWhenJudgeIsOnAnotherActiveRound() {
        var conflictingJudgeId = UUID.randomUUID();
        var existingActiveRound = new JudgingRound(judging.getId(), UUID.randomUUID(), "R-Existing",
                UUID.randomUUID(), null);
        existingActiveRound.assignJudge(conflictingJudgeId);
        existingActiveRound.startRound1();
        var activeRound = new JudgingRound(judging.getId(), UUID.randomUUID(), "R-Active",
                UUID.randomUUID(), null);
        activeRound.startRound1(); // already ROUND_1 — assignJudge enforces conflict here
        given(judgingRoundRepository.findById(activeRound.getId())).willReturn(Optional.of(activeRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(judgingRoundRepository.findAll()).willReturn(List.of(existingActiveRound, activeRound));

        assertThatThrownBy(() -> service.assignJudge(activeRound.getId(), conflictingJudgeId, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.round.judge-active-conflict");
        assertThat(activeRound.getAssignments()).isEmpty();
    }

    @Test
    void shouldAllowAssigningJudgeToNotStartedRoundEvenIfJudgeIsOnAnotherActiveRound() {
        // Pre-planning: assigning to a NOT_STARTED round bypasses the conflict
        // check (it fires again at startRound time).
        var conflictingJudgeId = UUID.randomUUID();
        var existingActiveRound = new JudgingRound(judging.getId(), UUID.randomUUID(), "R-Existing",
                UUID.randomUUID(), null);
        existingActiveRound.assignJudge(conflictingJudgeId);
        existingActiveRound.startRound1();
        var futureRound = new JudgingRound(judging.getId(), UUID.randomUUID(), "R-Future",
                UUID.randomUUID(), null);
        given(judgingRoundRepository.findById(futureRound.getId())).willReturn(Optional.of(futureRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(judgingRoundRepository.save(any(JudgingRound.class))).willAnswer(inv -> inv.getArgument(0));

        assertThatNoException().isThrownBy(
                () -> service.assignJudge(futureRound.getId(), conflictingJudgeId, adminUserId));
        assertThat(futureRound.getAssignments()).hasSize(1);
    }
}
