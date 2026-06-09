package app.meads.judging.internal;

import java.util.List;

public final class MjpScoringFieldDefinition {

    public static final String APPEARANCE = "Appearance";
    public static final String AROMA_BOUQUET = "Aroma/Bouquet";
    public static final String FLAVOUR_AND_BODY = "Flavour and Body";
    public static final String FINISH = "Finish";
    public static final String OVERALL_IMPRESSION = "Overall Impression";

    /**
     * The six MJP rubric quality bands, in ascending order. Each carries a shared
     * i18n key for its display name; per-field score ranges + descriptions live on
     * the {@link FieldDefinition}.
     */
    public enum RubricBand {
        UNACCEPTABLE("unacceptable"),
        BELOW_AVERAGE("below-average"),
        AVERAGE("average"),
        VERY_GOOD("very-good"),
        EXCELLENT("excellent"),
        PERFECT("perfect");

        private final String slug;

        RubricBand(String slug) {
            this.slug = slug;
        }

        public String slug() {
            return slug;
        }

        public String nameKey() {
            return "scoresheet.rubric.band." + slug;
        }
    }

    /** A scored band for one field: the quality band and its inclusive score range. */
    public record Band(RubricBand band, int low, int high) {
    }

    public record FieldDefinition(String fieldName, String slug, int maxValue, List<Band> bands) {
        /** i18n key for this field's description of the given band. */
        public String descriptionKey(RubricBand band) {
            return "scoresheet.rubric.desc." + slug + "." + band.slug();
        }
    }

    private static Band band(RubricBand b, int low, int high) {
        return new Band(b, low, high);
    }

    public static final List<FieldDefinition> MJP_FIELDS = List.of(
            new FieldDefinition(APPEARANCE, "appearance", 12, List.of(
                    band(RubricBand.UNACCEPTABLE, 0, 2),
                    band(RubricBand.BELOW_AVERAGE, 3, 4),
                    band(RubricBand.AVERAGE, 5, 6),
                    band(RubricBand.VERY_GOOD, 7, 8),
                    band(RubricBand.EXCELLENT, 9, 10),
                    band(RubricBand.PERFECT, 11, 12))),
            new FieldDefinition(AROMA_BOUQUET, "aroma-bouquet", 30, List.of(
                    band(RubricBand.UNACCEPTABLE, 0, 5),
                    band(RubricBand.BELOW_AVERAGE, 6, 10),
                    band(RubricBand.AVERAGE, 11, 15),
                    band(RubricBand.VERY_GOOD, 16, 20),
                    band(RubricBand.EXCELLENT, 21, 25),
                    band(RubricBand.PERFECT, 26, 30))),
            new FieldDefinition(FLAVOUR_AND_BODY, "flavour-body", 32, List.of(
                    band(RubricBand.UNACCEPTABLE, 0, 5),
                    band(RubricBand.BELOW_AVERAGE, 6, 10),
                    band(RubricBand.AVERAGE, 11, 15),
                    band(RubricBand.VERY_GOOD, 16, 20),
                    band(RubricBand.EXCELLENT, 21, 26),
                    band(RubricBand.PERFECT, 27, 32))),
            new FieldDefinition(FINISH, "finish", 14, List.of(
                    band(RubricBand.UNACCEPTABLE, 0, 2),
                    band(RubricBand.BELOW_AVERAGE, 3, 4),
                    band(RubricBand.AVERAGE, 5, 6),
                    band(RubricBand.VERY_GOOD, 7, 8),
                    band(RubricBand.EXCELLENT, 9, 11),
                    band(RubricBand.PERFECT, 12, 14))),
            new FieldDefinition(OVERALL_IMPRESSION, "overall-impression", 12, List.of(
                    band(RubricBand.UNACCEPTABLE, 0, 2),
                    band(RubricBand.BELOW_AVERAGE, 3, 4),
                    band(RubricBand.AVERAGE, 5, 6),
                    band(RubricBand.VERY_GOOD, 7, 8),
                    band(RubricBand.EXCELLENT, 9, 10),
                    band(RubricBand.PERFECT, 11, 12)))
    );

    private MjpScoringFieldDefinition() {
    }
}
