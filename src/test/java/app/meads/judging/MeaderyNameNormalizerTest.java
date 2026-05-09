package app.meads.judging;

import app.meads.judging.internal.MeaderyNameNormalizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MeaderyNameNormalizerTest {

    @Test
    void shouldLowercaseAndStripGlobalSuffixes() {
        assertThat(MeaderyNameNormalizer.normalize("Acme Meadery LLC", null))
                .isEqualTo("acme");
        assertThat(MeaderyNameNormalizer.normalize("Acme Meads Co.", "US"))
                .isEqualTo("acme");
        assertThat(MeaderyNameNormalizer.normalize("Honey Cellars", null))
                .isEqualTo("honey");
    }

    @Test
    void shouldStripPortugueseSuffixes() {
        assertThat(MeaderyNameNormalizer.normalize("Hidromelaria do Sul, Lda.", "PT"))
                .isEqualTo("do sul");
        assertThat(MeaderyNameNormalizer.normalize("Casa do Mel SA", "PT"))
                .isEqualTo("casa do mel");
    }

    @Test
    void shouldStripItalianSuffixes() {
        assertThat(MeaderyNameNormalizer.normalize("Idromeleria Toscana SRL", "IT"))
                .isEqualTo("toscana");
    }

    @Test
    void shouldReplaceNonAlphanumericWithSpaceAndCollapseWhitespace() {
        assertThat(MeaderyNameNormalizer.normalize("Honey-Hill, Inc.", null))
                .isEqualTo("honey hill");
        assertThat(MeaderyNameNormalizer.normalize("  Honey   Hill  ", null))
                .isEqualTo("honey hill");
    }

    @Test
    void shouldReturnEmptyForNullOrBlank() {
        assertThat(MeaderyNameNormalizer.normalize(null, null)).isEmpty();
        assertThat(MeaderyNameNormalizer.normalize("", null)).isEmpty();
        assertThat(MeaderyNameNormalizer.normalize("   ", null)).isEmpty();
    }

    @Test
    void shouldFlagSimilarWhenExactNormalizedMatch() {
        assertThat(MeaderyNameNormalizer.areSimilar(
                "Acme Meadery LLC", "US", "Acme Meads Co.", "US")).isTrue();
    }

    @Test
    void shouldFlagSimilarWhenLevenshteinDistanceTwoOrLess() {
        // distance 1 (typo)
        assertThat(MeaderyNameNormalizer.areSimilar(
                "Honey Hill Meadery", "US", "Honey Hil Meadery", "US")).isTrue();
        // distance 2
        assertThat(MeaderyNameNormalizer.areSimilar(
                "Honey Hill", null, "Honey Hilly", null)).isTrue();
    }

    @Test
    void shouldNotFlagWhenLevenshteinDistanceGreaterThanTwo() {
        assertThat(MeaderyNameNormalizer.areSimilar(
                "Honey Hill Meadery", "US", "Bear Mountain Mead", "US")).isFalse();
    }

    @Test
    void shouldSkipWhenCountriesDiffer() {
        // Same normalized form but different countries → skip
        assertThat(MeaderyNameNormalizer.areSimilar(
                "Acme Meadery", "US", "Acme Hidromelaria", "PT")).isFalse();
    }

    @Test
    void shouldCompareWhenOneCountryIsNull() {
        // Cross-country gate only fires when BOTH are set; if either is null, compare
        assertThat(MeaderyNameNormalizer.areSimilar(
                "Casa do Mel Lda", null, "Casa do Mel", "PT")).isTrue();
    }

    @Test
    void shouldNotFlagWhenNamesAreNullOrBlank() {
        assertThat(MeaderyNameNormalizer.areSimilar(null, "US", "Honey Hill", "US")).isFalse();
        assertThat(MeaderyNameNormalizer.areSimilar("", "US", "Honey Hill", "US")).isFalse();
        assertThat(MeaderyNameNormalizer.areSimilar("Honey Hill", "US", "  ", "US")).isFalse();
    }

    @Test
    void shouldUseGlobalFallbackForUnsupportedCountries() {
        // ZZ is not in the suffix map; should still strip global suffixes
        assertThat(MeaderyNameNormalizer.normalize("Acme Meadery", "ZZ"))
                .isEqualTo("acme");
    }

    @Test
    void shouldHandleDiacriticsViaLevenshtein() {
        assertThat(MeaderyNameNormalizer.areSimilar(
                "Casa do Mel", "PT", "Casa do Mél", "PT")).isTrue();
    }
}
