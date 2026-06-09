package app.meads.awards;

import app.meads.entry.Carbonation;
import app.meads.entry.Strength;
import app.meads.entry.Sweetness;
import app.meads.judging.Medal;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AnonymizedScoresheetView(
        UUID scoresheetId,
        UUID entryId,
        String entryNumber,
        String meadName,
        String categoryCode,
        String categoryName,
        String competitionLogoDataUri,
        MeadDetails meadDetails,
        Medal medal,
        Integer bosPlace,
        List<AnonymizedScoresheet> scoresheets) {

    /** Full mead characteristics, shown to the owning entrant on their scoresheet. */
    public record MeadDetails(
            Sweetness sweetness,
            Strength strength,
            BigDecimal abv,
            Carbonation carbonation,
            String honeyVarieties,
            String otherIngredients,
            boolean woodAged,
            String woodAgeingDetails,
            String additionalInformation) {
    }

    public record AnonymizedScoresheet(
            int judgeOrdinal,
            String commentLanguage,
            Integer totalScore,
            boolean advanced,
            List<FieldScore> fieldScores,
            String overallComments) {
    }

    public record FieldScore(
            String fieldName,
            int value,
            int maxValue,
            String comment) {
    }
}
