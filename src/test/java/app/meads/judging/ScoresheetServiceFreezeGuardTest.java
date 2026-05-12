package app.meads.judging;

import app.meads.BusinessRuleException;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionStatus;
import app.meads.entry.Entry;
import app.meads.entry.EntryService;
import app.meads.judging.internal.CategoryJudgingConfigRepository;
import app.meads.judging.internal.JudgingRepository;
import app.meads.judging.internal.JudgingTableRepository;
import app.meads.judging.internal.ScoresheetRepository;
import app.meads.judging.internal.ScoresheetServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScoresheetServiceFreezeGuardTest {

    @InjectMocks ScoresheetServiceImpl service;

    @Mock ScoresheetRepository scoresheetRepository;
    @Mock JudgingTableRepository judgingTableRepository;
    @Mock CategoryJudgingConfigRepository categoryConfigRepository;
    @Mock JudgingRepository judgingRepository;
    @Mock EntryService entryService;
    @Mock CompetitionService competitionService;
    @Mock JudgeProfileService judgeProfileService;
    @Mock CoiCheckService coiCheckService;
    @Mock ApplicationEventPublisher eventPublisher;

    UUID divisionId;
    UUID adminUserId;
    UUID judgeUserId;
    UUID scoresheetId;
    UUID tableId;
    UUID entryId;
    UUID divisionCategoryId;
    Judging judging;
    JudgingTable table;
    Scoresheet sheet;

    @BeforeEach
    void setUp() {
        divisionId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();
        judgeUserId = UUID.randomUUID();
        entryId = UUID.randomUUID();
        divisionCategoryId = UUID.randomUUID();
        var frozenDivision = mock(Division.class);
        given(frozenDivision.getStatus()).willReturn(DivisionStatus.RESULTS_PUBLISHED);
        given(competitionService.findDivisionById(divisionId)).willReturn(frozenDivision);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        judging = new Judging(divisionId);
        given(judgingRepository.findById(judging.getId())).willReturn(Optional.of(judging));
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        table = new JudgingTable(judging.getId(), "T1", divisionCategoryId, null);
        tableId = table.getId();
        given(judgingTableRepository.findById(tableId)).willReturn(Optional.of(table));
        sheet = new Scoresheet(tableId, entryId);
        scoresheetId = sheet.getId();
        given(scoresheetRepository.findById(scoresheetId)).willReturn(Optional.of(sheet));
    }

    private void assertFrozen(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.judging.results-published-frozen");
    }

    @Test
    void shouldRejectCreateScoresheetsForTableWhenResultsPublished() {
        assertFrozen(() -> service.createScoresheetsForTable(tableId));
    }

    @Test
    void shouldRejectEnsureScoresheetForEntryWhenResultsPublished() {
        given(scoresheetRepository.findByEntryId(entryId)).willReturn(Optional.empty());
        var entry = mock(Entry.class);
        given(entry.getDivisionId()).willReturn(divisionId);
        given(entry.getFinalCategoryId()).willReturn(divisionCategoryId);
        given(entryService.findEntryById(entryId)).willReturn(entry);
        assertFrozen(() -> service.ensureScoresheetForEntry(entryId));
    }

    @Test
    void shouldRejectUpdateScoreWhenResultsPublished() {
        assertFrozen(() -> service.updateScore(scoresheetId, "BOUQUET", 5, "ok", judgeUserId));
    }

    @Test
    void shouldRejectUpdateOverallCommentsWhenResultsPublished() {
        assertFrozen(() -> service.updateOverallComments(scoresheetId, "comments", judgeUserId));
    }

    @Test
    void shouldRejectSetAdvancedToMedalRoundWhenResultsPublished() {
        assertFrozen(() -> service.setAdvancedToMedalRound(scoresheetId, true, judgeUserId));
    }

    @Test
    void shouldRejectSetCommentLanguageWhenResultsPublished() {
        assertFrozen(() -> service.setCommentLanguage(scoresheetId, "en", judgeUserId));
    }

    @Test
    void shouldRejectSubmitWhenResultsPublished() {
        assertFrozen(() -> service.submit(scoresheetId, judgeUserId));
    }

    @Test
    void shouldRejectRevertToDraftWhenResultsPublished() {
        assertFrozen(() -> service.revertToDraft(scoresheetId, adminUserId));
    }

    @Test
    void shouldRejectMoveToTableWhenResultsPublished() {
        var newTableId = UUID.randomUUID();
        var newTable = new JudgingTable(judging.getId(), "T2", divisionCategoryId, null);
        given(judgingTableRepository.findById(newTableId)).willReturn(Optional.of(newTable));
        assertFrozen(() -> service.moveToTable(scoresheetId, newTableId, adminUserId));
    }
}
