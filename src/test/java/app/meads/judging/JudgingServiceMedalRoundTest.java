package app.meads.judging;

import app.meads.BusinessRuleException;
import app.meads.competition.Competition;
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
        var competition = new Competition("Amateur Competition", "amateur-competition",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "Lisboa");
        competition.updateSharedTables(false); // medal-round tests don't exercise cross-division sharing
        division = new Division(competition.getId(), "Amateur", "amateur",
                ScoringSystem.MJP,
                LocalDateTime.of(2026, 6, 1, 23, 59),
                "Europe/Lisbon");
        division.advanceStatus(); // DRAFT → REGISTRATION_OPEN
        division.advanceStatus(); // REGISTRATION_OPEN → REGISTRATION_CLOSED
        division.advanceStatus(); // REGISTRATION_CLOSED → JUDGING (startRound requires this)
        category = new DivisionCategory(divisionId, null, "M1A", "Dry Trad",
                "Description", null, 0);
        judging = new Judging(divisionId);
        lenient().when(competitionService.findDivisionById(any())).thenReturn(division);
        lenient().when(competitionService.findCompetitionById(any())).thenReturn(competition);
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
    void shouldRejectStartScoreBasedMedalRoundWhenNoEntriesAssigned() {
        // Small-category flow: the medal round owns scoresheets, so an empty
        // entries set means nothing to judge. Mirrors the SCORING-round guard.
        var physicalTableId = UUID.randomUUID();
        var medalRound = new JudgingRound(judging.getId(), physicalTableId, "Medal",
                divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.SCORE_BASED);
        medalRound.assignJudge(UUID.randomUUID());
        given(judgingRoundRepository.findByJudgingId(judging.getId())).willReturn(List.of(medalRound));
        given(judgingRoundRepository.findAll()).willReturn(List.of(medalRound));
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);

        assertThatThrownBy(() -> service.startRound(medalRound.getId(), adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.round.no-entries-assigned");

        assertThat(medalRound.getStatus()).isEqualTo(JudgingRoundStatus.PENDING);
        then(scoresheetService).should(never()).createScoresheetsForTable(any());
    }

    @Test
    void shouldCreateBlankScoresheetsWhenStartingScoreBasedMedalRound() {
        // Mirror the SCORING-round createScoresheetsForTable call — the medal
        // round owns the sheets in this mode, so judges need them to fill in.
        var physicalTableId = UUID.randomUUID();
        var medalRound = new JudgingRound(judging.getId(), physicalTableId, "Medal",
                divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.SCORE_BASED);
        medalRound.assignJudge(UUID.randomUUID());
        medalRound.assignEntry(UUID.randomUUID());
        given(judgingRoundRepository.findByJudgingId(judging.getId())).willReturn(List.of(medalRound));
        given(judgingRoundRepository.findAll()).willReturn(List.of(medalRound));
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(scoresheetRepository.findByRoundId(medalRound.getId())).willReturn(List.of());
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(judgingRepository.save(any(Judging.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.startRound(medalRound.getId(), adminUserId);

        assertThat(medalRound.getStatus()).isEqualTo(JudgingRoundStatus.ACTIVE);
        then(scoresheetService).should().createScoresheetsForTable(medalRound.getId());
    }

    @Test
    void shouldAutoPopulateMedalsFromMedalRoundOwnSubmittedSheetsWhenNoPrelimScoringRound() {
        // No preceding scoring round in the category. The medal round's own
        // SUBMITTED sheets feed autoPopulateMedalsByScore — and the
        // advance-flag filter only applies to SCORING-round sheets, not to
        // medal-round-owned sheets (those ARE the medal round).
        var physicalTableId = UUID.randomUUID();
        var medalRound = new JudgingRound(judging.getId(), physicalTableId, "Medal",
                divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.SCORE_BASED);
        medalRound.assignJudge(UUID.randomUUID());
        var entryGold = UUID.randomUUID();
        var entrySilver = UUID.randomUUID();
        var entryBronze = UUID.randomUUID();
        medalRound.assignEntry(entryGold);
        medalRound.assignEntry(entrySilver);
        medalRound.assignEntry(entryBronze);
        var sheetGold = mock(app.meads.judging.Scoresheet.class);
        given(sheetGold.getStatus()).willReturn(ScoresheetStatus.SUBMITTED);
        given(sheetGold.getTotalScore()).willReturn(85);
        given(sheetGold.getEntryId()).willReturn(entryGold);
        given(sheetGold.getRoundId()).willReturn(medalRound.getId());
        // isAdvancedToMedalRound is not asserted — medal-round-owned sheets skip that filter.
        var sheetSilver = mock(app.meads.judging.Scoresheet.class);
        given(sheetSilver.getRoundId()).willReturn(medalRound.getId());
        given(sheetSilver.getStatus()).willReturn(ScoresheetStatus.SUBMITTED);
        given(sheetSilver.getTotalScore()).willReturn(78);
        given(sheetSilver.getEntryId()).willReturn(entrySilver);
        var sheetBronze = mock(app.meads.judging.Scoresheet.class);
        given(sheetBronze.getRoundId()).willReturn(medalRound.getId());
        given(sheetBronze.getStatus()).willReturn(ScoresheetStatus.SUBMITTED);
        given(sheetBronze.getTotalScore()).willReturn(72);
        given(sheetBronze.getEntryId()).willReturn(entryBronze);
        given(judgingRoundRepository.findByJudgingId(judging.getId())).willReturn(List.of(medalRound));
        given(judgingRoundRepository.findAll()).willReturn(List.of(medalRound));
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(scoresheetRepository.findByRoundId(medalRound.getId()))
                .willReturn(List.of(sheetGold, sheetSilver, sheetBronze));
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(judgingRepository.save(any(Judging.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.startRound(medalRound.getId(), adminUserId);

        var awardCaptor = org.mockito.ArgumentCaptor.forClass(MedalAward.class);
        then(medalAwardRepository).should(org.mockito.Mockito.times(3)).save(awardCaptor.capture());
        var awards = awardCaptor.getAllValues();
        assertThat(awards).extracting(MedalAward::getEntryId)
                .containsExactly(entryGold, entrySilver, entryBronze);
        assertThat(awards).extracting(MedalAward::getMedal)
                .containsExactly(Medal.GOLD, Medal.SILVER, Medal.BRONZE);
    }

    @Test
    void shouldRerunAutoPopulateWhenLastSheetSubmittedOnScoreBasedMedalRound() {
        // Listener path: judges submit sheets one-by-one. When the final sheet
        // lands and no BLANK/DRAFT remain, autoPopulate runs and produces
        // medals. Uses the submitting judge as the audit "trigger" user.
        var physicalTableId = UUID.randomUUID();
        var medalRound = new JudgingRound(judging.getId(), physicalTableId, "Medal",
                divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.SCORE_BASED);
        medalRound.assignJudge(UUID.randomUUID());
        var entryGold = UUID.randomUUID();
        var entrySilver = UUID.randomUUID();
        medalRound.assignEntry(entryGold);
        medalRound.assignEntry(entrySilver);
        medalRound.start();
        var judgeUserId = UUID.randomUUID();
        var submittedSheetId = UUID.randomUUID();
        var submittedSheet = mock(app.meads.judging.Scoresheet.class);
        given(submittedSheet.getRoundId()).willReturn(medalRound.getId());
        given(submittedSheet.getFilledByJudgeUserId()).willReturn(judgeUserId);
        given(submittedSheet.getStatus()).willReturn(ScoresheetStatus.SUBMITTED);
        given(submittedSheet.getEntryId()).willReturn(entrySilver);
        given(submittedSheet.getTotalScore()).willReturn(78);
        var earlierSheet = mock(app.meads.judging.Scoresheet.class);
        given(earlierSheet.getRoundId()).willReturn(medalRound.getId());
        given(earlierSheet.getStatus()).willReturn(ScoresheetStatus.SUBMITTED);
        given(earlierSheet.getEntryId()).willReturn(entryGold);
        given(earlierSheet.getTotalScore()).willReturn(85);
        given(scoresheetService.findById(submittedSheetId)).willReturn(Optional.of(submittedSheet));
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(judgingRoundRepository.findByJudgingId(judging.getId())).willReturn(List.of(medalRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(scoresheetService.countByRoundIdAndStatusNot(medalRound.getId(), ScoresheetStatus.SUBMITTED))
                .willReturn(0L);
        given(scoresheetRepository.findByRoundId(medalRound.getId()))
                .willReturn(List.of(submittedSheet, earlierSheet));

        var event = new ScoresheetSubmittedEvent(submittedSheetId, entrySilver,
                medalRound.getId(), 78, java.time.Instant.now());
        service.onScoresheetSubmitted(event);

        var awardCaptor = org.mockito.ArgumentCaptor.forClass(MedalAward.class);
        then(medalAwardRepository).should(org.mockito.Mockito.times(2)).save(awardCaptor.capture());
        var awards = awardCaptor.getAllValues();
        assertThat(awards).extracting(MedalAward::getEntryId)
                .containsExactly(entryGold, entrySilver);
        assertThat(awards).extracting(MedalAward::getMedal)
                .containsExactly(Medal.GOLD, Medal.SILVER);
    }

    @Test
    void shouldAutoPopulateMedalsWhenAllSheetsFilledOnScoreBasedMedalRound() {
        // Judge-driven flow: medals appear as soon as the LAST sheet is FILLED
        // (before the round-level Finalize submits them), keyed on the live
        // medal-eligible total rather than the locked SUBMITTED total.
        var physicalTableId = UUID.randomUUID();
        var medalRound = new JudgingRound(judging.getId(), physicalTableId, "Medal",
                divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.SCORE_BASED);
        medalRound.assignJudge(UUID.randomUUID());
        var entryGold = UUID.randomUUID();
        var entrySilver = UUID.randomUUID();
        medalRound.assignEntry(entryGold);
        medalRound.assignEntry(entrySilver);
        medalRound.start();
        var judgeUserId = UUID.randomUUID();
        var filledSheetId = UUID.randomUUID();
        var goldSheet = mock(app.meads.judging.Scoresheet.class);
        given(goldSheet.getRoundId()).willReturn(medalRound.getId());
        given(goldSheet.getStatus()).willReturn(ScoresheetStatus.FILLED);
        given(goldSheet.medalEligibleTotal()).willReturn(88);
        given(goldSheet.getEntryId()).willReturn(entryGold);
        var silverSheet = mock(app.meads.judging.Scoresheet.class);
        given(silverSheet.getRoundId()).willReturn(medalRound.getId());
        given(silverSheet.getStatus()).willReturn(ScoresheetStatus.FILLED);
        given(silverSheet.medalEligibleTotal()).willReturn(80);
        given(silverSheet.getEntryId()).willReturn(entrySilver);
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(judgingRoundRepository.findByJudgingId(judging.getId())).willReturn(List.of(medalRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(scoresheetService.countByRoundIdAndStatusNot(medalRound.getId(), ScoresheetStatus.FILLED))
                .willReturn(0L);
        given(scoresheetRepository.findByRoundId(medalRound.getId()))
                .willReturn(List.of(goldSheet, silverSheet));

        var event = new ScoresheetFilledEvent(filledSheetId, entrySilver,
                medalRound.getId(), judgeUserId, java.time.Instant.now());
        service.onScoresheetFilled(event);

        var awardCaptor = org.mockito.ArgumentCaptor.forClass(MedalAward.class);
        then(medalAwardRepository).should(org.mockito.Mockito.times(2)).save(awardCaptor.capture());
        assertThat(awardCaptor.getAllValues()).extracting(MedalAward::getEntryId)
                .containsExactly(entryGold, entrySilver);
        assertThat(awardCaptor.getAllValues()).extracting(MedalAward::getMedal)
                .containsExactly(Medal.GOLD, Medal.SILVER);
    }

    @Test
    void shouldRecomputeUnconfirmedMedalsWhenScoresChangeOnScoreBasedMedalRound() {
        // After auto-population, a judge edits a sheet and the ranking flips.
        // Re-running autoPopulate must drop the stale auto (unconfirmed) awards
        // and re-derive from the new scores — previously it skipped any entry
        // that already had an award, so medals never moved.
        var medalRound = new JudgingRound(judging.getId(), UUID.randomUUID(), "Medal",
                divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.SCORE_BASED);
        var entryA = UUID.randomUUID();
        var entryB = UUID.randomUUID();
        medalRound.assignJudge(UUID.randomUUID());
        medalRound.assignEntry(entryA);
        medalRound.assignEntry(entryB);
        medalRound.start();
        // The edit: B now outscores A, but the stale awards still say A=GOLD, B=SILVER.
        var sheetA = mock(app.meads.judging.Scoresheet.class);
        given(sheetA.getRoundId()).willReturn(medalRound.getId());
        given(sheetA.getStatus()).willReturn(ScoresheetStatus.FILLED);
        given(sheetA.medalEligibleTotal()).willReturn(80);
        given(sheetA.getEntryId()).willReturn(entryA);
        var sheetB = mock(app.meads.judging.Scoresheet.class);
        given(sheetB.getRoundId()).willReturn(medalRound.getId());
        given(sheetB.getStatus()).willReturn(ScoresheetStatus.FILLED);
        given(sheetB.medalEligibleTotal()).willReturn(95);
        given(sheetB.getEntryId()).willReturn(entryB);
        var staleGoldA = new MedalAward(entryA, divisionId, divisionCategoryId, Medal.GOLD, adminUserId);
        var staleSilverB = new MedalAward(entryB, divisionId, divisionCategoryId, Medal.SILVER, adminUserId);
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(judgingRoundRepository.findByJudgingId(judging.getId())).willReturn(List.of(medalRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(scoresheetService.countByRoundIdAndStatusNot(medalRound.getId(), ScoresheetStatus.FILLED))
                .willReturn(0L);
        given(scoresheetRepository.findByRoundId(medalRound.getId()))
                .willReturn(List.of(sheetA, sheetB));
        given(medalAwardRepository.findByFinalCategoryId(divisionCategoryId))
                .willReturn(List.of(staleGoldA, staleSilverB));

        service.onScoresheetFilled(new ScoresheetFilledEvent(UUID.randomUUID(), entryB,
                medalRound.getId(), adminUserId, java.time.Instant.now()));

        then(medalAwardRepository).should().deleteAll(List.of(staleGoldA, staleSilverB));
        var captor = org.mockito.ArgumentCaptor.forClass(MedalAward.class);
        then(medalAwardRepository).should(org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(MedalAward::getEntryId)
                .containsExactly(entryB, entryA);
        assertThat(captor.getAllValues()).extracting(MedalAward::getMedal)
                .containsExactly(Medal.GOLD, Medal.SILVER);
    }

    @Test
    void shouldNotAutoPopulateMedalsUntilEverySheetFilledOnScoreBasedMedalRound() {
        var medalRound = new JudgingRound(judging.getId(), UUID.randomUUID(), "Medal",
                divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.SCORE_BASED);
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(scoresheetService.countByRoundIdAndStatusNot(medalRound.getId(), ScoresheetStatus.FILLED))
                .willReturn(1L); // one sheet still BLANK/DRAFT

        service.onScoresheetFilled(new ScoresheetFilledEvent(UUID.randomUUID(), UUID.randomUUID(),
                medalRound.getId(), UUID.randomUUID(), java.time.Instant.now()));

        then(medalAwardRepository).should(never()).save(any(MedalAward.class));
    }

    @Test
    void shouldNotRerunAutoPopulateWhenSheetsStillInProgress() {
        var medalRound = new JudgingRound(judging.getId(), UUID.randomUUID(), "Medal",
                divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.SCORE_BASED);
        var submittedSheetId = UUID.randomUUID();
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(scoresheetService.countByRoundIdAndStatusNot(medalRound.getId(), ScoresheetStatus.SUBMITTED))
                .willReturn(2L); // 2 sheets still BLANK/DRAFT

        var event = new ScoresheetSubmittedEvent(submittedSheetId, UUID.randomUUID(),
                medalRound.getId(), 75, java.time.Instant.now());
        service.onScoresheetSubmitted(event);

        then(medalAwardRepository).should(never()).save(any(MedalAward.class));
    }

    @Test
    void shouldNotRerunAutoPopulateForScoringRoundOnScoresheetSubmitted() {
        // Scoring-round submits don't trigger the medal-round populate path —
        // those go through the existing scoring-round cascade.
        var scoringRound = new JudgingRound(judging.getId(), UUID.randomUUID(), "T1",
                divisionCategoryId, null);
        var submittedSheetId = UUID.randomUUID();
        given(judgingRoundRepository.findById(scoringRound.getId())).willReturn(Optional.of(scoringRound));

        var event = new ScoresheetSubmittedEvent(submittedSheetId, UUID.randomUUID(),
                scoringRound.getId(), 75, java.time.Instant.now());
        service.onScoresheetSubmitted(event);

        then(medalAwardRepository).should(never()).save(any(MedalAward.class));
        then(scoresheetService).should(never()).countByRoundIdAndStatusNot(any(), any());
    }

    @Test
    void shouldFlipScoreBasedMedalRoundToReadyWhenJudgePushesAboveMinimum() {
        // Auto-readiness: SCORE_BASED medal round behaves like a scoring round.
        // Once table + ≥ minJudgesPerRound judges + ≥ 1 entry + division ≥ JUDGING
        // are all satisfied, PENDING → READY without admin intervention.
        var medalRound = new JudgingRound(judging.getId(), UUID.randomUUID(), "Medal",
                divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.SCORE_BASED);
        medalRound.assignEntry(UUID.randomUUID());
        medalRound.assignJudge(UUID.randomUUID()); // 1 of 2 minimum
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.assignJudge(medalRound.getId(), UUID.randomUUID(), adminUserId);

        assertThat(medalRound.getStatus()).isEqualTo(JudgingRoundStatus.READY);
    }

    @Test
    void shouldFlipScoreBasedMedalRoundBackToPendingWhenJudgesDropBelowMinimum() {
        var medalRound = new JudgingRound(judging.getId(), UUID.randomUUID(), "Medal",
                divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.SCORE_BASED);
        medalRound.assignEntry(UUID.randomUUID());
        var judgeA = UUID.randomUUID();
        var judgeB = UUID.randomUUID();
        medalRound.assignJudge(judgeA);
        medalRound.assignJudge(judgeB);
        medalRound.markReady();
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.removeJudge(medalRound.getId(), judgeB, adminUserId);

        assertThat(medalRound.getStatus()).isEqualTo(JudgingRoundStatus.PENDING);
    }

    @Test
    void shouldNotAutoFlipComparativeMedalRoundOnConfigurationChange() {
        // COMPARATIVE keeps the cascade-driven readiness model. Adding a judge
        // doesn't auto-promote — readiness comes from scoring rounds COMPLETE.
        var medalRound = new JudgingRound(judging.getId(), UUID.randomUUID(), "Medal",
                divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.COMPARATIVE);
        medalRound.assignEntry(UUID.randomUUID());
        medalRound.assignJudge(UUID.randomUUID()); // 1 of 2 (would-be minimum)
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.assignJudge(medalRound.getId(), UUID.randomUUID(), adminUserId);

        assertThat(medalRound.getStatus()).isEqualTo(JudgingRoundStatus.PENDING);
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
    void shouldAutoCreateConfigWhenCreatingMedalRoundWithoutPriorConfiguration() {
        // Small-category flow: admin creates the medal round before any scoring
        // round runs in the category, so no CategoryJudgingConfig exists yet.
        // Auto-create with default mode (COMPARATIVE); admin can switch to
        // SCORE_BASED via the MedalRoundView header Select afterwards. Mirrors
        // the existing auto-create in JudgingServiceImpl.startRound for SCORING.
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(competitionService.findDivisionCategoryById(divisionCategoryId)).willReturn(category);
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(judgingRoundRepository.findByJudgingId(judging.getId())).willReturn(List.of());
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.empty());
        given(categoryConfigRepository.save(any(CategoryJudgingConfig.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var round = service.createMedalRound(judging.getId(), divisionCategoryId, adminUserId);

        assertThat(round.getType()).isEqualTo(RoundType.MEDAL);
        assertThat(round.getMedalMode()).isEqualTo(MedalRoundMode.COMPARATIVE);
        then(categoryConfigRepository).should().save(any(CategoryJudgingConfig.class));
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
    void shouldAllowAssignedJudgeToCompleteComparativeMedalRound() {
        // The judges award the medals on a COMPARATIVE round, so an assigned
        // judge (not only an admin) can finalize it — no admin authorization stub.
        var judgeId = UUID.randomUUID();
        var medalRound = new JudgingRound(judging.getId(), "Medal", divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.COMPARATIVE);
        medalRound.assignJudge(judgeId);
        medalRound.markReady();
        medalRound.start();
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.findDivisionById(divisionId)).willReturn(division);

        service.completeMedalRoundById(medalRound.getId(), judgeId);

        assertThat(medalRound.getStatus()).isEqualTo(JudgingRoundStatus.COMPLETE);
    }

    @Test
    void shouldCompleteMedalRoundEvenWhenSomeAssignedEntriesHaveNoMedal() {
        // Withhold was removed: a COMPARATIVE finalize commits whatever medals
        // were awarded and the remaining entries simply receive no medal. The
        // confirmation dialog is responsible for making the "left behind" count
        // clear; the service no longer blocks on undecided entries.
        var medalRound = new JudgingRound(judging.getId(), "Medal", divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.COMPARATIVE);
        medalRound.markReady();
        medalRound.start();
        medalRound.assignEntry(UUID.randomUUID()); // assigned, no medal award — gets no medal
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
    void shouldReturnTransientDefaultConfigForCategoryWithoutOneWithoutPersisting() {
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

        var result = service.findCategoryConfigsForDivision(divisionId, adminUserId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CategoryJudgingConfig::getDivisionCategoryId)
                .containsExactlyInAnyOrder(existingCat.getId(), newCat.getId());
        // The missing one is returned as a transient default — NOT persisted, so
        // entry-less categories don't accumulate config rows.
        then(categoryConfigRepository).should(never()).save(any(CategoryJudgingConfig.class));
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
    void shouldNotFlagTiesWhenNoSubmittedSheetsYetInScoreBasedMedalRound() {
        // Small-category flow: just after admin assigns entries to a SCORE_BASED
        // medal round, no sheets are SUBMITTED yet so every row has a null total.
        // The tied-slot check must skip until at least one score is in — otherwise
        // Objects.equals(null, null) treats all entries as tied at "null".
        var entryA = mockEntryWithoutScoresheet("AMA-1");
        var entryB = mockEntryWithoutScoresheet("AMA-2");
        var entryC = mockEntryWithoutScoresheet("AMA-3");
        var medalRound = new JudgingRound(judging.getId(), UUID.randomUUID(), "Medal",
                divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.SCORE_BASED);
        medalRound.assignEntry(entryA.getId());
        medalRound.assignEntry(entryB.getId());
        medalRound.assignEntry(entryC.getId());
        given(judgingRoundRepository.findFirstByDivisionCategoryIdAndType(divisionCategoryId, RoundType.MEDAL))
                .willReturn(Optional.of(medalRound));
        given(entryService.findEntryById(entryA.getId())).willReturn(entryA);
        given(entryService.findEntryById(entryB.getId())).willReturn(entryB);
        given(entryService.findEntryById(entryC.getId())).willReturn(entryC);
        given(scoresheetRepository.findByEntryId(any())).willReturn(Optional.empty());
        given(medalAwardRepository.findByEntryId(any())).willReturn(Optional.empty());

        var preview = service.recomputeScorePreview(divisionCategoryId);

        assertThat(preview.tiedSlotCount()).isZero();
        assertThat(preview.tiedEntryIds()).isEmpty();
    }

    private Entry mockEntryWithoutScoresheet(String code) {
        var id = UUID.randomUUID();
        var entry = mock(Entry.class);
        lenient().when(entry.getId()).thenReturn(id);
        lenient().when(entry.getEntryCode()).thenReturn(code);
        lenient().when(entry.getMeadName()).thenReturn(code + " mead");
        lenient().when(entry.getUserId()).thenReturn(UUID.randomUUID());
        lenient().when(entry.getStatus()).thenReturn(EntryStatus.RECEIVED);
        return entry;
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
    void shouldIncludeBlankAndDraftSheetsInScoreBasedMedalRoundEntriesViaExplicitEntriesSet() {
        // Small-category flow: SCORE_BASED medal round has entries assigned
        // explicitly but their scoresheets may be BLANK/DRAFT (judges still
        // scoring). The view needs to show those rows so admin sees the
        // assignment + current progress.
        var entrySubmittedId = UUID.randomUUID();
        var entryDraftId = UUID.randomUUID();
        var entryBlankId = UUID.randomUUID();
        var medalRound = new JudgingRound(judging.getId(), UUID.randomUUID(), "Medal",
                divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.SCORE_BASED);
        medalRound.assignEntry(entrySubmittedId);
        medalRound.assignEntry(entryDraftId);
        medalRound.assignEntry(entryBlankId);
        var entrySubmitted = mockReceivedEntry(entrySubmittedId, "AMA-1");
        var entryDraft = mockReceivedEntry(entryDraftId, "AMA-2");
        var entryBlank = mockReceivedEntry(entryBlankId, "AMA-3");
        var sheetSubmitted = mock(Scoresheet.class);
        given(sheetSubmitted.getStatus()).willReturn(ScoresheetStatus.SUBMITTED);
        given(sheetSubmitted.getTotalScore()).willReturn(85);
        given(sheetSubmitted.isAdvancedToMedalRound()).willReturn(false);
        var sheetDraft = mock(Scoresheet.class);
        given(sheetDraft.getStatus()).willReturn(ScoresheetStatus.DRAFT);
        var sheetBlank = mock(Scoresheet.class);
        given(sheetBlank.getStatus()).willReturn(ScoresheetStatus.BLANK);
        given(scoresheetRepository.findByEntryId(entrySubmittedId)).willReturn(Optional.of(sheetSubmitted));
        given(scoresheetRepository.findByEntryId(entryDraftId)).willReturn(Optional.of(sheetDraft));
        given(scoresheetRepository.findByEntryId(entryBlankId)).willReturn(Optional.of(sheetBlank));
        given(medalAwardRepository.findByEntryId(any())).willReturn(Optional.empty());
        given(judgingRoundRepository.findFirstByDivisionCategoryIdAndType(divisionCategoryId, RoundType.MEDAL))
                .willReturn(Optional.of(medalRound));
        given(entryService.findEntryById(entrySubmittedId)).willReturn(entrySubmitted);
        given(entryService.findEntryById(entryDraftId)).willReturn(entryDraft);
        given(entryService.findEntryById(entryBlankId)).willReturn(entryBlank);

        var rows = service.findMedalRoundEntries(divisionCategoryId, MedalRoundMode.SCORE_BASED);

        assertThat(rows).hasSize(3);
        // SUBMITTED row has the highest score (85), then BLANK/DRAFT (null totals)
        // sort last via nullsLast(reverseOrder).
        assertThat(rows.get(0).entryId()).isEqualTo(entrySubmittedId);
        assertThat(rows.get(0).round1Total()).isEqualTo(85);
        assertThat(rows).extracting(MedalRoundEntryRow::entryId)
                .containsExactlyInAnyOrder(entrySubmittedId, entryDraftId, entryBlankId);
    }

    @Test
    void shouldShowExplicitlyAssignedEntryWithNoSheetYetInScoreBasedMedalRound() {
        // Cycle 1 assigns the entry at PENDING with no sheet creation. The
        // view should still surface that row so the admin sees the assignment.
        var entryId = UUID.randomUUID();
        var medalRound = new JudgingRound(judging.getId(), UUID.randomUUID(), "Medal",
                divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.SCORE_BASED);
        medalRound.assignEntry(entryId);
        var entry = mockReceivedEntry(entryId, "AMA-1");
        given(scoresheetRepository.findByEntryId(entryId)).willReturn(Optional.empty());
        given(medalAwardRepository.findByEntryId(any())).willReturn(Optional.empty());
        given(judgingRoundRepository.findFirstByDivisionCategoryIdAndType(divisionCategoryId, RoundType.MEDAL))
                .willReturn(Optional.of(medalRound));
        given(entryService.findEntryById(entryId)).willReturn(entry);

        var rows = service.findMedalRoundEntries(divisionCategoryId, MedalRoundMode.SCORE_BASED);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).round1Total()).isNull();
        assertThat(rows.get(0).advancedToMedalRound()).isFalse();
    }

    private Entry mockReceivedEntry(UUID entryId, String code) {
        var entry = mock(Entry.class);
        lenient().when(entry.getId()).thenReturn(entryId);
        lenient().when(entry.getEntryCode()).thenReturn(code);
        lenient().when(entry.getMeadName()).thenReturn(code + " mead");
        lenient().when(entry.getUserId()).thenReturn(UUID.randomUUID());
        lenient().when(entry.getStatus()).thenReturn(EntryStatus.RECEIVED);
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
    void shouldRevertSubmittedScoresheetsToFilledWhenReopeningScoreBasedMedalRound() {
        // A SCORE_BASED medal round owns its scoresheets; finalize submitted them.
        // Reopening must drop them back to FILLED so judges/admins can edit the
        // scores again — otherwise the medals can be reassigned but the sheets
        // stay locked.
        var medalRound = new JudgingRound(judging.getId(), "Medal", divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.SCORE_BASED);
        medalRound.markReady();
        medalRound.start();
        medalRound.markComplete();
        judging.markActive();
        var submitted = mock(app.meads.judging.Scoresheet.class);
        given(submitted.getStatus()).willReturn(ScoresheetStatus.SUBMITTED);
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(scoresheetRepository.findByRoundId(medalRound.getId())).willReturn(List.of(submitted));

        service.reopenMedalRoundById(medalRound.getId(), adminUserId);

        then(submitted).should().revertToFilled();
        then(scoresheetRepository).should().save(submitted);
        assertThat(medalRound.getStatus()).isEqualTo(JudgingRoundStatus.ACTIVE);
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

    @Test
    void shouldCreateScoresheetWhenAssigningEntryToActiveScoreBasedMedalRound() {
        // Small-category flow: SCORE_BASED medal round runs without a preceding
        // SCORING round. Mid-round add must mirror the SCORING-round behavior
        // (line 344 of JudgingServiceImpl) — create a BLANK scoresheet pinned
        // to the medal round so its judges can start scoring this entry.
        var physicalTableId = UUID.randomUUID();
        var medalRound = new JudgingRound(judging.getId(), physicalTableId,
                "Medal — M1A", divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.SCORE_BASED);
        medalRound.assignJudge(UUID.randomUUID());
        medalRound.start();
        var entryId = UUID.randomUUID();
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.assignEntryToRound(medalRound.getId(), entryId, adminUserId);

        assertThat(medalRound.getEntries()).contains(entryId);
        then(scoresheetService).should().ensureScoresheetForRound(entryId, medalRound.getId());
    }

    @Test
    void shouldNotCreateScoresheetWhenAssigningEntryToComparativeMedalRound() {
        // COMPARATIVE keeps the existing semantics: it picks from advance-flagged
        // entries that already have SUBMITTED sheets from a preceding scoring
        // round. The medal round itself owns no sheets in this mode.
        var physicalTableId = UUID.randomUUID();
        var medalRound = new JudgingRound(judging.getId(), physicalTableId,
                "Medal — M1A", divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.COMPARATIVE);
        medalRound.assignJudge(UUID.randomUUID());
        medalRound.start();
        var entryId = UUID.randomUUID();
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.assignEntryToRound(medalRound.getId(), entryId, adminUserId);

        assertThat(medalRound.getEntries()).contains(entryId);
        then(scoresheetService).should(never()).ensureScoresheetForRound(any(), any());
        then(scoresheetService).should(never()).ensureScoresheetForEntry(any());
    }

    @Test
    void shouldRejectManualUnassignOfReceivedEntryFromActiveScoreBasedMedalRound() {
        // Force-all invariant: RECEIVED entries can't be removed from a
        // SCORE_BASED medal round manually — the reject fires before the
        // SUBMITTED-sheet branch, so scoresheetService is never consulted.
        // Non-RECEIVED entries are an admin escape hatch.
        var physicalTableId = UUID.randomUUID();
        var medalRound = new JudgingRound(judging.getId(), physicalTableId,
                "Medal — M1A", divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.SCORE_BASED);
        medalRound.assignJudge(UUID.randomUUID());
        medalRound.start();
        var entryId = UUID.randomUUID();
        medalRound.assignEntry(entryId);
        var entry = mock(app.meads.entry.Entry.class);
        given(entry.getStatus()).willReturn(app.meads.entry.EntryStatus.RECEIVED);
        given(entryService.findById(entryId)).willReturn(Optional.of(entry));
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);

        assertThatThrownBy(() -> service.unassignEntryFromRound(medalRound.getId(), entryId, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.entry.cannot-unassign-from-score-based");

        assertThat(medalRound.getEntries()).contains(entryId);
        then(scoresheetService).should(never()).deleteScoresheet(any(), any());
    }

    @Test
    void shouldNotCreateScoresheetWhenAssigningEntryToPendingScoreBasedMedalRound() {
        // Pre-start assignments are bookkeeping only — sheets are created at
        // startRound time (see Cycle 3) for whatever's been assigned.
        var medalRound = new JudgingRound(judging.getId(), "Medal — M1A",
                divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.SCORE_BASED);
        var entryId = UUID.randomUUID();
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.assignEntryToRound(medalRound.getId(), entryId, adminUserId);

        assertThat(medalRound.getEntries()).contains(entryId);
        then(scoresheetService).should(never()).ensureScoresheetForRound(any(), any());
    }
}
