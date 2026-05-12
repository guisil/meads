package app.meads.judging;

import app.meads.BusinessRuleException;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import app.meads.competition.DivisionStatus;
import app.meads.entry.Entry;
import app.meads.entry.EntryService;
import app.meads.judging.internal.BosPlacementRepository;
import app.meads.judging.internal.CategoryJudgingConfigRepository;
import app.meads.judging.internal.JudgingRepository;
import app.meads.judging.internal.JudgingServiceImpl;
import app.meads.judging.internal.JudgingTableRepository;
import app.meads.judging.internal.MedalAwardRepository;
import app.meads.judging.internal.ScoresheetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JudgingServiceFreezeGuardTest {

    @InjectMocks JudgingServiceImpl service;

    @Mock JudgingRepository judgingRepository;
    @Mock JudgingTableRepository judgingTableRepository;
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
    UUID judgeUserId;
    UUID divisionCategoryId;
    UUID tableId;
    UUID entryId;
    Division frozenDivision;
    Judging judging;
    JudgingTable table;

    @BeforeEach
    void setUp() {
        divisionId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();
        judgeUserId = UUID.randomUUID();
        divisionCategoryId = UUID.randomUUID();
        entryId = UUID.randomUUID();
        frozenDivision = mock(Division.class);
        given(frozenDivision.getStatus()).willReturn(DivisionStatus.RESULTS_PUBLISHED);
        given(competitionService.findDivisionById(divisionId)).willReturn(frozenDivision);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        judging = new Judging(divisionId);
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        table = new JudgingTable(judging.getId(), "T1", divisionCategoryId, null);
        tableId = table.getId();
        given(judgingTableRepository.findById(tableId)).willReturn(Optional.of(table));
        var cat = mock(DivisionCategory.class);
        given(cat.getDivisionId()).willReturn(divisionId);
        given(competitionService.findDivisionCategoryById(divisionCategoryId)).willReturn(cat);
    }

    private void assertFrozen(ThrowingRunnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.judging.results-published-frozen");
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run();
    }

    @Test
    void shouldRejectCreateTableWhenResultsPublished() {
        assertFrozen(() -> service.createTable(judging.getId(), "Table 1",
                divisionCategoryId, LocalDate.of(2026, 7, 1), adminUserId));
    }

    @Test
    void shouldRejectUpdateTableNameWhenResultsPublished() {
        assertFrozen(() -> service.updateTableName(tableId, "New", adminUserId));
    }

    @Test
    void shouldRejectUpdateTableScheduledDateWhenResultsPublished() {
        assertFrozen(() -> service.updateTableScheduledDate(tableId, LocalDate.now(), adminUserId));
    }

    @Test
    void shouldRejectDeleteTableWhenResultsPublished() {
        assertFrozen(() -> service.deleteTable(tableId, adminUserId));
    }

    @Test
    void shouldRejectAssignJudgeWhenResultsPublished() {
        assertFrozen(() -> service.assignJudge(tableId, judgeUserId, adminUserId));
    }

    @Test
    void shouldRejectRemoveJudgeWhenResultsPublished() {
        assertFrozen(() -> service.removeJudge(tableId, judgeUserId, adminUserId));
    }

    @Test
    void shouldRejectStartTableWhenResultsPublished() {
        assertFrozen(() -> service.startTable(tableId, adminUserId));
    }

    @Test
    void shouldRejectConfigureCategoryMedalRoundWhenResultsPublished() {
        assertFrozen(() -> service.configureCategoryMedalRound(
                divisionCategoryId, MedalRoundMode.COMPARATIVE, adminUserId));
    }

    @Test
    void shouldRejectStartMedalRoundWhenResultsPublished() {
        var config = mock(CategoryJudgingConfig.class);
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.of(config));
        assertFrozen(() -> service.startMedalRound(divisionCategoryId, adminUserId));
    }

    @Test
    void shouldRejectCompleteMedalRoundWhenResultsPublished() {
        var config = mock(CategoryJudgingConfig.class);
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.of(config));
        assertFrozen(() -> service.completeMedalRound(divisionCategoryId, adminUserId));
    }

    @Test
    void shouldRejectReopenMedalRoundWhenResultsPublished() {
        var config = mock(CategoryJudgingConfig.class);
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.of(config));
        assertFrozen(() -> service.reopenMedalRound(divisionCategoryId, adminUserId));
    }

    @Test
    void shouldRejectResetMedalRoundWhenResultsPublished() {
        var config = mock(CategoryJudgingConfig.class);
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.of(config));
        assertFrozen(() -> service.resetMedalRound(divisionCategoryId, adminUserId));
    }

    @Test
    void shouldRejectRecordMedalWhenResultsPublished() {
        var entry = mock(Entry.class);
        given(entry.getDivisionId()).willReturn(divisionId);
        given(entry.getFinalCategoryId()).willReturn(divisionCategoryId);
        given(entryService.findEntryById(entryId)).willReturn(entry);
        assertFrozen(() -> service.recordMedal(entryId, Medal.GOLD, judgeUserId));
    }

    @Test
    void shouldRejectUpdateMedalWhenResultsPublished() {
        var awardId = UUID.randomUUID();
        var award = mock(MedalAward.class);
        given(award.getDivisionId()).willReturn(divisionId);
        given(award.getFinalCategoryId()).willReturn(divisionCategoryId);
        given(medalAwardRepository.findById(awardId)).willReturn(Optional.of(award));
        assertFrozen(() -> service.updateMedal(awardId, Medal.SILVER, judgeUserId));
    }

    @Test
    void shouldRejectDeleteMedalAwardWhenResultsPublished() {
        var awardId = UUID.randomUUID();
        var award = mock(MedalAward.class);
        given(award.getDivisionId()).willReturn(divisionId);
        given(award.getFinalCategoryId()).willReturn(divisionCategoryId);
        given(medalAwardRepository.findById(awardId)).willReturn(Optional.of(award));
        assertFrozen(() -> service.deleteMedalAward(awardId, judgeUserId));
    }

    @Test
    void shouldRejectStartBosWhenResultsPublished() {
        assertFrozen(() -> service.startBos(divisionId, adminUserId));
    }

    @Test
    void shouldRejectCompleteBosWhenResultsPublished() {
        assertFrozen(() -> service.completeBos(divisionId, adminUserId));
    }

    @Test
    void shouldRejectReopenBosWhenResultsPublished() {
        assertFrozen(() -> service.reopenBos(divisionId, adminUserId));
    }

    @Test
    void shouldRejectResetBosWhenResultsPublished() {
        assertFrozen(() -> service.resetBos(divisionId, adminUserId));
    }

    @Test
    void shouldRejectRecordBosPlacementWhenResultsPublished() {
        assertFrozen(() -> service.recordBosPlacement(divisionId, entryId, 1, adminUserId));
    }

    @Test
    void shouldRejectUpdateBosPlacementWhenResultsPublished() {
        var placementId = UUID.randomUUID();
        var placement = mock(BosPlacement.class);
        given(placement.getDivisionId()).willReturn(divisionId);
        given(bosPlacementRepository.findById(placementId)).willReturn(Optional.of(placement));
        assertFrozen(() -> service.updateBosPlacement(placementId, 1, adminUserId));
    }

    @Test
    void shouldRejectDeleteBosPlacementWhenResultsPublished() {
        var placementId = UUID.randomUUID();
        var placement = mock(BosPlacement.class);
        given(placement.getDivisionId()).willReturn(divisionId);
        given(bosPlacementRepository.findById(placementId)).willReturn(Optional.of(placement));
        assertFrozen(() -> service.deleteBosPlacement(placementId, adminUserId));
    }
}
