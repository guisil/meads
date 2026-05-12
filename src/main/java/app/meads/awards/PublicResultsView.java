package app.meads.awards;

import java.time.Instant;
import java.util.List;

public record PublicResultsView(
        String competitionName,
        String divisionName,
        Instant lastUpdatedAt,
        boolean hasMultiplePublications,
        List<PublicCategorySection> categories,
        List<PublicBosRow> bosLeaderboard) {

    public record PublicCategorySection(
            String categoryCode,
            String categoryName,
            List<PublicMedalRow> golds,
            List<PublicMedalRow> silvers,
            List<PublicMedalRow> bronzes) {
    }

    public record PublicMedalRow(String meadName, String meaderyName) {
    }

    public record PublicBosRow(int place, String meadName, String meaderyName) {
    }
}
