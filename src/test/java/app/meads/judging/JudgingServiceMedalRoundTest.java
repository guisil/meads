package app.meads.judging;

import app.meads.BusinessRuleException;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import app.meads.competition.ScoringSystem;
import app.meads.entry.Entry;
import app.meads.entry.EntryService;
import app.meads.entry.EntryStatus;
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
class JudgingServiceMedalRoundTest {

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
    Division division;
    DivisionCategory category;
    Judging judging;

    @BeforeEach
    void setUp() {
        divisionId = UUID.randomUUID();
        divisionCategoryId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();
        division = new Division(UUID.randomUUID(), "Amateur", "amateur",
                ScoringSystem.MJP,
                LocalDateTime.of(2026, 6, 1, 23, 59),
                "Europe/Lisbon");
        category = new DivisionCategory(divisionId, null, "M1A", "Dry Trad",
                "Description", null, 0);
        judging = new Judging(divisionId);
        lenient().when(competitionService.findDivisionById(any())).thenReturn(division);
    }

    @Test
    void shouldStartTableWhenSufficientJudges() {
        var physicalTableId = UUID.randomUUID();
        var table = new JudgingRound(judging.getId(), physicalTableId, "T1", divisionCategoryId, null);
        table.assignJudge(UUID.randomUUID());
        table.assignJudge(UUID.randomUUID());
        given(judgingRoundRepository.findByJudgingId(judging.getId())).willReturn(java.util.List.of(table));
        given(judgingRoundRepository.findAll()).willReturn(java.util.List.of(table));
        given(judgingRoundRepository.findById(table.getId())).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.empty());
        given(categoryConfigRepository.save(any(CategoryJudgingConfig.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(judgingRepository.save(any(Judging.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.startRound(table.getId(), adminUserId);

        assertThat(table.getStatus()).isEqualTo(JudgingRoundStatus.ACTIVE);
        assertThat(judging.getPhase()).isEqualTo(JudgingPhase.ACTIVE);
        then(scoresheetService).should().createScoresheetsForTable(table.getId());
    }

    @Test
    void shouldRejectStartTableWhenInsufficientJudges() {
        var physicalTableId = UUID.randomUUID();
        var table = new JudgingRound(judging.getId(), physicalTableId, "T1", divisionCategoryId, null);
        table.assignJudge(UUID.randomUUID()); // only 1 judge, min is 2
        given(judgingRoundRepository.findByJudgingId(judging.getId())).willReturn(java.util.List.of(table));
        given(judgingRoundRepository.findById(table.getId())).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);

        assertThatThrownBy(() -> service.startRound(table.getId(), adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.judging-table.too-few-judges");

        assertThat(table.getStatus()).isEqualTo(JudgingRoundStatus.PENDING);
        then(scoresheetService).should(never()).createScoresheetsForTable(any());
    }

    @Test
    void shouldConfigureCategoryMedalRoundCreatingNew() {
        given(competitionService.findDivisionCategoryById(divisionCategoryId)).willReturn(category);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.empty());
        given(categoryConfigRepository.save(any(CategoryJudgingConfig.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var config = service.configureCategoryMedalRound(divisionCategoryId,
                MedalRoundMode.SCORE_BASED, adminUserId);

        assertThat(config.getMedalRoundMode()).isEqualTo(MedalRoundMode.SCORE_BASED);
    }

    @Test
    void shouldConfigureCategoryMedalRoundUpdatingExisting() {
        var existing = new CategoryJudgingConfig(divisionCategoryId, MedalRoundMode.COMPARATIVE);
        given(competitionService.findDivisionCategoryById(divisionCategoryId)).willReturn(category);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.of(existing));
        given(categoryConfigRepository.save(any(CategoryJudgingConfig.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var config = service.configureCategoryMedalRound(divisionCategoryId,
                MedalRoundMode.SCORE_BASED, adminUserId);

        assertThat(config.getMedalRoundMode()).isEqualTo(MedalRoundMode.SCORE_BASED);
    }

    @Test
    void shouldStartMedalRoundFromReady() {
        var config = new CategoryJudgingConfig(divisionCategoryId, MedalRoundMode.COMPARATIVE);
        config.markReady();
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.of(config));
        given(competitionService.findDivisionCategoryById(divisionCategoryId)).willReturn(category);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(categoryConfigRepository.save(any(CategoryJudgingConfig.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.startMedalRound(divisionCategoryId, adminUserId);

        assertThat(config.getMedalRoundStatus()).isEqualTo(MedalRoundStatus.ACTIVE);
    }

    @Test
    void shouldCompleteMedalRoundFromActive() {
        var config = new CategoryJudgingConfig(divisionCategoryId, MedalRoundMode.COMPARATIVE);
        config.markReady();
        config.startMedalRound();
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.of(config));
        given(competitionService.findDivisionCategoryById(divisionCategoryId)).willReturn(category);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(categoryConfigRepository.save(any(CategoryJudgingConfig.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.completeMedalRound(divisionCategoryId, adminUserId);

        assertThat(config.getMedalRoundStatus()).isEqualTo(MedalRoundStatus.COMPLETE);
    }

    @Test
    void shouldResetMedalRoundAndDeleteAwards() {
        var config = new CategoryJudgingConfig(divisionCategoryId, MedalRoundMode.COMPARATIVE);
        config.markReady();
        config.startMedalRound();
        judging.markActive();
        var award1 = new MedalAward(UUID.randomUUID(), divisionId, divisionCategoryId,
                Medal.GOLD, adminUserId);
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.of(config));
        given(competitionService.findDivisionCategoryById(divisionCategoryId)).willReturn(category);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(medalAwardRepository.findByFinalCategoryId(divisionCategoryId))
                .willReturn(List.of(award1));
        given(categoryConfigRepository.save(any(CategoryJudgingConfig.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.resetMedalRound(divisionCategoryId, adminUserId);

        assertThat(config.getMedalRoundStatus()).isEqualTo(MedalRoundStatus.READY);
        then(medalAwardRepository).should().deleteAll(List.of(award1));
    }

    @Test
    void shouldFindMedalAwardsForCategory() {
        var awards = List.of(
                new MedalAward(UUID.randomUUID(), divisionId, divisionCategoryId, Medal.GOLD, adminUserId),
                new MedalAward(UUID.randomUUID(), divisionId, divisionCategoryId, Medal.SILVER, adminUserId));
        given(medalAwardRepository.findByFinalCategoryId(divisionCategoryId)).willReturn(awards);

        var result = service.findMedalAwardsForCategory(divisionCategoryId);

        assertThat(result).isEqualTo(awards);
    }

    @Test
    void shouldFindCategoryConfigsForDivisionAndLazyCreateMissing() {
        var existingCat = new DivisionCategory(divisionId, null, "M1A", "Dry Trad",
                "Desc", null, 0, app.meads.competition.CategoryScope.JUDGING);
        var newCat = new DivisionCategory(divisionId, null, "M1B", "Medium Trad",
                "Desc", null, 1, app.meads.competition.CategoryScope.JUDGING);
        var existingConfig = new CategoryJudgingConfig(existingCat.getId(), MedalRoundMode.SCORE_BASED);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findJudgingCategories(divisionId))
                .willReturn(List.of(existingCat, newCat));
        given(categoryConfigRepository.findByDivisionCategoryId(existingCat.getId()))
                .willReturn(Optional.of(existingConfig));
        given(categoryConfigRepository.findByDivisionCategoryId(newCat.getId()))
                .willReturn(Optional.empty());
        given(categoryConfigRepository.save(any(CategoryJudgingConfig.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = service.findCategoryConfigsForDivision(divisionId, adminUserId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CategoryJudgingConfig::getDivisionCategoryId)
                .containsExactlyInAnyOrder(existingCat.getId(), newCat.getId());
        // The missing one should have been saved lazily with default COMPARATIVE
        then(categoryConfigRepository).should().save(any(CategoryJudgingConfig.class));
    }

    @Test
    void shouldFindJudgeUserIdsForTable() {
        var table = new JudgingRound(judging.getId(), "T1", divisionCategoryId, null);
        var judge1 = UUID.randomUUID();
        var judge2 = UUID.randomUUID();
        table.assignJudge(judge1);
        table.assignJudge(judge2);
        given(judgingRoundRepository.findById(table.getId())).willReturn(Optional.of(table));

        assertThat(service.findJudgeUserIdsForRound(table.getId()))
                .containsExactlyInAnyOrder(judge1, judge2);
    }

    @Test
    void shouldFindMedalRoundEntriesForComparativeModeFilteringToAdvancedEntries() {
        var advancedEntryId = UUID.randomUUID();
        var notAdvancedEntryId = UUID.randomUUID();
        var entrantUserId = UUID.randomUUID();

        var advancedEntry = mock(Entry.class);
        given(advancedEntry.getId()).willReturn(advancedEntryId);
        given(advancedEntry.getEntryCode()).willReturn("AMA-1");
        given(advancedEntry.getMeadName()).willReturn("Wildflower");
        given(advancedEntry.getUserId()).willReturn(entrantUserId);
        given(advancedEntry.getStatus()).willReturn(EntryStatus.RECEIVED);

        var notAdvancedEntry = mock(Entry.class);
        given(notAdvancedEntry.getId()).willReturn(notAdvancedEntryId);
        given(notAdvancedEntry.getStatus()).willReturn(EntryStatus.RECEIVED);

        given(entryService.findEntriesByFinalCategoryId(divisionCategoryId))
                .willReturn(List.of(advancedEntry, notAdvancedEntry));

        var advancedSheet = mock(Scoresheet.class);
        given(advancedSheet.getStatus()).willReturn(ScoresheetStatus.SUBMITTED);
        given(advancedSheet.isAdvancedToMedalRound()).willReturn(true);
        given(advancedSheet.getTotalScore()).willReturn(85);

        var notAdvancedSheet = mock(Scoresheet.class);
        given(notAdvancedSheet.getStatus()).willReturn(ScoresheetStatus.SUBMITTED);
        given(notAdvancedSheet.isAdvancedToMedalRound()).willReturn(false);

        given(scoresheetRepository.findByEntryId(advancedEntryId))
                .willReturn(Optional.of(advancedSheet));
        given(scoresheetRepository.findByEntryId(notAdvancedEntryId))
                .willReturn(Optional.of(notAdvancedSheet));
        given(medalAwardRepository.findByEntryId(advancedEntryId)).willReturn(Optional.empty());

        var rows = service.findMedalRoundEntries(divisionCategoryId, MedalRoundMode.COMPARATIVE);

        assertThat(rows).hasSize(1);
        var row = rows.get(0);
        assertThat(row.entryId()).isEqualTo(advancedEntryId);
        assertThat(row.entryCode()).isEqualTo("AMA-1");
        assertThat(row.meadName()).isEqualTo("Wildflower");
        assertThat(row.entrantUserId()).isEqualTo(entrantUserId);
        assertThat(row.round1Total()).isEqualTo(85);
        assertThat(row.advancedToMedalRound()).isTrue();
        assertThat(row.medalAwardId()).isNull();
        assertThat(row.currentMedal()).isNull();
    }

    @Test
    void shouldFindMedalRoundEntriesForScoreBasedModeIncludingNonAdvancedRankedByTotal() {
        var low = mockEntryWithScoresheet("AMA-3", 70, false);
        var high = mockEntryWithScoresheet("AMA-1", 92, true);
        var mid = mockEntryWithScoresheet("AMA-2", 80, false);
        given(entryService.findEntriesByFinalCategoryId(divisionCategoryId))
                .willReturn(List.of(low, high, mid));

        var rows = service.findMedalRoundEntries(divisionCategoryId, MedalRoundMode.SCORE_BASED);

        assertThat(rows).extracting(MedalRoundEntryRow::round1Total)
                .containsExactly(92, 80, 70);
    }

    @Test
    void shouldDetectTiedTopScoresInScoreBasedPreview() {
        var tieA = mockEntryWithScoresheet("AMA-1", 90, false);
        var tieB = mockEntryWithScoresheet("AMA-2", 90, false);
        var lower = mockEntryWithScoresheet("AMA-3", 80, false);
        given(entryService.findEntriesByFinalCategoryId(divisionCategoryId))
                .willReturn(List.of(tieA, tieB, lower));

        var preview = service.recomputeScorePreview(divisionCategoryId);

        assertThat(preview.tiedSlotCount()).isGreaterThan(0);
        assertThat(preview.tiedEntryIds())
                .containsExactlyInAnyOrder(tieA.getId(), tieB.getId());
    }

    @Test
    void shouldReportNoTiesWhenScoreBasedTopScoresAreDistinct() {
        var first = mockEntryWithScoresheet("AMA-1", 95, false);
        var second = mockEntryWithScoresheet("AMA-2", 88, false);
        var third = mockEntryWithScoresheet("AMA-3", 80, false);
        given(entryService.findEntriesByFinalCategoryId(divisionCategoryId))
                .willReturn(List.of(first, second, third));

        var preview = service.recomputeScorePreview(divisionCategoryId);

        assertThat(preview.tiedSlotCount()).isZero();
        assertThat(preview.tiedEntryIds()).isEmpty();
    }

    private Entry mockEntryWithScoresheet(String entryCode, int total, boolean advanced) {
        var entryId = UUID.randomUUID();
        var entry = mock(Entry.class);
        lenient().when(entry.getId()).thenReturn(entryId);
        lenient().when(entry.getEntryCode()).thenReturn(entryCode);
        lenient().when(entry.getMeadName()).thenReturn(entryCode + " mead");
        lenient().when(entry.getUserId()).thenReturn(UUID.randomUUID());
        lenient().when(entry.getStatus()).thenReturn(EntryStatus.RECEIVED);
        var sheet = mock(Scoresheet.class);
        lenient().when(sheet.getStatus()).thenReturn(ScoresheetStatus.SUBMITTED);
        lenient().when(sheet.isAdvancedToMedalRound()).thenReturn(advanced);
        lenient().when(sheet.getTotalScore()).thenReturn(total);
        lenient().when(scoresheetRepository.findByEntryId(entryId)).thenReturn(Optional.of(sheet));
        lenient().when(medalAwardRepository.findByEntryId(entryId)).thenReturn(Optional.empty());
        return entry;
    }

    @Test
    void shouldExcludeNonReceivedEntriesFromMedalRoundEntries() {
        var received = mockEntryWithScoresheet("AMA-1", 88, true);
        var withdrawn = mockEntryWithScoresheet("AMA-2", 90, true);
        lenient().when(withdrawn.getStatus()).thenReturn(EntryStatus.WITHDRAWN);
        given(entryService.findEntriesByFinalCategoryId(divisionCategoryId))
                .willReturn(List.of(received, withdrawn));

        var rows = service.findMedalRoundEntries(divisionCategoryId, MedalRoundMode.COMPARATIVE);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).entryId()).isEqualTo(received.getId());
    }

    @Test
    void shouldRejectReopenMedalRoundWhenJudgingNotActive() {
        var config = new CategoryJudgingConfig(divisionCategoryId, MedalRoundMode.COMPARATIVE);
        config.markReady();
        config.startMedalRound();
        config.completeMedalRound();
        judging.markActive();
        judging.startBos(); // phase = BOS, not ACTIVE
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.of(config));
        given(competitionService.findDivisionCategoryById(divisionCategoryId)).willReturn(category);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));

        assertThatThrownBy(() -> service.reopenMedalRound(divisionCategoryId, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.medal-round.judging-not-active");
    }
}
