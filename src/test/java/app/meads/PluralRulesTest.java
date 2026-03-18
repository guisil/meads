package app.meads;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.*;

class PluralRulesTest {

    // --- English (simple: one / other) ---

    @ParameterizedTest
    @CsvSource({"1, one", "0, other", "2, other", "5, other", "10, other", "21, other", "100, other"})
    void shouldReturnCorrectCategoryForEnglish(int count, String expected) {
        assertThat(PluralRules.getCategory(count, Locale.ENGLISH)).isEqualTo(expected);
    }

    // --- Portuguese (simple: one / other) ---

    @ParameterizedTest
    @CsvSource({"1, one", "0, other", "2, other", "5, other"})
    void shouldReturnCorrectCategoryForPortuguese(int count, String expected) {
        assertThat(PluralRules.getCategory(count, Locale.of("pt"))).isEqualTo(expected);
    }

    // --- Spanish (simple: one / other) ---

    @ParameterizedTest
    @CsvSource({"1, one", "0, other", "2, other", "5, other"})
    void shouldReturnCorrectCategoryForSpanish(int count, String expected) {
        assertThat(PluralRules.getCategory(count, Locale.of("es"))).isEqualTo(expected);
    }

    // --- Italian (simple: one / other) ---

    @ParameterizedTest
    @CsvSource({"1, one", "0, other", "2, other", "5, other"})
    void shouldReturnCorrectCategoryForItalian(int count, String expected) {
        assertThat(PluralRules.getCategory(count, Locale.ITALIAN)).isEqualTo(expected);
    }

    // --- Polish (complex: one / few / many) ---

    @ParameterizedTest
    @CsvSource({
            "1, one",
            "0, many",
            "2, few", "3, few", "4, few",
            "5, many", "6, many", "7, many", "8, many", "9, many",
            "10, many", "11, many", "12, many", "13, many", "14, many",
            "15, many", "16, many", "17, many", "18, many", "19, many",
            "20, many", "21, many",
            "22, few", "23, few", "24, few",
            "25, many", "30, many", "31, many",
            "32, few", "33, few", "34, few",
            "35, many", "40, many", "42, few", "45, many",
            "100, many", "101, many", "102, few", "105, many",
            "111, many", "112, many", "114, many",
            "122, few", "123, few", "124, few"
    })
    void shouldReturnCorrectCategoryForPolish(int count, String expected) {
        assertThat(PluralRules.getCategory(count, Locale.of("pl"))).isEqualTo(expected);
    }

    // --- Unknown locale falls back to simple one/other ---

    @Test
    void shouldFallBackToSimpleRulesForUnknownLocale() {
        assertThat(PluralRules.getCategory(1, Locale.JAPANESE)).isEqualTo("one");
        assertThat(PluralRules.getCategory(2, Locale.JAPANESE)).isEqualTo("other");
    }
}
