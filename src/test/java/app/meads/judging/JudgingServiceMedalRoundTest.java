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
        table.assignEntry(UUID.randomUUID());
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
    void shouldRejectStartScoringRoundWhenNoEntriesAssigned() {
        // Scoring rounds must have an explicit entry assignment before they
        // can be started. The earlier auto-populate-from-category fallback
        // was a back-compat shim during the JudgingTable → JudgingRound
        // redesign; with the Assign Entries dialog live, admins are expected
        // to set this explicitly.
        var physicalTableId = UUID.randomUUID();
        var table = new JudgingRound(judging.getId(), physicalTableId, "T1", divisionCategoryId, null);
        table.assignJudge(UUID.randomUUID());
        table.assignJudge(UUID.randomUUID());
        given(judgingRoundRepository.findByJudgingId(judging.getId())).willReturn(List.of(table));
        given(judgingRoundRepository.findAll()).willReturn(List.of(table));
        given(judgingRoundRepository.findById(table.getId())).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);

        assertThatThrownBy(() -> service.startRound(table.getId(), adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.round.no-entries-assigned");

        assertThat(table.getStatus()).isEqualTo(JudgingRoundStatus.PENDING);
        then(scoresheetService).should(never()).createScoresheetsForTable(any());
    }

    @Test
    void shouldStartMedalTypedRoundWithoutCreatingScoresheetsOrEnforcingMinJudges() {
        var physicalTableId = UUID.randomUUID();
        var medalRound = new JudgingRound(judging.getId(), physicalTableId, "Medal",
                divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.COMPARATIVE);
        medalRound.assignJudge(UUID.randomUUID()); // only 1 judge — would fail for SCORING
        given(judgingRoundRepository.findByJudgingId(judging.getId())).willReturn(List.of(medalRound));
        given(judgingRoundRepository.findAll()).willReturn(List.of(medalRound));
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(judgingRepository.save(any(Judging.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.startRound(medalRound.getId(), adminUserId);

        assertThat(medalRound.getStatus()).isEqualTo(JudgingRoundStatus.ACTIVE);
        then(scoresheetService).should(never()).createScoresheetsForTable(any());
        then(entryService).should(never()).findEntriesByFinalCategoryId(any());
    }

    @Test
    void shouldNotOverwriteExplicitlyAssignedRoundEntriesWhenStartingRound() {
        var physicalTableId = UUID.randomUUID();
        var table = new JudgingRound(judging.getId(), physicalTableId, "T1", divisionCategoryId, null);
        table.assignJudge(UUID.randomUUID());
        table.assignJudge(UUID.randomUUID());
        var preAssigned = UUID.randomUUID();
        table.assignEntry(preAssigned);
        given(judgingRoundRepository.findByJudgingId(judging.getId())).willReturn(List.of(table));
        given(judgingRoundRepository.findAll()).willReturn(List.of(table));
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

        assertThat(table.getEntries()).containsExactly(preAssigned);
        then(entryService).should(never()).findEntriesByFinalCategoryId(any());
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
    void shouldCreateMedalRoundWithModeFromCategoryConfig() {
        var config = new CategoryJudgingConfig(divisionCategoryId, MedalRoundMode.SCORE_BASED);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionCategoryById(divisionCategoryId)).willReturn(category);
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(judgingRoundRepository.findByJudgingId(judging.getId())).willReturn(List.of());
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.of(config));
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var round = service.createMedalRound(judging.getId(), divisionCategoryId, adminUserId);

        assertThat(round.getType()).isEqualTo(RoundType.MEDAL);
        assertThat(round.getMedalMode()).isEqualTo(MedalRoundMode.SCORE_BASED);
        assertThat(round.getDivisionCategoryId()).isEqualTo(divisionCategoryId);
        assertThat(round.getJudgingId()).isEqualTo(judging.getId());
        assertThat(round.getStatus()).isEqualTo(JudgingRoundStatus.PENDING);
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
    void shouldUpdateMedalRoundModeOnPendingRound() {
        var medalRound = new JudgingRound(judging.getId(), "Medal", divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.COMPARATIVE);
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);

        service.updateMedalRoundMode(medalRound.getId(), MedalRoundMode.SCORE_BASED, adminUserId);

        assertThat(medalRound.getMedalMode()).isEqualTo(MedalRoundMode.SCORE_BASED);
        then(judgingRoundRepository).should().save(medalRound);
    }

    @Test
    void shouldUpdateMedalRoundModeOnReadyRound() {
        var medalRound = new JudgingRound(judging.getId(), "Medal", divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.COMPARATIVE);
        medalRound.markReady();
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);

        service.updateMedalRoundMode(medalRound.getId(), MedalRoundMode.SCORE_BASED, adminUserId);

        assertThat(medalRound.getMedalMode()).isEqualTo(MedalRoundMode.SCORE_BASED);
    }

    @Test
    void shouldRejectUpdateMedalRoundModeOnActiveRound() {
        var medalRound = new JudgingRound(judging.getId(), "Medal", divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.COMPARATIVE);
        medalRound.markReady();
        medalRound.start();
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);

        assertThatThrownBy(() -> service.updateMedalRoundMode(medalRound.getId(),
                MedalRoundMode.SCORE_BASED, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.medal-round.mode-locked-after-start");
        assertThat(medalRound.getMedalMode()).isEqualTo(MedalRoundMode.COMPARATIVE);
    }

    @Test
    void shouldRejectUpdateMedalRoundModeOnScoringRound() {
        var scoringRound = new JudgingRound(judging.getId(), "Scoring", divisionCategoryId, null);
        given(judgingRoundRepository.findById(scoringRound.getId())).willReturn(Optional.of(scoringRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);

        assertThatThrownBy(() -> service.updateMedalRoundMode(scoringRound.getId(),
                MedalRoundMode.SCORE_BASED, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.medal-round.mode-not-applicable");
    }

    @Test
    void shouldRejectUpdateMedalRoundModeWhenUnauthorized() {
        var medalRound = new JudgingRound(judging.getId(), "Medal", divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.COMPARATIVE);
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(false);

        assertThatThrownBy(() -> service.updateMedalRoundMode(medalRound.getId(),
                MedalRoundMode.SCORE_BASED, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.auth.unauthorized");
    }

    @Test
    void shouldAllowRemoveJudgeOnActiveMedalRoundBypassingMinJudges() {
        // Medal rounds may have fewer judges than scoring rounds (often just
        // head judges). The min-judges check on removeJudge applies only to
        // SCORING; for MEDAL the admin must be able to drop below the
        // scoring-round minimum (even to zero, which is a different shape of
        // panel, not a real-world conflict).
        var medalRound = new JudgingRound(judging.getId(), "Medal", divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.COMPARATIVE);
        var judge1 = UUID.randomUUID();
        medalRound.assignJudge(judge1);
        medalRound.markReady();
        medalRound.start();
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.removeJudge(medalRound.getId(), judge1, adminUserId);

        assertThat(medalRound.getAssignments()).isEmpty();
    }

    @Test
    void shouldCompleteMedalRoundByIdFromActive() {
        var medalRound = new JudgingRound(judging.getId(), "Medal", divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.COMPARATIVE);
        medalRound.markReady();
        medalRound.start();
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);

        service.completeMedalRoundById(medalRound.getId(), adminUserId);

        assertThat(medalRound.getStatus()).isEqualTo(JudgingRoundStatus.COMPLETE);
        then(judgingRoundRepository).should().save(medalRound);
    }

    @Test
    void shouldResetMedalRoundByIdAndDeleteAwards() {
        var medalRound = new JudgingRound(judging.getId(), "Medal", divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.COMPARATIVE);
        medalRound.markReady();
        medalRound.start();
        judging.markActive();
        var award1 = new MedalAward(UUID.randomUUID(), divisionId, divisionCategoryId,
                Medal.GOLD, adminUserId);
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(medalAwardRepository.findByFinalCategoryId(divisionCategoryId))
                .willReturn(List.of(award1));

        service.resetMedalRoundById(medalRound.getId(), adminUserId);

        assertThat(medalRound.getStatus()).isEqualTo(JudgingRoundStatus.READY);
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
    void shouldRejectReopenMedalRoundByIdWhenJudgingNotActive() {
        var medalRound = new JudgingRound(judging.getId(), "Medal", divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.COMPARATIVE);
        medalRound.markReady();
        medalRound.start();
        medalRound.markComplete();
        judging.markActive();
        judging.startBos(); // phase = BOS, not ACTIVE
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);

        assertThatThrownBy(() -> service.reopenMedalRoundById(medalRound.getId(), adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.medal-round.judging-not-active");
    }
}
