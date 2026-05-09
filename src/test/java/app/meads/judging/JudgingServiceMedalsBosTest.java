package app.meads.judging;

import app.meads.BusinessRuleException;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import app.meads.competition.ScoringSystem;
import app.meads.entry.Entry;
import app.meads.entry.EntryService;
import app.meads.judging.CoiCheckService.CoiResult;
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
import org.springframework.context.ApplicationEventPublisher;

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
class JudgingServiceMedalsBosTest {

    @InjectMocks
    JudgingServiceImpl service;

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
    UUID divisionCategoryId;
    UUID adminUserId;
    UUID judgeUserId;
    UUID entryId;
    Division division;
    DivisionCategory category;
    Judging judging;

    @BeforeEach
    void setUp() {
        divisionId = UUID.randomUUID();
        divisionCategoryId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();
        judgeUserId = UUID.randomUUID();
        entryId = UUID.randomUUID();
        division = new Division(UUID.randomUUID(), "Amateur", "amateur",
                ScoringSystem.MJP,
                LocalDateTime.of(2026, 6, 1, 23, 59),
                "Europe/Lisbon");
        division.updateBosPlaces(3);
        category = new DivisionCategory(divisionId, null, "M1A", "Dry Trad",
                "Description", null, 0);
        judging = new Judging(divisionId);
    }

    private Entry mockEntry() {
        var entry = mock(Entry.class);
        lenient().when(entry.getId()).thenReturn(entryId);
        lenient().when(entry.getDivisionId()).thenReturn(divisionId);
        lenient().when(entry.getFinalCategoryId()).thenReturn(divisionCategoryId);
        return entry;
    }

    private CategoryJudgingConfig activeConfig() {
        var config = new CategoryJudgingConfig(divisionCategoryId, MedalRoundMode.COMPARATIVE);
        config.markReady();
        config.startMedalRound();
        return config;
    }

    // === Medals ===

    @Test
    void shouldRecordMedalWhenAdminAndMedalRoundActive() {
        var entry = mockEntry();
        given(entryService.findEntryById(entryId)).willReturn(entry);
        given(coiCheckService.check(adminUserId, entryId)).willReturn(CoiResult.clear());
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.of(activeConfig()));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(medalAwardRepository.findByEntryId(entryId)).willReturn(Optional.empty());
        given(medalAwardRepository.save(any(MedalAward.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var award = service.recordMedal(entryId, Medal.GOLD, adminUserId);

        assertThat(award.getMedal()).isEqualTo(Medal.GOLD);
        assertThat(award.getEntryId()).isEqualTo(entryId);
        assertThat(award.getDivisionId()).isEqualTo(divisionId);
    }

    @Test
    void shouldRejectRecordMedalWhenCoiHardBlock() {
        var entry = mockEntry();
        given(entryService.findEntryById(entryId)).willReturn(entry);
        given(coiCheckService.check(judgeUserId, entryId)).willReturn(CoiResult.blocking());

        assertThatThrownBy(() -> service.recordMedal(entryId, Medal.GOLD, judgeUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.coi.self-entry");

        then(medalAwardRepository).should(never()).save(any(MedalAward.class));
    }

    @Test
    void shouldRejectRecordMedalWhenMedalRoundNotActive() {
        var entry = mockEntry();
        var pendingConfig = new CategoryJudgingConfig(divisionCategoryId, MedalRoundMode.COMPARATIVE);
        given(entryService.findEntryById(entryId)).willReturn(entry);
        given(coiCheckService.check(adminUserId, entryId)).willReturn(CoiResult.clear());
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.of(pendingConfig));

        assertThatThrownBy(() -> service.recordMedal(entryId, Medal.GOLD, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.medal-round.not-active");
    }

    @Test
    void shouldUpdateExistingMedalOnRecordMedal() {
        var entry = mockEntry();
        var existing = new MedalAward(entryId, divisionId, divisionCategoryId,
                Medal.SILVER, adminUserId);
        given(entryService.findEntryById(entryId)).willReturn(entry);
        given(coiCheckService.check(adminUserId, entryId)).willReturn(CoiResult.clear());
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.of(activeConfig()));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(medalAwardRepository.findByEntryId(entryId)).willReturn(Optional.of(existing));
        given(medalAwardRepository.save(any(MedalAward.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var award = service.recordMedal(entryId, Medal.GOLD, adminUserId);

        assertThat(award.getMedal()).isEqualTo(Medal.GOLD);
    }

    @Test
    void shouldDeleteMedalAwardWhenAdmin() {
        var existing = new MedalAward(entryId, divisionId, divisionCategoryId,
                Medal.GOLD, adminUserId);
        given(medalAwardRepository.findById(existing.getId())).willReturn(Optional.of(existing));
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.of(activeConfig()));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);

        service.deleteMedalAward(existing.getId(), adminUserId);

        then(medalAwardRepository).should().delete(existing);
    }

    // === BOS lifecycle ===

    @Test
    void shouldStartBosWhenAllCategoriesComplete() {
        judging.markActive();
        var completeConfig = new CategoryJudgingConfig(category.getId(), MedalRoundMode.COMPARATIVE);
        completeConfig.markReady();
        completeConfig.startMedalRound();
        completeConfig.completeMedalRound();
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(competitionService.findDivisionCategories(divisionId))
                .willReturn(List.of(category));
        given(categoryConfigRepository.findByDivisionCategoryId(category.getId()))
                .willReturn(Optional.of(completeConfig));
        given(judgingRepository.save(any(Judging.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.startBos(divisionId, adminUserId);

        assertThat(judging.getPhase()).isEqualTo(JudgingPhase.BOS);
    }

    @Test
    void shouldRejectStartBosWhenAnyCategoryIncomplete() {
        judging.markActive();
        var pendingConfig = new CategoryJudgingConfig(category.getId(), MedalRoundMode.COMPARATIVE);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(competitionService.findDivisionCategories(divisionId))
                .willReturn(List.of(category));
        given(categoryConfigRepository.findByDivisionCategoryId(category.getId()))
                .willReturn(Optional.of(pendingConfig));

        assertThatThrownBy(() -> service.startBos(divisionId, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.bos.medal-rounds-incomplete");

        assertThat(judging.getPhase()).isEqualTo(JudgingPhase.ACTIVE);
    }

    @Test
    void shouldResetBosOnlyWhenNoPlacements() {
        judging.markActive();
        judging.startBos();
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(bosPlacementRepository.findByDivisionIdOrderByPlace(divisionId))
                .willReturn(List.of());
        given(judgingRepository.save(any(Judging.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.resetBos(divisionId, adminUserId);

        assertThat(judging.getPhase()).isEqualTo(JudgingPhase.ACTIVE);
    }

    @Test
    void shouldRejectResetBosWhenPlacementsExist() {
        judging.markActive();
        judging.startBos();
        var placement = new BosPlacement(divisionId, entryId, 1, adminUserId);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(bosPlacementRepository.findByDivisionIdOrderByPlace(divisionId))
                .willReturn(List.of(placement));

        assertThatThrownBy(() -> service.resetBos(divisionId, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.bos.placements-exist");
    }

    // === BOS placements ===

    @Test
    void shouldRecordBosPlacementWhenEntryHasGoldAndPhaseBos() {
        judging.markActive();
        judging.startBos();
        var goldAward = new MedalAward(entryId, divisionId, divisionCategoryId,
                Medal.GOLD, adminUserId);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(medalAwardRepository.findByEntryId(entryId)).willReturn(Optional.of(goldAward));
        given(bosPlacementRepository.findByEntryId(entryId)).willReturn(Optional.empty());
        given(bosPlacementRepository.save(any(BosPlacement.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var placement = service.recordBosPlacement(divisionId, entryId, 1, adminUserId);

        assertThat(placement.getPlace()).isEqualTo(1);
        assertThat(placement.getEntryId()).isEqualTo(entryId);
    }

    @Test
    void shouldRejectRecordBosPlacementWhenEntryNotGold() {
        judging.markActive();
        judging.startBos();
        var silverAward = new MedalAward(entryId, divisionId, divisionCategoryId,
                Medal.SILVER, adminUserId);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(medalAwardRepository.findByEntryId(entryId)).willReturn(Optional.of(silverAward));

        assertThatThrownBy(() -> service.recordBosPlacement(divisionId, entryId, 1, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.bos.entry-not-gold");
    }

    @Test
    void shouldRejectRecordBosPlacementWhenPlaceOutOfRange() {
        judging.markActive();
        judging.startBos();
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(competitionService.findDivisionById(divisionId)).willReturn(division);

        assertThatThrownBy(() -> service.recordBosPlacement(divisionId, entryId, 4, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.bos.invalid-place");
    }

    @Test
    void shouldRejectRecordBosPlacementWhenPhaseNotBos() {
        judging.markActive(); // ACTIVE, not BOS
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));

        assertThatThrownBy(() -> service.recordBosPlacement(divisionId, entryId, 1, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.bos.not-active");
    }

    @Test
    void shouldDeleteBosPlacement() {
        var placement = new BosPlacement(divisionId, entryId, 2, adminUserId);
        given(bosPlacementRepository.findById(placement.getId())).willReturn(Optional.of(placement));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);

        service.deleteBosPlacement(placement.getId(), adminUserId);

        then(bosPlacementRepository).should().delete(placement);
    }
}
