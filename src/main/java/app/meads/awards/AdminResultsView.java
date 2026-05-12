package app.meads.awards;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminResultsView(
        UUID divisionId,
        String divisionName,
        String competitionName,
        String divisionStatusKey,
        List<AdminCategoryLeaderboard> categories,
        List<AdminBosRow> bosLeaderboard,
        List<PublicationSummary> publicationHistory) {

    public record AdminCategoryLeaderboard(
            UUID divisionCategoryId,
            String categoryCode,
            String categoryName,
            List<AdminEntryRow> rows) {
    }

    public record AdminEntryRow(
            UUID entryId,
            String entryNumber,
            String entrantName,
            String meaderyName,
            String meadName,
            Integer round1Total,
            boolean advancedToMedalRound,
            String medalLabel,
            Integer bosPlace) {
    }

    public record AdminBosRow(
            int place,
            UUID entryId,
            String entryNumber,
            String entrantName,
            String meaderyName,
            String meadName,
            String originatingCategoryCode) {
    }

    public record PublicationSummary(
            int version,
            Instant publishedAt,
            String publishedByDisplayName,
            String justification,
            boolean initial) {
    }
}
