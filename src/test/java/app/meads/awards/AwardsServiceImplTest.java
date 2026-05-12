package app.meads.awards;

import app.meads.BusinessRuleException;
import app.meads.awards.internal.AwardsServiceImpl;
import app.meads.awards.internal.PublicationRepository;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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

    private AwardsServiceImpl createService() {
        return new AwardsServiceImpl(publicationRepository, competitionService, eventPublisher);
    }
}
