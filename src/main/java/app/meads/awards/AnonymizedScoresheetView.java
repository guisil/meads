package app.meads.awards;

import java.util.List;
import java.util.UUID;

public record AnonymizedScoresheetView(
        UUID scoresheetId,
        UUID entryId,
        String entryNumber,
        String meadName,
        String categoryCode,
        String categoryName,
        List<AnonymizedScoresheet> scoresheets) {

    public record AnonymizedScoresheet(
            int judgeOrdinal,
            String commentLanguage,
            Integer totalScore,
            List<FieldScore> fieldScores,
            String overallComments) {
    }

    public record FieldScore(
            String fieldName,
            int value,
            int maxValue,
            String tierLabel) {
    }
}
