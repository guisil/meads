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
            "BR, pt"
    })
    void shouldMapCountryToSupportedLanguage(String country, String expectedLanguage) {
        assertThat(LanguageMapping.languageForCountry(country))
                .isEqualTo(Locale.of(expectedLanguage));
    }

    @ParameterizedTest
    @CsvSource({
            "ES", "MX", "AR", "CL", "CO", "PE", "VE", "EC", "UY", "PY",
            "BO", "CU", "DO", "HN", "SV", "GT", "NI", "CR", "PA",
            "IT", "PL"
    })
    void shouldFallbackToEnglishForUnsupportedLanguageCountries(String country) {
        assertThat(LanguageMapping.languageForCountry(country)).isEqualTo(Locale.ENGLISH);
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
        assertThat(LanguageMapping.resolveLocale("pt", null)).isEqualTo(Locale.of("pt"));
        assertThat(LanguageMapping.resolveLocale("en", null)).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void shouldFallbackToEnglishForUnsupportedPreferredLanguage() {
        assertThat(LanguageMapping.resolveLocale("es", null)).isEqualTo(Locale.ENGLISH);
        assertThat(LanguageMapping.resolveLocale("pl", null)).isEqualTo(Locale.ENGLISH);
        assertThat(LanguageMapping.resolveLocale("it", null)).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void shouldFallbackToCountryWhenNoPreferredLanguage() {
        assertThat(LanguageMapping.resolveLocale(null, "PT")).isEqualTo(Locale.of("pt"));
        assertThat(LanguageMapping.resolveLocale(null, "ES")).isEqualTo(Locale.ENGLISH);
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
