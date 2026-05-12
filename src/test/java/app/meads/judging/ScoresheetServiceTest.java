package app.meads.judging;

import app.meads.BusinessRuleException;
import app.meads.competition.Competition;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionStatus;
import app.meads.entry.Entry;
import app.meads.entry.EntryService;
import app.meads.judging.CoiCheckService.CoiResult;
import app.meads.judging.internal.CategoryJudgingConfigRepository;
import app.meads.judging.internal.JudgingRepository;
import app.meads.judging.internal.JudgingTableRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    JudgingTableRepository judgingTableRepository;

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
    UUID tableId;
    UUID judgeUserId;
    UUID adminUserId;
    UUID competitionId;
    JudgingTable table;
    Judging judging;

    @BeforeEach
    void setUp() {
        divisionId = UUID.randomUUID();
        divisionCategoryId = UUID.randomUUID();
        judgeUserId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();
        competitionId = UUID.randomUUID();
        judging = new Judging(divisionId);
        table = new JudgingTable(judging.getId(), "T1", divisionCategoryId, LocalDate.of(2026, 7, 1));
        tableId = table.getId();
        var nonFrozenDivision = mock(Division.class);
        lenient().when(nonFrozenDivision.getStatus()).thenReturn(DivisionStatus.JUDGING);
        lenient().when(competitionService.findDivisionById(any())).thenReturn(nonFrozenDivision);
        lenient().when(judgingTableRepository.findById(tableId)).thenReturn(Optional.of(table));
        lenient().when(judgingRepository.findById(judging.getId())).thenReturn(Optional.of(judging));
    }

    private Entry mockEntry(UUID entryId, UUID userId) {
        var entry = mock(Entry.class);
        lenient().when(entry.getId()).thenReturn(entryId);
        lenient().when(entry.getUserId()).thenReturn(userId);
        lenient().when(entry.getDivisionId()).thenReturn(divisionId);
        lenient().when(entry.getFinalCategoryId()).thenReturn(divisionCategoryId);
        return entry;
    }

    @Test
    void shouldCountScoresheetsByTableAndStatus() {
        given(scoresheetRepository.countByTableIdAndStatus(tableId, ScoresheetStatus.DRAFT))
                .willReturn(3L);
        given(scoresheetRepository.countByTableIdAndStatus(tableId, ScoresheetStatus.SUBMITTED))
                .willReturn(2L);

        assertThat(service.countByTableIdAndStatus(tableId, ScoresheetStatus.DRAFT)).isEqualTo(3L);
        assertThat(service.countByTableIdAndStatus(tableId, ScoresheetStatus.SUBMITTED)).isEqualTo(2L);
    }

    @Test
    void shouldCreateOneScoresheetPerEntryWithMatchingFinalCategory() {
        var e1 = mockEntry(UUID.randomUUID(), UUID.randomUUID());
        var e2 = mockEntry(UUID.randomUUID(), UUID.randomUUID());
        given(judgingTableRepository.findById(tableId)).willReturn(Optional.of(table));
        given(entryService.findEntriesByFinalCategoryId(divisionCategoryId))
                .willReturn(List.of(e1, e2));
        given(scoresheetRepository.findByEntryId(any())).willReturn(Optional.empty());
        given(scoresheetRepository.save(any(Scoresheet.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.createScoresheetsForTable(tableId);

        then(scoresheetRepository).should(org.mockito.Mockito.times(2)).save(any(Scoresheet.class));
    }

    @Test
    void shouldSkipEntriesThatAlreadyHaveAScoresheet() {
        var e1 = mockEntry(UUID.randomUUID(), UUID.randomUUID());
        var existing = new Scoresheet(tableId, e1.getId());
        given(judgingTableRepository.findById(tableId)).willReturn(Optional.of(table));
        given(entryService.findEntriesByFinalCategoryId(divisionCategoryId))
                .willReturn(List.of(e1));
        given(scoresheetRepository.findByEntryId(e1.getId())).willReturn(Optional.of(existing));

        service.createScoresheetsForTable(tableId);

        then(scoresheetRepository).should(never()).save(any(Scoresheet.class));
    }

    @Test
    void shouldUpdateScoreAndSetFilledByOnFirstCall() {
        var entryId = UUID.randomUUID();
        var scoresheet = new Scoresheet(tableId, entryId);
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

    @Test
    void shouldRejectUpdateScoreWhenHardCoiBlock() {
        var entryId = UUID.randomUUID();
        var scoresheet = new Scoresheet(tableId, entryId);
        given(scoresheetRepository.findById(scoresheet.getId())).willReturn(Optional.of(scoresheet));
        given(coiCheckService.check(judgeUserId, entryId)).willReturn(CoiResult.blocking());

        assertThatThrownBy(() -> service.updateScore(scoresheet.getId(),
                MjpScoringFieldDefinition.APPEARANCE, 10, "x", judgeUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.coi.self-entry");

        then(scoresheetRepository).should(never()).save(any(Scoresheet.class));
    }

    @Test
    void shouldSubmitScoresheetWhenAllFieldsFilled() {
        var entryId = UUID.randomUUID();
        var scoresheet = new Scoresheet(tableId, entryId);
        // Fill all 5 fields with max value
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            scoresheet.updateScore(def.fieldName(), def.maxValue(), null);
        }
        scoresheet.setFilledBy(judgeUserId);
        given(scoresheetRepository.findById(scoresheet.getId())).willReturn(Optional.of(scoresheet));
        given(judgingTableRepository.findById(tableId)).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(coiCheckService.check(judgeUserId, entryId)).willReturn(CoiResult.clear());
        given(scoresheetRepository.findByTableId(tableId))
                .willReturn(List.of(scoresheet)); // last DRAFT
        given(scoresheetRepository.save(any(Scoresheet.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // Pre-mark table started
        table.startRound1();

        service.submit(scoresheet.getId(), judgeUserId);

        assertThat(scoresheet.getStatus()).isEqualTo(ScoresheetStatus.SUBMITTED);
        assertThat(scoresheet.getTotalScore()).isEqualTo(100);
        assertThat(table.getStatus()).isEqualTo(JudgingTableStatus.COMPLETE);
    }

    @Test
    void shouldRejectSubmitWhenNotAllFieldsFilled() {
        var entryId = UUID.randomUUID();
        var scoresheet = new Scoresheet(tableId, entryId);
        scoresheet.updateScore(MjpScoringFieldDefinition.APPEARANCE, 10, null);
        given(scoresheetRepository.findById(scoresheet.getId())).willReturn(Optional.of(scoresheet));
        given(coiCheckService.check(judgeUserId, entryId)).willReturn(CoiResult.clear());

        assertThatThrownBy(() -> service.submit(scoresheet.getId(), judgeUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.scoresheet.incomplete");
    }

    @Test
    void shouldRevertToDraftWhenAdmin() {
        var entryId = UUID.randomUUID();
        var scoresheet = new Scoresheet(tableId, entryId);
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            scoresheet.updateScore(def.fieldName(), def.maxValue(), null);
        }
        scoresheet.submit();
        given(scoresheetRepository.findById(scoresheet.getId())).willReturn(Optional.of(scoresheet));
        given(judgingTableRepository.findById(tableId)).willReturn(Optional.of(table));
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
        var scoresheet = new Scoresheet(tableId, entryId);
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            scoresheet.updateScore(def.fieldName(), def.maxValue(), null);
        }
        scoresheet.submit();
        given(scoresheetRepository.findById(scoresheet.getId())).willReturn(Optional.of(scoresheet));
        given(judgingTableRepository.findById(tableId)).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(false);

        assertThatThrownBy(() -> service.revertToDraft(scoresheet.getId(), adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.auth.unauthorized");
    }

    @Test
    void shouldMoveScoresheetToNewTableWhenCategoryMatches() {
        var entryId = UUID.randomUUID();
        var scoresheet = new Scoresheet(tableId, entryId);
        var newTable = new JudgingTable(judging.getId(), "T2", divisionCategoryId, null);
        var entry = mockEntry(entryId, UUID.randomUUID());
        given(scoresheetRepository.findById(scoresheet.getId())).willReturn(Optional.of(scoresheet));
        given(judgingTableRepository.findById(newTable.getId())).willReturn(Optional.of(newTable));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(entryService.findEntryById(entryId)).willReturn(entry);
        given(scoresheetRepository.save(any(Scoresheet.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.moveToTable(scoresheet.getId(), newTable.getId(), adminUserId);

        assertThat(scoresheet.getTableId()).isEqualTo(newTable.getId());
    }

    @Test
    void shouldRejectMoveToTableWhenCategoryMismatch() {
        var entryId = UUID.randomUUID();
        var scoresheet = new Scoresheet(tableId, entryId);
        var differentCategory = UUID.randomUUID();
        var newTable = new JudgingTable(judging.getId(), "T2", differentCategory, null);
        var entry = mockEntry(entryId, UUID.randomUUID());
        given(scoresheetRepository.findById(scoresheet.getId())).willReturn(Optional.of(scoresheet));
        given(judgingTableRepository.findById(newTable.getId())).willReturn(Optional.of(newTable));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(entryService.findEntryById(entryId)).willReturn(entry);

        assertThatThrownBy(() -> service.moveToTable(scoresheet.getId(), newTable.getId(), adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.scoresheet.category-mismatch");
    }

    @Test
    void shouldSetCommentLanguageWhenInCompetitionList() {
        var entryId = UUID.randomUUID();
        var scoresheet = new Scoresheet(tableId, entryId);
        var competition = mock(Competition.class);
        var division = mock(app.meads.competition.Division.class);
        given(scoresheetRepository.findById(scoresheet.getId())).willReturn(Optional.of(scoresheet));
        given(judgingTableRepository.findById(tableId)).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(coiCheckService.check(judgeUserId, entryId)).willReturn(CoiResult.clear());
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(division.getStatus()).willReturn(DivisionStatus.JUDGING);
        given(division.getCompetitionId()).willReturn(competitionId);
        given(competitionService.findCompetitionById(competitionId)).willReturn(competition);
        given(competition.getCommentLanguages()).willReturn(Set.of("en", "pt"));
        given(judgeProfileService.findByUserId(judgeUserId)).willReturn(Optional.empty());
        given(scoresheetRepository.save(any(Scoresheet.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.setCommentLanguage(scoresheet.getId(), "pt", judgeUserId);

        assertThat(scoresheet.getCommentLanguage()).isEqualTo("pt");
        then(judgeProfileService).should().updatePreferredCommentLanguage(judgeUserId, "pt");
    }

    @Test
    void shouldRejectSetCommentLanguageWhenNotInAllowedList() {
        var entryId = UUID.randomUUID();
        var scoresheet = new Scoresheet(tableId, entryId);
        var competition = mock(Competition.class);
        var division = mock(app.meads.competition.Division.class);
        given(scoresheetRepository.findById(scoresheet.getId())).willReturn(Optional.of(scoresheet));
        given(judgingTableRepository.findById(tableId)).willReturn(Optional.of(table));
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(coiCheckService.check(judgeUserId, entryId)).willReturn(CoiResult.clear());
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(division.getStatus()).willReturn(DivisionStatus.JUDGING);
        given(division.getCompetitionId()).willReturn(competitionId);
        given(competitionService.findCompetitionById(competitionId)).willReturn(competition);
        given(competition.getCommentLanguages()).willReturn(Set.of("en"));
        given(judgeProfileService.findByUserId(judgeUserId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.setCommentLanguage(scoresheet.getId(), "pt", judgeUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.scoresheet.language-not-allowed");
    }
}
