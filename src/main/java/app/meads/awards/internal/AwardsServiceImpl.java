package app.meads.awards.internal;

import app.meads.BusinessRuleException;
import app.meads.awards.AwardsService;
import app.meads.awards.Publication;
import app.meads.awards.ResultsPublishedEvent;
import app.meads.competition.CompetitionService;
import app.meads.competition.DivisionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

@Slf4j
@Service
@Transactional
@Validated
public class AwardsServiceImpl implements AwardsService {

    private final PublicationRepository publicationRepository;
    private final CompetitionService competitionService;
    private final ApplicationEventPublisher eventPublisher;

    public AwardsServiceImpl(PublicationRepository publicationRepository,
                             CompetitionService competitionService,
                             ApplicationEventPublisher eventPublisher) {
        this.publicationRepository = publicationRepository;
        this.competitionService = competitionService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Publication publish(@NotNull UUID divisionId, @NotNull UUID adminUserId) {
        if (!competitionService.isAuthorizedForDivision(divisionId, adminUserId)) {
            throw new BusinessRuleException("error.awards.unauthorized");
        }
        var division = competitionService.findDivisionById(divisionId);
        if (division.getStatus() != DivisionStatus.DELIBERATION) {
            throw new BusinessRuleException("error.awards.publish-wrong-status");
        }
        if (publicationRepository.existsByDivisionId(divisionId)) {
            throw new BusinessRuleException("error.awards.already-published");
        }
        var publication = publicationRepository.save(new Publication(divisionId, adminUserId));
        competitionService.advanceDivisionStatus(divisionId, adminUserId);
        eventPublisher.publishEvent(new ResultsPublishedEvent(
                divisionId, publication.getId(), publication.getVersion(),
                publication.getPublishedAt(), publication.getPublishedBy()));
        log.info("Published results for division {} (version {})", divisionId, publication.getVersion());
        return publication;
    }
}
