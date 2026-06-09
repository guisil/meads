package app.meads.awards;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public interface AwardsService {

    Publication publish(@NotNull UUID divisionId, @NotNull UUID adminUserId);

    Publication republish(@NotNull UUID divisionId, @NotBlank String justification, @NotNull UUID adminUserId);

    void sendAnnouncement(@NotNull UUID divisionId, String customMessage, @NotNull UUID adminUserId);

    Optional<Publication> getLatestPublication(@NotNull UUID divisionId);

    List<Publication> getPublicationHistory(@NotNull UUID divisionId);

    List<EntrantResultRow> getResultsForEntrant(@NotNull UUID userId, @NotNull UUID divisionId);

    AdminResultsView getResultsForAdmin(@NotNull UUID divisionId, @NotNull UUID adminUserId);

    PublicResultsView getPublicResults(@NotBlank String competitionShortName, @NotBlank String divisionShortName,
                                       @NotNull Locale locale);

    /**
     * Admin-only preview of the public results page before publishing — available
     * from DELIBERATION onward (results data exists once judging is COMPLETE).
     * Authorizes {@code adminUserId} for the division.
     */
    PublicResultsView getResultsPreview(@NotBlank String competitionShortName, @NotBlank String divisionShortName,
                                        @NotNull Locale locale, @NotNull UUID adminUserId);

    AnonymizedScoresheetView getAnonymizedScoresheet(@NotNull UUID scoresheetId, @NotNull UUID requestingUserId);
}
