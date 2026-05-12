package app.meads.awards.internal;

import app.meads.BusinessRuleException;
import app.meads.awards.AwardsService;
import app.meads.awards.Publication;
import app.meads.awards.ResultsPublishedEvent;
import app.meads.awards.ResultsRepublishedEvent;
import app.meads.competition.CompetitionService;
import app.meads.competition.DivisionStatus;
import jakarta.validation.constraints.NotBlank;
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

    private static final int JUSTIFICATION_MIN_LENGTH = 20;
    private static final int JUSTIFICATION_MAX_LENGTH = 1000;

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

    @Override
    public Publication republish(@NotNull UUID divisionId,
                                  @NotBlank String justification,
                                  @NotNull UUID adminUserId) {
        if (!competitionService.isAuthorizedForDivision(divisionId, adminUserId)) {
            throw new BusinessRuleException("error.awards.unauthorized");
        }
        var division = competitionService.findDivisionById(divisionId);
        if (division.getStatus() != DivisionStatus.RESULTS_PUBLISHED) {
            throw new BusinessRuleException("error.awards.republish-wrong-status");
        }
        var trimmed = justification.trim();
        if (trimmed.length() < JUSTIFICATION_MIN_LENGTH) {
            throw new BusinessRuleException("error.awards.justification-too-short");
        }
        if (trimmed.length() > JUSTIFICATION_MAX_LENGTH) {
            throw new BusinessRuleException("error.awards.justification-too-long");
        }
        var previous = publicationRepository.findTopByDivisionIdOrderByVersionDesc(divisionId)
                .orElseThrow(() -> new BusinessRuleException("error.awards.no-prior-publication"));
        var publication = publicationRepository.save(
                Publication.republish(divisionId, previous.getVersion(), trimmed, adminUserId));
        eventPublisher.publishEvent(new ResultsRepublishedEvent(
                divisionId, publication.getId(), publication.getVersion(),
                publication.getPublishedAt(), publication.getPublishedBy(), trimmed));
        log.info("Republished results for division {} (version {})", divisionId, publication.getVersion());
        return publication;
    }
}
