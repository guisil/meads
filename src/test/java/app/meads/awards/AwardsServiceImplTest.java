package app.meads.awards;

import app.meads.BusinessRuleException;
import app.meads.awards.internal.AwardsServiceImpl;
import app.meads.awards.internal.PublicationRepository;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionStatus;
import app.meads.entry.EntryService;
import app.meads.identity.UserService;
import app.meads.judging.JudgingService;
import app.meads.judging.ScoresheetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AwardsServiceImplTest {

    @Mock PublicationRepository publicationRepository;
    @Mock CompetitionService competitionService;
    @Mock EntryService entryService;
    @Mock JudgingService judgingService;
    @Mock ScoresheetService scoresheetService;
    @Mock UserService userService;
    @Mock ApplicationEventPublisher eventPublisher;

    @Test
    void shouldPublishInitialResults() {
        var service = createService();
        var divisionId = UUID.randomUUID();
        var adminUserId = UUID.randomUUID();
        var division = mock(Division.class);
        given(division.getStatus()).willReturn(DivisionStatus.DELIBERATION);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(publicationRepository.existsByDivisionId(divisionId)).willReturn(false);
        given(publicationRepository.save(any(Publication.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var publication = service.publish(divisionId, adminUserId);

        assertThat(publication.getVersion()).isEqualTo(1);
        assertThat(publication.isInitial()).isTrue();
        then(competitionService).should().advanceDivisionStatus(divisionId, adminUserId);
        var captor = ArgumentCaptor.forClass(ResultsPublishedEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().divisionId()).isEqualTo(divisionId);
        assertThat(captor.getValue().version()).isEqualTo(1);
    }

    @Test
    void shouldRejectPublishWhenStatusNotDeliberation() {
        var service = createService();
        var divisionId = UUID.randomUUID();
        var adminUserId = UUID.randomUUID();
        var division = mock(Division.class);
        given(division.getStatus()).willReturn(DivisionStatus.JUDGING);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);

        assertThatThrownBy(() -> service.publish(divisionId, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.awards.publish-wrong-status");
    }

    @Test
    void shouldRejectPublishWhenUnauthorized() {
        var service = createService();
        var divisionId = UUID.randomUUID();
        var adminUserId = UUID.randomUUID();
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(false);

        assertThatThrownBy(() -> service.publish(divisionId, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.awards.unauthorized");
    }

    @Test
    void shouldRejectPublishWhenAlreadyPublished() {
        var service = createService();
        var divisionId = UUID.randomUUID();
        var adminUserId = UUID.randomUUID();
        var division = mock(Division.class);
        given(division.getStatus()).willReturn(DivisionStatus.DELIBERATION);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        given(publicationRepository.existsByDivisionId(divisionId)).willReturn(true);

        assertThatThrownBy(() -> service.publish(divisionId, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.awards.already-published");
    }

    @Test
    void shouldRepublishWithIncrementedVersion() {
        var service = createService();
        var divisionId = UUID.randomUUID();
        var adminUserId = UUID.randomUUID();
        var division = mock(Division.class);
        given(division.getStatus()).willReturn(DivisionStatus.RESULTS_PUBLISHED);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);
        var existing = new Publication(divisionId, adminUserId);
        given(publicationRepository.findTopByDivisionIdOrderByVersionDesc(divisionId))
                .willReturn(Optional.of(existing));
        given(publicationRepository.save(any(Publication.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var publication = service.republish(divisionId,
                "Fixed gold medal in M1A — judge re-scored after spreadsheet error.", adminUserId);

        assertThat(publication.getVersion()).isEqualTo(2);
        assertThat(publication.isInitial()).isFalse();
        assertThat(publication.getJustification()).contains("Fixed gold medal");

        var captor = ArgumentCaptor.forClass(ResultsRepublishedEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().version()).isEqualTo(2);
    }

    @Test
    void shouldRejectRepublishWhenStatusNotPublished() {
        var service = createService();
        var divisionId = UUID.randomUUID();
        var adminUserId = UUID.randomUUID();
        var division = mock(Division.class);
        given(division.getStatus()).willReturn(DivisionStatus.DELIBERATION);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);

        assertThatThrownBy(() -> service.republish(divisionId, "valid justification text here", adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.awards.republish-wrong-status");
    }

    @Test
    void shouldRejectRepublishWithJustificationTooShort() {
        var service = createService();
        var divisionId = UUID.randomUUID();
        var adminUserId = UUID.randomUUID();
        var division = mock(Division.class);
        given(division.getStatus()).willReturn(DivisionStatus.RESULTS_PUBLISHED);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);

        assertThatThrownBy(() -> service.republish(divisionId, "short", adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.awards.justification-too-short");
    }

    @Test
    void shouldRejectRepublishWithJustificationTooLong() {
        var service = createService();
        var divisionId = UUID.randomUUID();
        var adminUserId = UUID.randomUUID();
        var division = mock(Division.class);
        given(division.getStatus()).willReturn(DivisionStatus.RESULTS_PUBLISHED);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(true);

        var tooLong = "x".repeat(1001);
        assertThatThrownBy(() -> service.republish(divisionId, tooLong, adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.awards.justification-too-long");
    }

    @Test
    void shouldReturnEmptyOptionalWhenNoPublication() {
        var service = createService();
        var divisionId = UUID.randomUUID();
        given(publicationRepository.findTopByDivisionIdOrderByVersionDesc(divisionId))
                .willReturn(Optional.empty());

        assertThat(service.getLatestPublication(divisionId)).isEmpty();
    }

    @Test
    void shouldReturnLatestPublicationWhenPresent() {
        var service = createService();
        var divisionId = UUID.randomUUID();
        var publication = new Publication(divisionId, UUID.randomUUID());
        given(publicationRepository.findTopByDivisionIdOrderByVersionDesc(divisionId))
                .willReturn(Optional.of(publication));

        assertThat(service.getLatestPublication(divisionId)).contains(publication);
    }

    @Test
    void shouldReturnPublicationHistoryOrderedByVersion() {
        var service = createService();
        var divisionId = UUID.randomUUID();
        var p1 = new Publication(divisionId, UUID.randomUUID());
        var p2 = Publication.republish(divisionId, 1, "first revision applied to gold list", UUID.randomUUID());
        given(publicationRepository.findByDivisionIdOrderByVersionAsc(divisionId))
                .willReturn(java.util.List.of(p1, p2));

        var history = service.getPublicationHistory(divisionId);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getVersion()).isEqualTo(1);
        assertThat(history.get(1).getVersion()).isEqualTo(2);
    }

    @Test
    void shouldRejectGetResultsForEntrantWhenDivisionNotPublished() {
        var service = createService();
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var division = mock(Division.class);
        given(division.getStatus()).willReturn(DivisionStatus.DELIBERATION);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);

        assertThatThrownBy(() -> service.getResultsForEntrant(userId, divisionId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.awards.not-published");
    }

    @Test
    void shouldRejectGetPublicResultsWhenNotPublished() {
        var service = createService();
        var competitionId = UUID.randomUUID();
        var competition = mock(app.meads.competition.Competition.class);
        given(competition.getId()).willReturn(competitionId);
        var division = mock(Division.class);
        given(division.getStatus()).willReturn(DivisionStatus.JUDGING);
        given(competitionService.findCompetitionByShortName("test")).willReturn(competition);
        given(competitionService.findDivisionByShortName(competitionId, "amateur"))
                .willReturn(division);

        assertThatThrownBy(() -> service.getPublicResults("test", "amateur"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.awards.not-published");
    }

    @Test
    void shouldRejectGetAnonymizedScoresheetWhenScoresheetNotFound() {
        var service = createService();
        var sheetId = UUID.randomUUID();
        given(scoresheetService.findById(sheetId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAnonymizedScoresheet(sheetId, UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.awards.scoresheet-not-found");
    }

    @Test
    void shouldRejectGetAnonymizedScoresheetWhenNotAdminAndNotOwner() {
        var service = createService();
        var sheetId = UUID.randomUUID();
        var entryId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var requestingUserId = UUID.randomUUID();
        var ownerUserId = UUID.randomUUID();
        var sheet = mock(app.meads.judging.Scoresheet.class);
        given(sheet.getStatus()).willReturn(app.meads.judging.ScoresheetStatus.SUBMITTED);
        given(sheet.getEntryId()).willReturn(entryId);
        given(scoresheetService.findById(sheetId)).willReturn(Optional.of(sheet));
        var entry = mock(app.meads.entry.Entry.class);
        given(entry.getDivisionId()).willReturn(divisionId);
        given(entry.getUserId()).willReturn(ownerUserId);
        given(entryService.findEntryById(entryId)).willReturn(entry);
        given(competitionService.isAuthorizedForDivision(divisionId, requestingUserId)).willReturn(false);

        assertThatThrownBy(() -> service.getAnonymizedScoresheet(sheetId, requestingUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.awards.unauthorized");
    }

    @Test
    void shouldRejectRepublishWhenUnauthorized() {
        var service = createService();
        var divisionId = UUID.randomUUID();
        var adminUserId = UUID.randomUUID();
        given(competitionService.isAuthorizedForDivision(divisionId, adminUserId)).willReturn(false);

        assertThatThrownBy(() -> service.republish(divisionId, "valid justification text here", adminUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.awards.unauthorized");
    }

    private AwardsServiceImpl createService() {
        return new AwardsServiceImpl(publicationRepository, competitionService,
                entryService, judgingService, scoresheetService, userService, eventPublisher);
    }
}
