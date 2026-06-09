package app.meads.awards;

import java.time.Instant;
import java.util.List;

public record PublicResultsView(
        String competitionName,
        String divisionName,
        Instant lastUpdatedAt,
        boolean hasMultiplePublications,
        List<PublicCategorySection> categories,
        List<PublicBosRow> bosLeaderboard,
        boolean meaderyRequired) {

    public record PublicCategorySection(
            String categoryCode,
            String categoryName,
            List<PublicMedalRow> golds,
            List<PublicMedalRow> silvers,
            List<PublicMedalRow> bronzes) {
    }

    /** {@code producer} = meadery name in meadery-required divisions, else "Maker (Country)". */
    public record PublicMedalRow(String meadName, String producer) {
    }

    public record PublicBosRow(int place, String meadName, String producer) {
    }
}
