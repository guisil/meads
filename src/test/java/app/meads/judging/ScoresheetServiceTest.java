package app.meads.judging;

import app.meads.BusinessRuleException;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionStatus;
import app.meads.entry.Entry;
import app.meads.entry.EntryService;
import app.meads.entry.EntryStatus;
import app.meads.judging.CoiCheckService.CoiResult;
import app.meads.judging.CategoryJudgingConfig;
import app.meads.judging.MedalRoundMode;
import app.meads.judging.internal.CategoryJudgingConfigRepository;
import app.meads.judging.internal.JudgingRepository;
import app.meads.judging.internal.JudgingRoundRepository;
import app.meads.judging.internal.MjpScoringFieldDefinition;
import app.meads.judging.internal.ScoresheetRepository;
import app.meads.judging.internal.ScoresheetServiceImpl;
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
class ScoresheetServiceTest {

    @InjectMocks
    ScoresheetServiceImpl service;

    @Mock
    ScoresheetRepository scoresheetRepository;

    @Mock
    JudgingRoundRepository judgingRoundRepository;

    @Mock
    CategoryJudgingConfigRepository categoryConfigRepository;

    @Mock
    JudgingRepository judgingRepository;

    @Mock
    EntryService entryService;

    @Mock
    CompetitionService competitionService;

    @Mock
    JudgeProfileService judgeProfileService;

    @Mock
    CoiCheckService coiCheckService;

    @Mock
    ApplicationEventPublisher eventPublisher;

    UUID divisionId;
    UUID divisionCategoryId;
    UUID roundId;
    UUID judgeUserId;
    UUID adminUserId;
    UUID competitionId;
    JudgingRound table;
    Judging judging;

    @BeforeEach
    void setUp() {
        divisionId = UUID.randomUUID();
        divisionCategoryId = UUID.randomUUID();
        judgeUserId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();
        competitionId = UUID.randomUUID();
        judging = new Judging(divisionId);
        table = new JudgingRound(judging.getId(), "T1", divisionCategoryId, LocalDateTime.of(2026, 7, 1, 0, 0));
        roundId = table.getId();
        var nonFrozenDivision = mock(Division.class);
        lenient().when(nonFrozenDivision.getStatus()).thenReturn(DivisionStatus.JUDGING);
        lenient().when(competitionService.findDivisionById(any())).thenReturn(nonFrozenDivision);
        lenient().when(judgingRoundRepository.findById(roundId)).thenReturn(Optional.of(table));
        lenient().when(judgingRepository.findById(judging.getId())).thenReturn(Optional.of(judging));
    }

    private Entry mockEntry(UUID entryId, UUID userId) {
        return mockEntry(entryId, userId, EntryStatus.RECEIVED);
    }

    private Entry mockEntry(UUID entryId, UUID userId, EntryStatus status) {
        var entry = mock(Entry.class);
        lenient().when(entry.getId()).thenReturn(entryId);
        lenient().when(entry.getUserId()).thenReturn(userId);
        lenient().when(entry.getDivisionId()).thenReturn(divisionId);
        lenient().when(entry.getFinalCategoryId()).thenReturn(divisionCategoryId);
        lenient().when(entry.getStatus()).thenReturn(status);
        return entry;
    }

    @Test
    void shouldCountScoresheetsByTableAndStatus() {
        given(scoresheetRepository.countByRoundIdAndStatus(roundId, ScoresheetStatus.DRAFT))
                .willReturn(3L);
        given(scoresheetRepository.countByRoundIdAndStatus(roundId, ScoresheetStatus.SUBMITTED))
                .willReturn(2L);

        assertThat(service.countByRoundIdAndStatus(roundId, ScoresheetStatus.DRAFT)).isEqualTo(3L);
        assertThat(service.countByRoundIdAndStatus(roundId, ScoresheetStatus.SUBMITTED)).isEqualTo(2L);
    }

    @Test
    void shouldCreateOneScoresheetPerEntryWithMatchingFinalCategory() {
        var e1 = mockEntry(UUID.randomUUID(), UUID.randomUUID());
        var e2 = mockEntry(UUID.randomUUID(), UUID.randomUUID());
        given(judgingRoundRepository.findById(roundId)).willReturn(Optional.of(table));
        given(entryService.findEntriesByFinalCategoryId(divisionCategoryId))
                .willReturn(List.of(e1, e2));
        given(scoresheetRepository.findByEntryId(any())).willReturn(Optional.empty());
        given(scoresheetRepository.save(any(Scoresheet.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.createScoresheetsForTable(roundId);

        then(scoresheetRepository).should(org.mockito.Mockito.times(2)).save(any(Scoresheet.class));
    }

    @Test
    void shouldSkipNonReceivedEntriesWhenCreatingScoresheets() {
        var received = mockEntry(UUID.randomUUID(), UUID.randomUUID(), EntryStatus.RECEIVED);
        var submitted = mockEntry(UUID.randomUUID(), UUID.randomUUID(), EntryStatus.SUBMITTED);
        var withdrawn = mockEntry(UUID.randomUUID(), UUID.randomUUID(), EntryStatus.WITHDRAWN);
        given(judgingRoundRepository.findById(roundId)).willReturn(Optional.of(table));
        given(entryService.findEntriesByFinalCategoryId(divisionCategoryId))
                .willReturn(List.of(received, submitted, withdrawn));
        given(scoresheetRepository.findByEntryId(any())).willReturn(Optional.empty());
        given(scoresheetRepository.save(any(Scoresheet.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.createScoresheetsForTable(roundId);

        then(scoresheetRepository).should(org.mockito.Mockito.times(1)).save(any(Scoresheet.class));
    }

    @Test
    void shouldSkipEntriesThatAlreadyHaveAScoresheet() {
        var e1 = mockEntry(UUID.randomUUID(), UUID.randomUUID());
        var existing = new Scoresheet(roundId, e1.getId());
        given(judgingRoundRepository.findById(roundId)).willReturn(Optional.of(table));
        given(entryService.findEntriesByFinalCategoryId(divisionCategoryId))
                .willReturn(List.of(e1));
        given(scoresheetRepository.findByEntryId(e1.getId())).willReturn(Optional.of(existing));

        service.createScoresheetsForTable(roundId);

        then(scoresheetRepository).should(never()).save(any(Scoresheet.class));
    }

    @Test
    void shouldUseRoundEntriesWhenNonEmpty() {
        var assigned1 = UUID.randomUUID();
        var assigned2 = UUID.randomUUID();
        var notAssigned = UUID.randomUUID();
        table.assignEntry(assigned1);
        table.assignEntry(assigned2);
        var e1 = mockEntry(assigned1, UUID.randomUUID());
        var e2 = mockEntry(assigned2, UUID.randomUUID());
        given(entryService.findEntryById(assigned1)).willReturn(e1);
        given(entryService.findEntryById(assigned2)).willReturn(e2);
        given(scoresheetRepository.findByEntryId(any())).willReturn(Optional.empty());
        given(scoresheetRepository.save(any(Scoresheet.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.createScoresheetsForTable(roundId);

        // Should NOT fall back to the derived list — round.entries is the source of truth.
        then(entryService).should(never()).findEntriesByFinalCategoryId(any());
        // notAssigned is not in round.entries, so it must not be looked up.
        then(entryService).should(never()).findEntryById(notAssigned);
        then(scoresheetRepository).should(org.mockito.Mockito.times(2)).save(any(Scoresheet.class));
    }

    @Test
    void shouldUpdateScoreAndSetFilledByOnFirstCall() {
        var entryId = UUID.randomUUID();
        var scoresheet = new Scoresheet(roundId, entryId);
        given(scoresheetRepository.findById(scoresheet.getId())).willReturn(Optional.of(scoresheet));
        given(coiCheckService.check(judgeUserId, entryId)).willReturn(CoiResult.clear());
        given(scoresheetRepository.save(any(Scoresheet.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.updateScore(scoresheet.getId(),
                MjpScoringFieldDefinition.APPEARANCE, 10, "looks great", judgeUserId);

        assertThat(scoresheet.getFilledByJudgeUserId()).isEqualTo(judgeUserId);
        var appearance = scoresheet.getFields().stream()
                .filter(f -> f.getFieldName().equals(MjpScoringFieldDefinition.APPEARANCE))
                .findFirst().orElseThrow();
        assertThat(appearance.getValue()).isEqualTo(10);
        assertThat(appearance.getComment()).isEqualTo("looks great");
    }

    private Scoresheet filledSheet(UUID entryId, UUID filledBy) {
        var sheet = new Scoresheet(roundId, entryId);
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            sheet.updateScore(def.fieldName(), def.maxValue(), "good depth and balance");
        }
        sheet.updateOverallComments("A reasonably-worded overall assessment.");
        sheet.setFilledBy(filledBy);
        sheet.markFilled();
        return sheet;
    }

    @Test
    void shouldSwitchFilledByToTheLastAssignedJudgeWhoRevalidates() {
        // Non-standard but unblockable: a second assigned judge edits + re-Saves
        // another judge's sheet. "Filled by" follows the latest validator.
        var judge2 = UUID.randomUUID();
        table.assignJudge(judgeUserId);
        table.assignJudge(judge2);
        var entryId = UUID.randomUUID();
        var scoresheet = filledSheet(entryId, judgeUserId);
        given(scoresheetRepository.findById(scoresheet.getId())).willReturn(Optional.of(scoresheet));
        given(coiCheckService.check(judge2, entryId)).willReturn(CoiResult.clear());
        given(scoresheetRepository.save(any(Scoresheet.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.markFilled(scoresheet.getId(), judge2);

        assertThat(scoresheet.getFilledByJudgeUserId()).isEqualTo(judge2);
    }

    @Test
    void shouldNotChangeFilledByWhenNonAssignedUserRevalidates() {
        // An admin editing on behalf of a judge is not an assigned judge and must
        // not claim authorship of an already-filled sheet.
        var admin = UUID.randomUUID();
        table.assignJudge(judgeUserId);
        var entryId = UUID.randomUUID();
        var scoresheet = filledSheet(entryId, judgeUserId);
        given(scoresheetRepository.findById(scoresheet.getId())).willReturn(Optional.of(scoresheet));
        given(coiCheckService.check(admin, entryId)).willReturn(CoiResult.clear());
        given(scoresheetRepository.save(any(Scoresheet.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.markFilled(scoresheet.getId(), admin);

        assertThat(scoresheet.getFilledByJudgeUserId()).isEqualTo(judgeUserId);
    }

    @Test
    void shouldRejectUpdateScoreWhenHardCoiBlock() {
        var entryId = UUID.randomUUID();
        var scoresheet = new Scoresheet(roundId, entryId);
        given(scoresheetRepository.findById(scoresheet.getId())).willReturn(Optional.of(scoresheet));
        given(coiCheckService.check(judgeUserId, entryId)).willReturn(CoiResult.blocking());

        assertThatThrownBy(() -> service.updateScore(scoresheet.getId(),
                MjpScoringFieldDefinition.APPEARANCE, 10, "x", judgeUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.coi.self-entry");

        then(scoresheetRepository).should(never()).save(any(Scoresheet.class));
    }

    @Test
    void shouldFinalizeScoringRoundSubmittingAllSheetsAndCompleting() {
        var entryId = UUID.randomUUID();
        var scoresheet = new Scoresheet(roundId, entryId);
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            scoresheet.updateScore(def.fieldName(), def.maxValue(), "good depth and balance");
        }
        scoresheet.updateOverallComments("A reasonably-worded overall assessment.");
        scoresheet.markFilled(); // FILLED — the precondition the round Finalize requires
        given(judgingRoundRepository.findById(roundId)).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(scoresheetRepository.findByRoundId(roundId)).willReturn(List.of(scoresheet));
        given(scoresheetRepository.save(any(Scoresheet.class)))
                .willAnswer(inv -> inv.getArgument(0));

        table.start();

        service.finalizeScoringRound(roundId, adminUserId);

        assertThat(scoresheet.getStatus()).isEqualTo(ScoresheetStatus.SUBMITTED);
        assertThat(scoresheet.getTotalScore()).isEqualTo(100);
        assertThat(table.getStatus()).isEqualTo(JudgingRoundStatus.COMPLETE);
    }

    @Test
    void shouldRevertToDraftWhenAdmin() {
        var entryId = UUID.randomUUID();
        var scoresheet = new Scoresheet(roundId, entryId);
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            scoresheet.updateScore(def.fieldName(), def.maxValue(), null);
        }
        scoresheet.markFilled();
        scoresheet.submit();
        given(scoresheetRepository.findById(scoresheet.getId())).willReturn(Optional.of(scoresheet));
        given(judgingRoundRepository.findById(roundId)).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(scoresheetRepository.save(any(Scoresheet.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.revertToDraft(scoresheet.getId(), adminUserId);

        assertThat(scoresheet.getStatus()).isEqualTo(ScoresheetStatus.DRAFT);
        assertThat(scoresheet.getTotalScore()).isNull();
    }

    @Test
    void shouldRejectRevertToDraftWhenNotAuthorized() {
        var entryId = UUID.randomUUID();
        var scoresheet = new Scoresheet(roundId, entryId);
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            scoresheet.updateScore(def.fieldName(), def.maxValue(), null);
        }
        scoresheet.markFilled();
        scoresheet.submit();
        given(scoresheetRepository.findById(scoresheet.getId())).willReturn(Optional.of(scoresheet));
        given(judgingRoundRepository.findById(roundId)).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(false);

        assertThatThrownBy(() -> service.revertToDraft(scoresheet.getId(), adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.auth.unauthorized");
    }

    @Test
    void shouldMoveScoresheetToNewTableWhenCategoryMatches() {
        var entryId = UUID.randomUUID();
        var scoresheet = new Scoresheet(roundId, entryId);
        var newTable = new JudgingRound(judging.getId(), "T2", divisionCategoryId, null);
        var entry = mockEntry(entryId, UUID.randomUUID());
        given(scoresheetRepository.findById(scoresheet.getId())).willReturn(Optional.of(scoresheet));
        given(judgingRoundRepository.findById(newTable.getId())).willReturn(Optional.of(newTable));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(entryService.findEntryById(entryId)).willReturn(entry);
        given(scoresheetRepository.save(any(Scoresheet.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.moveToRound(scoresheet.getId(), newTable.getId(), adminUserId);

        assertThat(scoresheet.getRoundId()).isEqualTo(newTable.getId());
    }

    @Test
    void shouldRejectMoveToTableWhenCategoryMismatch() {
        var entryId = UUID.randomUUID();
        var scoresheet = new Scoresheet(roundId, entryId);
        var differentCategory = UUID.randomUUID();
        var newTable = new JudgingRound(judging.getId(), "T2", differentCategory, null);
        var entry = mockEntry(entryId, UUID.randomUUID());
        given(scoresheetRepository.findById(scoresheet.getId())).willReturn(Optional.of(scoresheet));
        given(judgingRoundRepository.findById(newTable.getId())).willReturn(Optional.of(newTable));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(entryService.findEntryById(entryId)).willReturn(entry);

        assertThatThrownBy(() -> service.moveToRound(scoresheet.getId(), newTable.getId(), adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.scoresheet.category-mismatch");
    }

    @Test
    void shouldSetCommentLanguageWhenValidIsoCode() {
        var entryId = UUID.randomUUID();
        var scoresheet = new Scoresheet(roundId, entryId);
        var division = mock(app.meads.competition.Division.class);
        given(scoresheetRepository.findById(scoresheet.getId())).willReturn(Optional.of(scoresheet));
        given(judgingRoundRepository.findById(roundId)).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(coiCheckService.check(judgeUserId, entryId)).willReturn(CoiResult.clear());
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(division.getStatus()).willReturn(DivisionStatus.JUDGING);
        given(scoresheetRepository.save(any(Scoresheet.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.setCommentLanguage(scoresheet.getId(), "pt", judgeUserId);

        assertThat(scoresheet.getCommentLanguage()).isEqualTo("pt");
        then(judgeProfileService).should().updatePreferredCommentLanguage(judgeUserId, "pt");
    }

    @Test
    void shouldAutoCreateMedalJudgingRoundIfMissingWhenCascadeFires() {
        var entryId = UUID.randomUUID();
        var scoresheet = new Scoresheet(roundId, entryId);
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            scoresheet.updateScore(def.fieldName(), def.maxValue(), "good depth and balance");
        }
        scoresheet.updateOverallComments("A reasonably-worded overall assessment.");
        scoresheet.setFilledBy(judgeUserId);
        // No medal JudgingRound exists yet — only the scoring table.
        var config = new CategoryJudgingConfig(divisionCategoryId, MedalRoundMode.SCORE_BASED);
        var category = new app.meads.competition.DivisionCategory(judging.getDivisionId(),
                null, "M1A", "Dry Mead", "Desc", null, 0);
        given(judgingRoundRepository.findById(roundId)).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(scoresheetRepository.findByRoundId(roundId)).willReturn(List.of(scoresheet));
        given(judgingRoundRepository.findByJudgingId(judging.getId())).willReturn(List.of(table));
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(scoresheetRepository.save(any(Scoresheet.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.of(config));
        given(competitionService.findDivisionCategoryById(divisionCategoryId)).willReturn(category);

        scoresheet.markFilled();
        table.start();

        service.finalizeScoringRound(roundId, adminUserId);

        var captor = org.mockito.ArgumentCaptor.forClass(JudgingRound.class);
        then(judgingRoundRepository).should(org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        var medalRound = captor.getAllValues().stream()
                .filter(r -> r.getType() == app.meads.judging.RoundType.MEDAL)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No medal JudgingRound created"));
        assertThat(medalRound.getDivisionCategoryId()).isEqualTo(divisionCategoryId);
        assertThat(medalRound.getMedalMode()).isEqualTo(MedalRoundMode.SCORE_BASED);
        assertThat(medalRound.getStatus()).isEqualTo(JudgingRoundStatus.READY);
    }

    @Test
    void shouldMarkMedalJudgingRoundReadyWhenAllScoringRoundsInCategoryComplete() {
        var entryId = UUID.randomUUID();
        var scoresheet = new Scoresheet(roundId, entryId);
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            scoresheet.updateScore(def.fieldName(), def.maxValue(), "good depth and balance");
        }
        scoresheet.updateOverallComments("A reasonably-worded overall assessment.");
        scoresheet.setFilledBy(judgeUserId);
        var medalRound = new JudgingRound(judging.getId(), UUID.randomUUID(), "Medal",
                divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.COMPARATIVE);
        given(judgingRoundRepository.findById(roundId)).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(scoresheetRepository.findByRoundId(roundId)).willReturn(List.of(scoresheet));
        given(judgingRoundRepository.findByJudgingId(judging.getId()))
                .willReturn(List.of(table, medalRound));
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(scoresheetRepository.save(any(Scoresheet.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.of(new CategoryJudgingConfig(divisionCategoryId)));

        scoresheet.markFilled();
        table.start();

        service.finalizeScoringRound(roundId, adminUserId);

        assertThat(table.getStatus()).isEqualTo(JudgingRoundStatus.COMPLETE);
        assertThat(medalRound.getStatus()).isEqualTo(JudgingRoundStatus.READY);
    }

    @Test
    void shouldReadyMedalRoundWithoutPopulatingEntriesWhenComparativeCascadeFires() {
        // When the scoring rounds in a category COMPLETE, the cascade auto-creates
        // / readies the medal round but does NOT copy the candidate entries onto
        // it: they still live in their SCORING rounds' `entries`
        // (judging_round_entries.entry_id is globally unique — an entry is on
        // exactly one round). Candidates derive from the advance-flagged
        // scoresheets instead (findMedalRoundEntries).
        var entryId = UUID.randomUUID();
        var triggeringScoresheet = new Scoresheet(roundId, entryId);
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            triggeringScoresheet.updateScore(def.fieldName(), def.maxValue(), "good depth and balance");
        }
        triggeringScoresheet.updateOverallComments("A reasonably-worded overall assessment.");
        triggeringScoresheet.setAdvancedToMedalRound(true);
        triggeringScoresheet.setFilledBy(judgeUserId);

        var medalRound = new JudgingRound(judging.getId(), UUID.randomUUID(), "Medal",
                divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.COMPARATIVE);

        given(judgingRoundRepository.findById(roundId)).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(scoresheetRepository.findByRoundId(roundId)).willReturn(List.of(triggeringScoresheet));
        given(judgingRoundRepository.findByJudgingId(judging.getId()))
                .willReturn(List.of(table, medalRound));
        given(judgingRoundRepository.save(any(JudgingRound.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(scoresheetRepository.save(any(Scoresheet.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId))
                .willReturn(Optional.of(new CategoryJudgingConfig(divisionCategoryId, MedalRoundMode.COMPARATIVE)));

        triggeringScoresheet.markFilled();
        table.start();

        service.finalizeScoringRound(roundId, adminUserId);

        assertThat(medalRound.getStatus()).isEqualTo(JudgingRoundStatus.READY);
        assertThat(medalRound.getEntries()).isEmpty();
    }

    @Test
    void shouldRejectSetCommentLanguageWhenNotValidIsoCode() {
        var entryId = UUID.randomUUID();
        var scoresheet = new Scoresheet(roundId, entryId);
        var division = mock(app.meads.competition.Division.class);
        given(scoresheetRepository.findById(scoresheet.getId())).willReturn(Optional.of(scoresheet));
        given(judgingRoundRepository.findById(roundId)).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(coiCheckService.check(judgeUserId, entryId)).willReturn(CoiResult.clear());
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(division.getStatus()).willReturn(DivisionStatus.JUDGING);

        assertThatThrownBy(() -> service.setCommentLanguage(scoresheet.getId(), "xx-not-iso", judgeUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.scoresheet.language-not-allowed");
    }

    @Test
    void shouldSilentlyNoOpSetAdvancedToMedalRoundForSheetOwnedByMedalRound() {
        // Small-category SCORE_BASED flow: judges save sheets at an ACTIVE
        // medal round. saveDraft always calls setAdvancedToMedalRound, which
        // would fire the medal-round-active guard and block every save. The
        // "advance to medal round" flag is meaningless for medal-owned sheets
        // (the entry is already there), so the call must no-op cleanly.
        var entryId = UUID.randomUUID();
        var medalRound = new JudgingRound(judging.getId(), UUID.randomUUID(), "Medal",
                divisionCategoryId, null);
        medalRound.convertToMedalRound(MedalRoundMode.SCORE_BASED);
        var scoresheet = new Scoresheet(medalRound.getId(), entryId);
        given(scoresheetRepository.findById(scoresheet.getId())).willReturn(Optional.of(scoresheet));
        given(judgingRoundRepository.findById(medalRound.getId())).willReturn(Optional.of(medalRound));
        given(coiCheckService.check(judgeUserId, entryId)).willReturn(CoiResult.clear());

        service.setAdvancedToMedalRound(scoresheet.getId(), true, judgeUserId);

        assertThat(scoresheet.isAdvancedToMedalRound()).isFalse(); // unchanged
        then(scoresheetRepository).should(never()).save(any(Scoresheet.class));
    }

    @Test
    void shouldMarkSheetFilledWhenAllFieldsScoredAndCommentsLongEnough() {
        var entryId = UUID.randomUUID();
        var scoresheet = new Scoresheet(roundId, entryId);
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            scoresheet.updateScore(def.fieldName(), def.maxValue(), "good depth and balance");
        }
        given(scoresheetRepository.findById(scoresheet.getId())).willReturn(Optional.of(scoresheet));
        given(coiCheckService.check(judgeUserId, entryId)).willReturn(CoiResult.clear());
        given(scoresheetRepository.save(any(Scoresheet.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.markFilled(scoresheet.getId(), judgeUserId);

        assertThat(scoresheet.getStatus()).isEqualTo(ScoresheetStatus.FILLED);
        assertThat(scoresheet.getFilledByJudgeUserId()).isEqualTo(judgeUserId);
        assertThat(scoresheet.getTotalScore())
                .as("Save must not compute the total — that is the round Finalize's job")
                .isNull();
    }

    @Test
    void shouldRejectMarkFilledWhenAFieldCommentIsTooShort() {
        var entryId = UUID.randomUUID();
        var scoresheet = new Scoresheet(roundId, entryId);
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            scoresheet.updateScore(def.fieldName(), def.maxValue(), "good depth and balance");
        }
        // Shorten one criterion's comment below the 15-char floor.
        scoresheet.updateScore(MjpScoringFieldDefinition.FINISH, 14, "short");
        given(scoresheetRepository.findById(scoresheet.getId())).willReturn(Optional.of(scoresheet));
        given(coiCheckService.check(judgeUserId, entryId)).willReturn(CoiResult.clear());

        assertThatThrownBy(() -> service.markFilled(scoresheet.getId(), judgeUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.scoresheet.field-comment-too-short");

        assertThat(scoresheet.getStatus()).isNotEqualTo(ScoresheetStatus.FILLED);
    }

    @Test
    void shouldRejectMarkFilledWhenAFieldIsUnscored() {
        var entryId = UUID.randomUUID();
        var scoresheet = new Scoresheet(roundId, entryId);
        // All comments long enough, but leave one field unscored.
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            Integer value = def.fieldName().equals(MjpScoringFieldDefinition.FINISH)
                    ? null : def.maxValue();
            scoresheet.updateScore(def.fieldName(), value, "good depth and balance");
        }
        given(scoresheetRepository.findById(scoresheet.getId())).willReturn(Optional.of(scoresheet));
        given(coiCheckService.check(judgeUserId, entryId)).willReturn(CoiResult.clear());

        assertThatThrownBy(() -> service.markFilled(scoresheet.getId(), judgeUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.scoresheet.incomplete");

        assertThat(scoresheet.getStatus()).isNotEqualTo(ScoresheetStatus.FILLED);
    }

    @Test
    void shouldRejectFinalizeScoringRoundWhenAnySheetNotFilled() {
        var scoresheet = new Scoresheet(roundId, UUID.randomUUID()); // BLANK, not FILLED
        given(scoresheetRepository.findByRoundId(roundId)).willReturn(List.of(scoresheet));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        table.start(); // ACTIVE

        assertThatThrownBy(() -> service.finalizeScoringRound(roundId, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.round.cannot-finalize-unfilled");

        assertThat(table.getStatus()).isEqualTo(JudgingRoundStatus.ACTIVE);
    }

    @Test
    void shouldRejectFinalizeScoringRoundWhenRoundNotActive() {
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        // table is PENDING — never started

        assertThatThrownBy(() -> service.finalizeScoringRound(roundId, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.round.cannot-finalize-not-active");
    }

    @Test
    void shouldReopenScoringRoundDroppingSubmittedSheetsBackToFilled() {
        var scoresheet = new Scoresheet(roundId, UUID.randomUUID());
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            scoresheet.updateScore(def.fieldName(), def.maxValue(), null);
        }
        scoresheet.markFilled();
        scoresheet.submit(); // SUBMITTED
        table.start();
        table.markComplete(); // COMPLETE
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(scoresheetRepository.findByRoundId(roundId)).willReturn(List.of(scoresheet));
        given(scoresheetRepository.save(any(Scoresheet.class))).willAnswer(inv -> inv.getArgument(0));
        given(judgingRoundRepository.save(any(JudgingRound.class))).willAnswer(inv -> inv.getArgument(0));
        given(judgingRoundRepository.findFirstByDivisionCategoryIdAndType(
                divisionCategoryId, app.meads.judging.RoundType.MEDAL)).willReturn(Optional.empty());

        service.reopenScoringRound(roundId, adminUserId);

        assertThat(table.getStatus()).isEqualTo(JudgingRoundStatus.ACTIVE);
        assertThat(scoresheet.getStatus()).isEqualTo(ScoresheetStatus.FILLED);
        assertThat(scoresheet.getTotalScore()).isNull();
    }
}
