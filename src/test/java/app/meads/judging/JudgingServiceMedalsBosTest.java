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
    @Mock JudgingRoundRepository judgingRoundRepository;
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
        lenient().when(competitionService.findDivisionById(any())).thenReturn(division);
    }

    private Entry mockEntry() {
        var entry = mock(Entry.class);
        lenient().when(entry.getId()).thenReturn(entryId);
        lenient().when(entry.getDivisionId()).thenReturn(divisionId);
        lenient().when(entry.getFinalCategoryId()).thenReturn(divisionCategoryId);
        return entry;
    }

    /** Stubs an ACTIVE medal JudgingRound for the test's divisionCategoryId. */
    private void givenActiveMedalRoundForCategory() {
        var medalRound = new JudgingRound(judging.getId(), "Medal", divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.COMPARATIVE);
        medalRound.markReady();
        medalRound.start();
        given(judgingRoundRepository
                .findFirstByDivisionCategoryIdAndType(divisionCategoryId, RoundType.MEDAL))
                .willReturn(Optional.of(medalRound));
    }

    /** Stubs an ACTIVE medal JudgingRound for an arbitrary category id. */
    private void givenActiveMedalRoundForCategory(UUID catId) {
        var medalRound = new JudgingRound(judging.getId(), "Medal", catId, null);
        medalRound.convertToMedalRound(MedalRoundMode.COMPARATIVE);
        medalRound.markReady();
        medalRound.start();
        given(judgingRoundRepository
                .findFirstByDivisionCategoryIdAndType(catId, RoundType.MEDAL))
                .willReturn(Optional.of(medalRound));
    }

    /** Stubs a COMPLETE medal JudgingRound for the given category id. */
    private void givenCompleteMedalRoundForCategory(UUID catId) {
        var medalRound = new JudgingRound(judging.getId(), "Medal", catId, null);
        medalRound.convertToMedalRound(MedalRoundMode.COMPARATIVE);
        medalRound.markReady();
        medalRound.start();
        medalRound.markComplete();
        given(judgingRoundRepository
                .findFirstByDivisionCategoryIdAndType(catId, RoundType.MEDAL))
                .willReturn(Optional.of(medalRound));
    }

    // === Medals ===

    @Test
    void shouldRecordMedalWhenAdminAndMedalRoundActive() {
        var entry = mockEntry();
        given(entryService.findEntryById(entryId)).willReturn(entry);
        given(coiCheckService.check(adminUserId, entryId)).willReturn(CoiResult.clear());
        givenActiveMedalRoundForCategory();
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
    void shouldWithholdMedalAsConfirmedNullAward() {
        // Clearing a medal on a SCORE_BASED round must persist a deliberate
        // "no medal" decision (confirmed award, null medal) so the score-driven
        // auto-populate (incl. the re-run at finalize) does not bring it back.
        var entry = mockEntry();
        given(entryService.findEntryById(entryId)).willReturn(entry);
        givenActiveMedalRoundForCategory();
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(medalAwardRepository.findByEntryId(entryId)).willReturn(Optional.empty());
        given(medalAwardRepository.save(any(MedalAward.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.withholdMedal(entryId, adminUserId);

        var captor = org.mockito.ArgumentCaptor.forClass(MedalAward.class);
        then(medalAwardRepository).should().save(captor.capture());
        assertThat(captor.getValue().getEntryId()).isEqualTo(entryId);
        assertThat(captor.getValue().getMedal()).isNull();
        assertThat(captor.getValue().isConfirmed()).isTrue();
    }

    @Test
    void shouldRejectRecordMedalWhenAnotherEntryInCategoryAlreadyHasSameType() {
        // At most one Gold / Silver / Bronze per category. Admin must clear or
        // reassign the existing award before stacking a duplicate medal of the
        // same type.
        var entry = mockEntry();
        var otherEntryId = UUID.randomUUID();
        var existingGold = new MedalAward(otherEntryId, divisionId, divisionCategoryId,
                Medal.GOLD, adminUserId);
        given(entryService.findEntryById(entryId)).willReturn(entry);
        given(coiCheckService.check(adminUserId, entryId)).willReturn(CoiResult.clear());
        givenActiveMedalRoundForCategory();
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(medalAwardRepository.findByFinalCategoryId(divisionCategoryId))
                .willReturn(List.of(existingGold));

        assertThatThrownBy(() -> service.recordMedal(entryId, Medal.GOLD, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.medal.duplicate-type");

        then(medalAwardRepository).should(never()).save(any(MedalAward.class));
    }

    @Test
    void shouldAllowRecordMedalWhenSameEntryAlreadyHasMedalOfDifferentType() {
        // Same-entry update: changing entry A from Silver to Gold is fine
        // even though A already has an award (just not the same type via
        // another entry).
        var entry = mockEntry();
        var existingSilverForSameEntry = new MedalAward(entryId, divisionId, divisionCategoryId,
                Medal.SILVER, adminUserId);
        given(entryService.findEntryById(entryId)).willReturn(entry);
        given(coiCheckService.check(adminUserId, entryId)).willReturn(CoiResult.clear());
        givenActiveMedalRoundForCategory();
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(medalAwardRepository.findByEntryId(entryId)).willReturn(Optional.of(existingSilverForSameEntry));
        given(medalAwardRepository.findByFinalCategoryId(divisionCategoryId))
                .willReturn(List.of(existingSilverForSameEntry));
        given(medalAwardRepository.save(any(MedalAward.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var award = service.recordMedal(entryId, Medal.GOLD, adminUserId);

        assertThat(award.getMedal()).isEqualTo(Medal.GOLD);
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
        var pendingMedalRound = new JudgingRound(judging.getId(), "Medal", divisionCategoryId, null);
        pendingMedalRound.convertToMedalRound(MedalRoundMode.COMPARATIVE);
        given(entryService.findEntryById(entryId)).willReturn(entry);
        given(coiCheckService.check(adminUserId, entryId)).willReturn(CoiResult.clear());
        given(judgingRoundRepository
                .findFirstByDivisionCategoryIdAndType(divisionCategoryId, RoundType.MEDAL))
                .willReturn(Optional.of(pendingMedalRound));

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
        givenActiveMedalRoundForCategory();
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(medalAwardRepository.findByEntryId(entryId)).willReturn(Optional.of(existing));
        given(medalAwardRepository.save(any(MedalAward.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var award = service.recordMedal(entryId, Medal.GOLD, adminUserId);

        assertThat(award.getMedal()).isEqualTo(Medal.GOLD);
    }

    @Test
    void shouldMarkUpdateMedalAsConfirmed() {
        var existing = new MedalAward(entryId, divisionId, divisionCategoryId,
                Medal.SILVER, adminUserId);
        given(medalAwardRepository.findById(existing.getId())).willReturn(Optional.of(existing));
        given(coiCheckService.check(adminUserId, entryId)).willReturn(CoiResult.clear());
        givenActiveMedalRoundForCategory();
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(medalAwardRepository.save(any(MedalAward.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.updateMedal(existing.getId(), Medal.GOLD, adminUserId);

        assertThat(existing.getMedal()).isEqualTo(Medal.GOLD);
        assertThat(existing.isConfirmed()).isTrue();
        assertThat(existing.getConfirmedBy()).isEqualTo(adminUserId);
    }

    @Test
    void shouldDeleteMedalAwardWhenAdmin() {
        var existing = new MedalAward(entryId, divisionId, divisionCategoryId,
                Medal.GOLD, adminUserId);
        given(medalAwardRepository.findById(existing.getId())).willReturn(Optional.of(existing));
        givenActiveMedalRoundForCategory();
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);

        service.deleteMedalAward(existing.getId(), adminUserId);

        then(medalAwardRepository).should().delete(existing);
    }

    // === BOS lifecycle ===

    @Test
    void shouldStartBosWhenAllCategoriesComplete() {
        judging.markActive();
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(competitionService.findDivisionCategories(divisionId))
                .willReturn(List.of(category));
        givenCompleteMedalRoundForCategory(category.getId());
        given(judgingRepository.save(any(Judging.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.startBos(divisionId, adminUserId);

        assertThat(judging.getPhase()).isEqualTo(JudgingPhase.BOS);
    }

    @Test
    void shouldRejectStartBosWhenAnyCategoryIncomplete() {
        judging.markActive();
        var pendingMedalRound = new JudgingRound(judging.getId(), "Medal", category.getId(), null);
        pendingMedalRound.convertToMedalRound(MedalRoundMode.COMPARATIVE);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(competitionService.findDivisionCategories(divisionId))
                .willReturn(List.of(category));
        given(judgingRoundRepository
                .findFirstByDivisionCategoryIdAndType(category.getId(), RoundType.MEDAL))
                .willReturn(Optional.of(pendingMedalRound));

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
    void shouldRejectCompleteBosWhenAPlaceCouldStillBeFilledByAnUnplacedGold() {
        judging.markActive();
        judging.startBos();
        // bosPlaces = 3 (setUp). One place filled, two empty, and a second
        // confirmed gold is still unplaced — finalize must be blocked.
        var placedGold = new MedalAward(entryId, divisionId, divisionCategoryId, Medal.GOLD, adminUserId);
        placedGold.confirm(adminUserId);
        var unplacedGold = new MedalAward(UUID.randomUUID(), divisionId, divisionCategoryId,
                Medal.GOLD, adminUserId);
        unplacedGold.confirm(adminUserId);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(bosPlacementRepository.findByDivisionIdOrderByPlace(divisionId))
                .willReturn(List.of(new BosPlacement(divisionId, entryId, 1, adminUserId)));
        given(medalAwardRepository.findByDivisionId(divisionId))
                .willReturn(List.of(placedGold, unplacedGold));

        assertThatThrownBy(() -> service.completeBos(divisionId, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.bos.cannot-complete-unfilled");
    }

    @Test
    void shouldCompleteBosWithEmptyPlacesWhenNoUnplacedGoldRemains() {
        judging.markActive();
        judging.startBos();
        // bosPlaces = 3, only one gold exists and it is placed — the two empty
        // places can't be filled, so finalize is allowed (short field).
        var placedGold = new MedalAward(entryId, divisionId, divisionCategoryId, Medal.GOLD, adminUserId);
        placedGold.confirm(adminUserId);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(bosPlacementRepository.findByDivisionIdOrderByPlace(divisionId))
                .willReturn(List.of(new BosPlacement(divisionId, entryId, 1, adminUserId)));
        given(medalAwardRepository.findByDivisionId(divisionId)).willReturn(List.of(placedGold));

        service.completeBos(divisionId, adminUserId);

        assertThat(judging.getPhase()).isEqualTo(JudgingPhase.COMPLETE);
    }

    @Test
    void shouldReportCannotFinalizeBosWhenGoldUnplacedAndPlaceEmpty() {
        var placedGold = new MedalAward(entryId, divisionId, divisionCategoryId, Medal.GOLD, adminUserId);
        placedGold.confirm(adminUserId);
        var unplacedGold = new MedalAward(UUID.randomUUID(), divisionId, divisionCategoryId,
                Medal.GOLD, adminUserId);
        unplacedGold.confirm(adminUserId);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(bosPlacementRepository.findByDivisionIdOrderByPlace(divisionId))
                .willReturn(List.of(new BosPlacement(divisionId, entryId, 1, adminUserId)));
        given(medalAwardRepository.findByDivisionId(divisionId))
                .willReturn(List.of(placedGold, unplacedGold));

        assertThat(service.canFinalizeBos(divisionId, adminUserId)).isFalse();
    }

    @Test
    void shouldRecordBosPlacementWhenEntryHasGoldAndPhaseBos() {
        judging.markActive();
        judging.startBos();
        var goldAward = new MedalAward(entryId, divisionId, divisionCategoryId,
                Medal.GOLD, adminUserId);
        goldAward.confirm(adminUserId);
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
    void shouldRejectRecordBosPlacementWhenGoldUnconfirmed() {
        judging.markActive();
        judging.startBos();
        var unconfirmedGold = new MedalAward(entryId, divisionId, divisionCategoryId,
                Medal.GOLD, adminUserId);
        // intentionally not confirmed
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(medalAwardRepository.findByEntryId(entryId)).willReturn(Optional.of(unconfirmedGold));

        assertThatThrownBy(() -> service.recordBosPlacement(divisionId, entryId, 1, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.bos.gold-not-confirmed");
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
    void shouldRejectRecordBosPlacementWhenTargetPlaceTakenByAnotherEntry() {
        judging.markActive();
        judging.startBos();
        var goldAward = new MedalAward(entryId, divisionId, divisionCategoryId,
                Medal.GOLD, adminUserId);
        goldAward.confirm(adminUserId);
        var occupant = new BosPlacement(divisionId, UUID.randomUUID(), 1, adminUserId);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(medalAwardRepository.findByEntryId(entryId)).willReturn(Optional.of(goldAward));
        given(bosPlacementRepository.findByDivisionIdAndPlace(divisionId, 1))
                .willReturn(Optional.of(occupant));

        assertThatThrownBy(() -> service.recordBosPlacement(divisionId, entryId, 1, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.bos.place-taken");
        then(bosPlacementRepository).should(never()).save(any(BosPlacement.class));
    }

    @Test
    void shouldRejectUpdateBosPlacementWhenTargetPlaceTakenByAnotherEntry() {
        // Moving a placement onto an already-occupied place must fail cleanly
        // (no swap, positions unchanged) instead of tripping the DB unique
        // constraint on (division_id, place).
        var moving = new BosPlacement(divisionId, UUID.randomUUID(), 3, adminUserId);
        var occupant = new BosPlacement(divisionId, UUID.randomUUID(), 1, adminUserId);
        given(bosPlacementRepository.findById(moving.getId())).willReturn(Optional.of(moving));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(bosPlacementRepository.findByDivisionIdAndPlace(divisionId, 1))
                .willReturn(Optional.of(occupant));

        assertThatThrownBy(() -> service.updateBosPlacement(moving.getId(), 1, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.bos.place-taken");
        then(bosPlacementRepository).should(never()).save(any(BosPlacement.class));
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

    @Test
    void shouldFindGoldMedalAwardsForDivisionFilteredFromAllMedals() {
        var goldId = UUID.randomUUID();
        var silverId = UUID.randomUUID();
        var bronzeId = UUID.randomUUID();
        var gold = new MedalAward(goldId, divisionId, divisionCategoryId,
                Medal.GOLD, adminUserId);
        gold.confirm(adminUserId);
        var silver = new MedalAward(silverId, divisionId, divisionCategoryId,
                Medal.SILVER, adminUserId);
        var bronze = new MedalAward(bronzeId, divisionId, divisionCategoryId,
                Medal.BRONZE, adminUserId);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(medalAwardRepository.findByDivisionId(divisionId))
                .willReturn(List.of(gold, silver, bronze));

        var golds = service.findGoldMedalAwardsForDivision(divisionId, adminUserId);

        assertThat(golds).extracting(MedalAward::getMedal).containsExactly(Medal.GOLD);
    }

    @Test
    void shouldExcludeUnconfirmedGoldAwardsForBosCandidates() {
        var confirmedGoldId = UUID.randomUUID();
        var unconfirmedGoldId = UUID.randomUUID();
        var confirmed = new MedalAward(confirmedGoldId, divisionId, divisionCategoryId,
                Medal.GOLD, adminUserId);
        confirmed.confirm(adminUserId);
        var unconfirmed = new MedalAward(unconfirmedGoldId, divisionId, divisionCategoryId,
                Medal.GOLD, adminUserId);
        // unconfirmed stays confirmed=false (auto-fill semantics)
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(medalAwardRepository.findByDivisionId(divisionId))
                .willReturn(List.of(confirmed, unconfirmed));

        var golds = service.findGoldMedalAwardsForDivision(divisionId, adminUserId);

        assertThat(golds).extracting(MedalAward::getEntryId).containsExactly(confirmedGoldId);
    }

    @Test
    void shouldRejectFindGoldMedalAwardsForDivisionWhenUnauthorized() {
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(false);

        assertThatThrownBy(() -> service.findGoldMedalAwardsForDivision(divisionId, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.auth.unauthorized");
    }

    @Test
    void shouldFindBosPlacementsForDivisionOrderedByPlace() {
        var p1 = new BosPlacement(divisionId, UUID.randomUUID(), 1, adminUserId);
        var p2 = new BosPlacement(divisionId, UUID.randomUUID(), 2, adminUserId);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(bosPlacementRepository.findByDivisionIdOrderByPlace(divisionId))
                .willReturn(List.of(p1, p2));

        var placements = service.findBosPlacementsForDivision(divisionId, adminUserId);

        assertThat(placements).containsExactly(p1, p2);
    }

    @Test
    void shouldRejectFindBosPlacementsForDivisionWhenUnauthorized() {
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(false);

        assertThatThrownBy(() -> service.findBosPlacementsForDivision(divisionId, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.auth.unauthorized");
    }

    @Test
    void shouldConfirmMedalAward() {
        var award = new MedalAward(entryId, divisionId, divisionCategoryId, Medal.GOLD, adminUserId);
        given(medalAwardRepository.findById(award.getId())).willReturn(Optional.of(award));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(medalAwardRepository.save(any(MedalAward.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.confirmMedalAward(award.getId(), adminUserId);

        assertThat(award.isConfirmed()).isTrue();
        assertThat(award.getConfirmedBy()).isEqualTo(adminUserId);
        assertThat(award.getConfirmedAt()).isNotNull();
    }

    @Test
    void shouldRecordMedalAsConfirmed() {
        var entry = mockEntry();
        given(entryService.findEntryById(entryId)).willReturn(entry);
        given(coiCheckService.check(adminUserId, entryId)).willReturn(CoiResult.clear());
        givenActiveMedalRoundForCategory();
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(medalAwardRepository.findByEntryId(entryId)).willReturn(Optional.empty());
        given(medalAwardRepository.save(any(MedalAward.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var award = service.recordMedal(entryId, Medal.GOLD, adminUserId);

        assertThat(award.isConfirmed()).isTrue();
        assertThat(award.getConfirmedBy()).isEqualTo(adminUserId);
    }
}
