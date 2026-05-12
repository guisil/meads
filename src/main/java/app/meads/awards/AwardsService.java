package app.meads.awards;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AwardsService {

    Publication publish(UUID divisionId, UUID adminUserId);

    Publication republish(UUID divisionId, String justification, UUID adminUserId);

    void sendAnnouncement(UUID divisionId, String customMessage, UUID adminUserId);

    Optional<Publication> getLatestPublication(UUID divisionId);

    List<Publication> getPublicationHistory(UUID divisionId);

    List<EntrantResultRow> getResultsForEntrant(UUID userId, UUID divisionId);

    AdminResultsView getResultsForAdmin(UUID divisionId, UUID adminUserId);

    PublicResultsView getPublicResults(String competitionShortName, String divisionShortName);

    AnonymizedScoresheetView getAnonymizedScoresheet(UUID scoresheetId, UUID requestingUserId);
}
