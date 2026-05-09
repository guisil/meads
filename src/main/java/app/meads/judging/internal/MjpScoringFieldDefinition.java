package app.meads.judging.internal;

import java.util.List;

public final class MjpScoringFieldDefinition {

    public static final String APPEARANCE = "Appearance";
    public static final String AROMA_BOUQUET = "Aroma/Bouquet";
    public static final String FLAVOUR_AND_BODY = "Flavour and Body";
    public static final String FINISH = "Finish";
    public static final String OVERALL_IMPRESSION = "Overall Impression";

    public record FieldDefinition(String fieldName, int maxValue) {}

    public static final List<FieldDefinition> MJP_FIELDS = List.of(
            new FieldDefinition(APPEARANCE, 12),
            new FieldDefinition(AROMA_BOUQUET, 30),
            new FieldDefinition(FLAVOUR_AND_BODY, 32),
            new FieldDefinition(FINISH, 14),
            new FieldDefinition(OVERALL_IMPRESSION, 12)
    );

    private MjpScoringFieldDefinition() {
    }
}
