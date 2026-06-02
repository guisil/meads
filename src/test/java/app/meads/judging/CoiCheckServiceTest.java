package app.meads.judging;

import app.meads.BusinessRuleException;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.entry.Entry;
import app.meads.entry.EntryService;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserService;
import app.meads.identity.UserStatus;
import app.meads.judging.internal.CoiCheckServiceImpl;
import app.meads.judging.internal.ManualCoi;
import app.meads.judging.internal.ManualCoiRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CoiCheckServiceTest {

    @InjectMocks
    CoiCheckServiceImpl service;

    @Mock
    UserService userService;

    @Mock
    EntryService entryService;

    @Mock
    CompetitionService competitionService;

    @Mock
    ManualCoiRepository manualCoiRepository;

    UUID judgeUserId;
    UUID entrantUserId;
    UUID entryId;
    UUID divisionId;
    UUID competitionId;
    User judge;
    User entrant;
    Entry entry;
    Division division;

    @BeforeEach
    void setUp() {
        judgeUserId = UUID.randomUUID();
        entrantUserId = UUID.randomUUID();
        entryId = UUID.randomUUID();
        divisionId = UUID.randomUUID();
        competitionId = UUID.randomUUID();
        judge = new User("judge@test.com", "Judge", UserStatus.ACTIVE, Role.USER);
        entrant = new User("entrant@test.com", "Entrant", UserStatus.ACTIVE, Role.USER);
        entry = org.mockito.Mockito.mock(Entry.class);
        division = org.mockito.Mockito.mock(Division.class);
    }

    /** Stubs the entry-owner-to-competition resolution used by the manual-COI lookup. */
    private void stubCompetitionResolution() {
        given(entry.getDivisionId()).willReturn(divisionId);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(division.getCompetitionId()).willReturn(competitionId);
    }

    @Test
    void shouldReturnHardBlockWhenJudgeIsTheEntrant() {
        given(entry.getUserId()).willReturn(judgeUserId);
        given(entryService.findEntryById(entryId)).willReturn(entry);

        var result = service.check(judgeUserId, entryId);

        assertThat(result.hardBlock()).isTrue();
        assertThat(result.softWarningKey()).isEmpty();
    }

    @Test
    void shouldReturnHardBlockWhenManualCoiDeclared() {
        given(entry.getUserId()).willReturn(entrantUserId);
        given(entryService.findEntryById(entryId)).willReturn(entry);
        stubCompetitionResolution();
        given(manualCoiRepository.existsByCompetitionIdAndJudgeUserIdAndEntrantUserId(
                competitionId, judgeUserId, entrantUserId)).willReturn(true);

        var result = service.check(judgeUserId, entryId);

        assertThat(result.hardBlock()).isTrue();
        assertThat(result.softWarningKey()).isEmpty();
    }

    @Test
    void shouldReturnSoftWarningWhenMeaderyNamesAreSimilar() {
        given(entry.getUserId()).willReturn(entrantUserId);
        given(entryService.findEntryById(entryId)).willReturn(entry);
        stubCompetitionResolution();
        judge.updateMeaderyName("Acme Meadery LLC");
        judge.updateCountry("US");
        entrant.updateMeaderyName("Acme Meads Co.");
        entrant.updateCountry("US");
        given(userService.findById(judgeUserId)).willReturn(judge);
        given(userService.findById(entrantUserId)).willReturn(entrant);

        var result = service.check(judgeUserId, entryId);

        assertThat(result.hardBlock()).isFalse();
        assertThat(result.softWarningKey()).contains("coi.warning.similar-meadery");
    }

    @Test
    void shouldReturnNoWarningWhenMeaderyNamesAreUnrelated() {
        given(entry.getUserId()).willReturn(entrantUserId);
        given(entryService.findEntryById(entryId)).willReturn(entry);
        stubCompetitionResolution();
        judge.updateMeaderyName("Honey Hill Meadery");
        judge.updateCountry("US");
        entrant.updateMeaderyName("Bear Mountain Mead");
        entrant.updateCountry("US");
        given(userService.findById(judgeUserId)).willReturn(judge);
        given(userService.findById(entrantUserId)).willReturn(entrant);

        var result = service.check(judgeUserId, entryId);

        assertThat(result.hardBlock()).isFalse();
        assertThat(result.softWarningKey()).isEmpty();
    }

    @Test
    void shouldAddManualCoi() {
        given(competitionService.isAuthorizedForCompetition(competitionId, judgeUserId)).willReturn(true);
        given(manualCoiRepository.existsByCompetitionIdAndJudgeUserIdAndEntrantUserId(
                competitionId, judgeUserId, entrantUserId)).willReturn(false);

        service.addManualCoi(competitionId, judgeUserId, entrantUserId, judgeUserId);

        var captor = ArgumentCaptor.forClass(ManualCoi.class);
        verify(manualCoiRepository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getCompetitionId()).isEqualTo(competitionId);
        assertThat(saved.getJudgeUserId()).isEqualTo(judgeUserId);
        assertThat(saved.getEntrantUserId()).isEqualTo(entrantUserId);
        assertThat(saved.getCreatedBy()).isEqualTo(judgeUserId);
    }

    @Test
    void shouldRejectAddManualCoiWhenAdminNotAuthorized() {
        given(competitionService.isAuthorizedForCompetition(competitionId, judgeUserId)).willReturn(false);

        assertThatThrownBy(() -> service.addManualCoi(competitionId, judgeUserId, entrantUserId, judgeUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("error.coi.manual.not-authorized");
        verify(manualCoiRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectAddManualCoiBetweenSameUser() {
        given(competitionService.isAuthorizedForCompetition(competitionId, judgeUserId)).willReturn(true);

        assertThatThrownBy(() -> service.addManualCoi(competitionId, judgeUserId, judgeUserId, judgeUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("error.coi.manual.same-user");
        verify(manualCoiRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectDuplicateManualCoi() {
        given(competitionService.isAuthorizedForCompetition(competitionId, judgeUserId)).willReturn(true);
        given(manualCoiRepository.existsByCompetitionIdAndJudgeUserIdAndEntrantUserId(
                competitionId, judgeUserId, entrantUserId)).willReturn(true);

        assertThatThrownBy(() -> service.addManualCoi(competitionId, judgeUserId, entrantUserId, judgeUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("error.coi.manual.duplicate");
        verify(manualCoiRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRemoveManualCoi() {
        var coi = new ManualCoi(competitionId, judgeUserId, entrantUserId, judgeUserId);
        given(manualCoiRepository.findById(coi.getId())).willReturn(java.util.Optional.of(coi));
        given(competitionService.isAuthorizedForCompetition(competitionId, judgeUserId)).willReturn(true);

        service.removeManualCoi(coi.getId(), judgeUserId);

        verify(manualCoiRepository).delete(coi);
    }

    @Test
    void shouldRejectRemoveManualCoiWhenAdminNotAuthorized() {
        var coi = new ManualCoi(competitionId, judgeUserId, entrantUserId, judgeUserId);
        given(manualCoiRepository.findById(coi.getId())).willReturn(java.util.Optional.of(coi));
        given(competitionService.isAuthorizedForCompetition(competitionId, judgeUserId)).willReturn(false);

        assertThatThrownBy(() -> service.removeManualCoi(coi.getId(), judgeUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("error.coi.manual.not-authorized");
        verify(manualCoiRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldListManualCoisWithNamesAndEmails() {
        var coi = new ManualCoi(competitionId, judgeUserId, entrantUserId, judgeUserId);
        given(manualCoiRepository.findByCompetitionId(competitionId)).willReturn(List.of(coi));
        given(userService.findById(judgeUserId)).willReturn(judge);
        given(userService.findById(entrantUserId)).willReturn(entrant);

        List<ManualCoiView> views = service.findManualCois(competitionId);

        assertThat(views).hasSize(1);
        var view = views.getFirst();
        assertThat(view.id()).isEqualTo(coi.getId());
        assertThat(view.judgeUserId()).isEqualTo(judgeUserId);
        assertThat(view.judgeName()).isEqualTo("Judge");
        assertThat(view.judgeEmail()).isEqualTo("judge@test.com");
        assertThat(view.entrantUserId()).isEqualTo(entrantUserId);
        assertThat(view.entrantName()).isEqualTo("Entrant");
        assertThat(view.entrantEmail()).isEqualTo("entrant@test.com");
    }

    @Test
    void shouldReturnNoWarningWhenJudgeMeaderyIsBlank() {
        given(entry.getUserId()).willReturn(entrantUserId);
        given(entryService.findEntryById(entryId)).willReturn(entry);
        stubCompetitionResolution();
        entrant.updateMeaderyName("Honey Hill Meadery");
        entrant.updateCountry("US");
        given(userService.findById(judgeUserId)).willReturn(judge);
        given(userService.findById(entrantUserId)).willReturn(entrant);

        var result = service.check(judgeUserId, entryId);

        assertThat(result.hardBlock()).isFalse();
        assertThat(result.softWarningKey()).isEmpty();
    }
}
