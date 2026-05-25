package app.meads.judging;

import app.meads.BusinessRuleException;
import app.meads.competition.Competition;
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

import java.time.LocalDate;
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
import static org.mockito.Mockito.lenient;
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
    Competition competition;
    Judging judging;

    @BeforeEach
    void setUp() {
        divisionId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();
        competition = new Competition("Amateur Competition", "amateur-competition",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "Lisboa");
        // Default tests run with sharedTables=false to exercise the per-division
        // busy-check path; cross-division tests opt in to true explicitly.
        competition.updateSharedTables(false);
        division = new Division(competition.getId(), "Amateur", "amateur",
                ScoringSystem.MJP,
                LocalDateTime.of(2026, 6, 1, 23, 59),
                "Europe/Lisbon");
        division.advanceStatus(); // DRAFT → REGISTRATION_OPEN
        division.advanceStatus(); // REGISTRATION_OPEN → REGISTRATION_CLOSED
        division.advanceStatus(); // REGISTRATION_CLOSED → JUDGING (startRound requires this)
        judging = new Judging(divisionId);
        lenient().when(competitionService.findCompetitionById(any())).thenReturn(competition);
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
    void shouldDeletePhysicalTableWhenNotInUse() {
        var pt = new PhysicalTable(divisionId, "Table 1");
        given(physicalTableRepository.findById(pt.getId())).willReturn(Optional.of(pt));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(judgingRoundRepository.findAll()).willReturn(List.of());

        assertThatNoException().isThrownBy(() -> service.deletePhysicalTable(pt.getId(), adminUserId));
        then(physicalTableRepository).should().delete(pt);
    }

    // === assignRoundToPhysicalTable ===

    @Test
    void shouldAllowAssignRoundToPhysicalTableWhileReady() {
        // Cascade-auto-created medal rounds become READY without a physical
        // table — admins must be able to assign one before starting.
        var pt = new PhysicalTable(divisionId, "Table 1");
        var round = new JudgingRound(judging.getId(), "Medal", UUID.randomUUID(), null);
        round.convertToMedalRound(MedalRoundMode.COMPARATIVE);
        round.markReady();
        given(judgingRoundRepository.findById(round.getId())).willReturn(Optional.of(round));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(physicalTableRepository.findById(pt.getId())).willReturn(Optional.of(pt));

        service.assignRoundToPhysicalTable(round.getId(), pt.getId(), adminUserId);

        assertThat(round.getPhysicalTableId()).isEqualTo(pt.getId());
        then(judgingRoundRepository).should().save(round);
    }

    @Test
    void shouldRejectAssignRoundToPhysicalTableAfterRoundStarted() {
        var pt = new PhysicalTable(divisionId, "Table 1");
        var round = new JudgingRound(judging.getId(), "R1", UUID.randomUUID(), null);
        round.start(); // now ACTIVE
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
    void shouldRejectStartRoundWhenDivisionNotYetInJudging() {
        // Admins can set up rounds at REGISTRATION_CLOSED, but they can't actually
        // *start* one until the division has been advanced to JUDGING.
        var regClosedDivision = new Division(UUID.randomUUID(), "Amateur", "amateur",
                ScoringSystem.MJP,
                LocalDateTime.of(2026, 6, 1, 23, 59),
                "Europe/Lisbon");
        regClosedDivision.advanceStatus(); // DRAFT → REGISTRATION_OPEN
        regClosedDivision.advanceStatus(); // REGISTRATION_OPEN → REGISTRATION_CLOSED
        var round = new JudgingRound(judging.getId(), UUID.randomUUID(), "R1",
                UUID.randomUUID(), null);
        round.assignJudge(UUID.randomUUID());
        round.assignJudge(UUID.randomUUID());
        round.assignEntry(UUID.randomUUID());
        given(judgingRoundRepository.findById(round.getId())).willReturn(Optional.of(round));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(regClosedDivision);

        assertThatThrownBy(() -> service.startRound(round.getId(), adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.round.cannot-start-before-judging");
        assertThat(round.getStatus()).isEqualTo(JudgingRoundStatus.PENDING);
    }

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
        assertThat(round.getStatus()).isEqualTo(JudgingRoundStatus.PENDING);
    }

    @Test
    void shouldRejectStartRoundWhenPhysicalTableIsBusyWithAnotherActiveRound() {
        var ptId = UUID.randomUUID();
        var otherRound = new JudgingRound(judging.getId(), ptId, "R-Other", UUID.randomUUID(), null);
        otherRound.start(); // already ACTIVE at the same physical table
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
    void shouldRejectStartRoundWhenSharedTablesAndAnotherDivisionHasActiveRoundAtSameLabel() {
        // Cross-division shared-tables: when competition.sharedTables=true,
        // two divisions can't run rounds at the same physical "Table 1" at
        // once. Matching is by physical-table label across the competition.
        competition.updateSharedTables(true);
        var otherDivisionId = UUID.randomUUID();
        var otherDivision = new Division(competition.getId(), "Commercial", "commercial",
                ScoringSystem.MJP, LocalDateTime.of(2026, 6, 1, 23, 59), "Europe/Lisbon");
        var otherJudgingId = UUID.randomUUID();
        var otherJudging = new Judging(otherDivisionId);
        var otherPtId = UUID.randomUUID();
        var otherPhysicalTable = new app.meads.judging.PhysicalTable(otherDivisionId, "Table 1");
        // Reflectively setting the id is overkill — we mock findById to return this object
        // and the lookup just compares label values.
        var otherActiveRound = new JudgingRound(otherJudging.getId(), otherPtId, "Other Active",
                UUID.randomUUID(), null);
        otherActiveRound.start();

        var thisPtId = UUID.randomUUID();
        var thisPhysicalTable = new app.meads.judging.PhysicalTable(divisionId, "Table 1");
        var thisRound = new JudgingRound(judging.getId(), thisPtId, "This Round",
                UUID.randomUUID(), null);
        thisRound.assignJudge(UUID.randomUUID());
        thisRound.assignJudge(UUID.randomUUID());
        thisRound.assignEntry(UUID.randomUUID());

        given(judgingRoundRepository.findById(thisRound.getId())).willReturn(Optional.of(thisRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(competitionService.findDivisionsByCompetition(competition.getId()))
                .willReturn(List.of(division, otherDivision));
        given(judgingRepository.findByDivisionId(otherDivision.getId()))
                .willReturn(Optional.of(otherJudging));
        given(judgingRoundRepository.findByJudgingId(otherJudging.getId()))
                .willReturn(List.of(otherActiveRound));
        // The same-division busy-check (per-ptId) finds nothing — only this round exists in this judging.
        given(judgingRoundRepository.findByJudgingId(judging.getId()))
                .willReturn(List.of(thisRound));
        given(physicalTableRepository.findById(thisPtId)).willReturn(Optional.of(thisPhysicalTable));
        given(physicalTableRepository.findById(otherPtId)).willReturn(Optional.of(otherPhysicalTable));

        assertThatThrownBy(() -> service.startRound(thisRound.getId(), adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.round.physical-table-busy-shared");
        assertThat(thisRound.getStatus()).isEqualTo(JudgingRoundStatus.PENDING);
    }

    @Test
    void shouldAllowStartRoundWhenSharedTablesFalseEvenIfOtherDivisionHasActiveRoundAtSameLabel() {
        // sharedTables=false (default for this test class) ⇒ each division's tables
        // are independent, so cross-division same-label is fine.
        // The default mock already returns competition with sharedTables=false.
        var thisPtId = UUID.randomUUID();
        var thisRound = new JudgingRound(judging.getId(), thisPtId, "This Round",
                UUID.randomUUID(), null);
        thisRound.assignJudge(UUID.randomUUID());
        thisRound.assignJudge(UUID.randomUUID());
        thisRound.assignEntry(UUID.randomUUID());
        given(judgingRoundRepository.findById(thisRound.getId())).willReturn(Optional.of(thisRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(judgingRoundRepository.findByJudgingId(judging.getId())).willReturn(List.of(thisRound));
        given(judgingRoundRepository.findAll()).willReturn(List.of(thisRound));
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(judgingRepository.save(any(Judging.class)))
                .willAnswer(inv -> inv.getArgument(0));

        assertThatNoException().isThrownBy(
                () -> service.startRound(thisRound.getId(), adminUserId));
        assertThat(thisRound.getStatus()).isEqualTo(JudgingRoundStatus.ACTIVE);
    }

    @Test
    void shouldRejectStartRoundWhenJudgeIsOnAnotherActiveRound() {
        var ptIdA = UUID.randomUUID();
        var ptIdB = UUID.randomUUID();
        var conflictingJudgeId = UUID.randomUUID();
        var otherActiveRound = new JudgingRound(judging.getId(), ptIdA, "R-Other", UUID.randomUUID(), null);
        otherActiveRound.assignJudge(conflictingJudgeId);
        otherActiveRound.start();
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
    void shouldRejectAssignJudgeToActiveRoundWhenJudgeIsOnAnActiveRoundInAnotherDivision() {
        // Cross-division regression: the conflict check uses findAll() so a
        // judge on an active round in Division A can't be assigned to an active
        // round in Division B of the same competition (or, indeed, of any
        // competition — the check is a hard rule about judge availability).
        var conflictingJudgeId = UUID.randomUUID();
        var divisionAJudgingId = UUID.randomUUID();
        var divisionBJudgingId = judging.getId();
        var roundInDivisionA = new JudgingRound(divisionAJudgingId, UUID.randomUUID(),
                "Division A Active", UUID.randomUUID(), null);
        roundInDivisionA.assignJudge(conflictingJudgeId);
        roundInDivisionA.start();
        var roundInDivisionB = new JudgingRound(divisionBJudgingId, UUID.randomUUID(),
                "Division B Active", UUID.randomUUID(), null);
        roundInDivisionB.start();
        given(judgingRoundRepository.findById(roundInDivisionB.getId())).willReturn(Optional.of(roundInDivisionB));
        given(judgingRepository.findById(divisionBJudgingId)).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(judgingRoundRepository.findAll()).willReturn(List.of(roundInDivisionA, roundInDivisionB));

        assertThatThrownBy(() -> service.assignJudge(roundInDivisionB.getId(), conflictingJudgeId, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.round.judge-active-conflict");
        assertThat(roundInDivisionB.getAssignments()).isEmpty();
    }

    @Test
    void shouldRejectAssignJudgeToActiveRoundWhenJudgeIsOnAnotherActiveRound() {
        var conflictingJudgeId = UUID.randomUUID();
        var existingActiveRound = new JudgingRound(judging.getId(), UUID.randomUUID(), "R-Existing",
                UUID.randomUUID(), null);
        existingActiveRound.assignJudge(conflictingJudgeId);
        existingActiveRound.start();
        var activeRound = new JudgingRound(judging.getId(), UUID.randomUUID(), "R-Active",
                UUID.randomUUID(), null);
        activeRound.start(); // already ACTIVE — assignJudge enforces conflict here
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
        existingActiveRound.start();
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
