package app.meads.competition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CategoryDisplayTest {

    private static final Locale PT = Locale.forLanguageTag("pt");

    private DivisionCategory catalogCategory() {
        // Standard catalog category: English base name, no per-category translation rows.
        return new DivisionCategory(java.util.UUID.randomUUID(), null, "M1",
                "Traditional Mead", "Desc", null, 1, CategoryScope.JUDGING);
    }

    private DivisionCategory customCategory() {
        var cat = new DivisionCategory(java.util.UUID.randomUUID(), null, "X9Z",
                "House Special", "Desc", null, 1, CategoryScope.JUDGING);
        cat.setTranslations(Map.of("pt", new LocalizedText("Especial da Casa", "Desc PT")));
        return cat;
    }

    /** Mimics getTranslation / MessageSource(default=key): returns the key when missing. */
    private static String catalogTranslator(String key) {
        return "category.M1.name".equals(key) ? "Hidromel Tradicional" : key;
    }

    @Test
    void shouldUseCatalogPropertiesTranslationWhenNoPerCategoryRow() {
        assertThat(CategoryDisplay.name(catalogCategory(), PT, CategoryDisplayTest::catalogTranslator))
                .isEqualTo("Hidromel Tradicional");
    }

    @Test
    void shouldFallBackToEnglishBaseWhenNoTranslationAnywhere() {
        // Translator returns the key (missing) -> English base.
        assertThat(CategoryDisplay.name(catalogCategory(), PT, key -> key))
                .isEqualTo("Traditional Mead");
    }

    @Test
    void shouldPreferPerCategoryTranslationOverCatalogProperties() {
        // Custom category has a PT row; the catalog key must not be consulted.
        assertThat(CategoryDisplay.name(customCategory(), PT, key -> "SHOULD-NOT-BE-USED"))
                .isEqualTo("Especial da Casa");
    }

    @Test
    void shouldTreatNullTranslatorResultAsMissing() {
        assertThat(CategoryDisplay.name(catalogCategory(), PT, key -> null))
                .isEqualTo("Traditional Mead");
    }

    @Test
    void codeAndNameShouldPrefixTheCode() {
        assertThat(CategoryDisplay.codeAndName(catalogCategory(), PT, CategoryDisplayTest::catalogTranslator))
                .isEqualTo("M1 — Hidromel Tradicional");
    }
}
