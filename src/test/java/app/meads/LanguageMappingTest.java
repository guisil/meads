package app.meads;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.*;

class LanguageMappingTest {

    @ParameterizedTest
    @CsvSource({
            "PT, pt",
            "BR, pt",
            "ES, es",
            "MX, es",
            "AR, es",
            "CL, es",
            "CO, es",
            "PE, es",
            "VE, es",
            "EC, es",
            "UY, es",
            "PY, es",
            "BO, es",
            "CU, es",
            "DO, es",
            "HN, es",
            "SV, es",
            "GT, es",
            "NI, es",
            "CR, es",
            "PA, es",
            "IT, it",
            "PL, pl"
    })
    void shouldMapCountryToSupportedLanguage(String country, String expectedLanguage) {
        assertThat(LanguageMapping.languageForCountry(country))
                .isEqualTo(Locale.of(expectedLanguage));
    }

    @Test
    void shouldDefaultToEnglishForUnmappedCountry() {
        assertThat(LanguageMapping.languageForCountry("US")).isEqualTo(Locale.ENGLISH);
        assertThat(LanguageMapping.languageForCountry("CH")).isEqualTo(Locale.ENGLISH);
        assertThat(LanguageMapping.languageForCountry("DE")).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void shouldDefaultToEnglishForNullCountry() {
        assertThat(LanguageMapping.languageForCountry(null)).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void shouldResolveLocaleFromSupportedPreferredLanguage() {
        assertThat(LanguageMapping.resolveLocale("en", null)).isEqualTo(Locale.ENGLISH);
        assertThat(LanguageMapping.resolveLocale("es", null)).isEqualTo(Locale.of("es"));
        assertThat(LanguageMapping.resolveLocale("it", null)).isEqualTo(Locale.ITALIAN);
        assertThat(LanguageMapping.resolveLocale("pl", null)).isEqualTo(Locale.of("pl"));
        assertThat(LanguageMapping.resolveLocale("pt", null)).isEqualTo(Locale.of("pt"));
    }

    @Test
    void shouldFallbackToEnglishForUnsupportedPreferredLanguage() {
        assertThat(LanguageMapping.resolveLocale("de", null)).isEqualTo(Locale.ENGLISH);
        assertThat(LanguageMapping.resolveLocale("fr", null)).isEqualTo(Locale.ENGLISH);
        assertThat(LanguageMapping.resolveLocale("ja", null)).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void shouldFallbackToCountryWhenNoPreferredLanguage() {
        assertThat(LanguageMapping.resolveLocale(null, "PT")).isEqualTo(Locale.of("pt"));
        assertThat(LanguageMapping.resolveLocale(null, "ES")).isEqualTo(Locale.of("es"));
        assertThat(LanguageMapping.resolveLocale(null, "IT")).isEqualTo(Locale.ITALIAN);
        assertThat(LanguageMapping.resolveLocale(null, "PL")).isEqualTo(Locale.of("pl"));
    }

    @Test
    void shouldFallbackToEnglishWhenNothingSet() {
        assertThat(LanguageMapping.resolveLocale(null, null)).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void shouldPreferExplicitLanguageOverCountry() {
        assertThat(LanguageMapping.resolveLocale("en", "PT")).isEqualTo(Locale.ENGLISH);
    }
}
