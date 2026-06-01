package app.meads.judging;

import app.meads.judging.internal.MjpScoringFieldDefinition;
import app.meads.judging.internal.MjpScoringFieldDefinition.RubricBand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MjpScoringFieldDefinitionTest {

    @Test
    void shouldDefineFiveFieldsTotallingOneHundred() {
        var total = MjpScoringFieldDefinition.MJP_FIELDS.stream()
                .mapToInt(MjpScoringFieldDefinition.FieldDefinition::maxValue)
                .sum();
        assertThat(MjpScoringFieldDefinition.MJP_FIELDS).hasSize(5);
        assertThat(total).isEqualTo(100);
    }

    @Test
    void everyFieldShouldHaveSixBandsInCanonicalOrder() {
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            assertThat(def.bands())
                    .as("bands for %s", def.fieldName())
                    .extracting(MjpScoringFieldDefinition.Band::band)
                    .containsExactly(RubricBand.UNACCEPTABLE, RubricBand.BELOW_AVERAGE,
                            RubricBand.AVERAGE, RubricBand.VERY_GOOD,
                            RubricBand.EXCELLENT, RubricBand.PERFECT);
        }
    }

    @Test
    void bandsShouldContiguouslyCoverZeroToMaxForEachField() {
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            var bands = def.bands();
            assertThat(bands.get(0).low())
                    .as("first band low for %s", def.fieldName()).isZero();
            for (int i = 1; i < bands.size(); i++) {
                assertThat(bands.get(i).low())
                        .as("band %d low for %s", i, def.fieldName())
                        .isEqualTo(bands.get(i - 1).high() + 1);
            }
            assertThat(bands.get(bands.size() - 1).high())
                    .as("last band high for %s", def.fieldName())
                    .isEqualTo(def.maxValue());
        }
    }

    @Test
    void descriptionKeyShouldBeDerivedFromSlugAndBand() {
        var appearance = MjpScoringFieldDefinition.MJP_FIELDS.get(0);
        assertThat(appearance.slug()).isEqualTo("appearance");
        assertThat(appearance.descriptionKey(RubricBand.UNACCEPTABLE))
                .isEqualTo("scoresheet.rubric.desc.appearance.unacceptable");
        assertThat(RubricBand.VERY_GOOD.nameKey())
                .isEqualTo("scoresheet.rubric.band.very-good");
    }
}
