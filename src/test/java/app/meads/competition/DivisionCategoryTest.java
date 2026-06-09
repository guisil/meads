package app.meads.competition;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DivisionCategoryTest {

    @Test
    void shouldResolveNameAndDescriptionByLocaleWithEnglishFallback() {
        var category = new DivisionCategory(UUID.randomUUID(), null,
                "M1A", "Traditional Mead", "Honey and water", null, 1);
        category.setTranslations(Map.of(
                "pt", new LocalizedText("Hidromel Tradicional", "Mel e água"),
                "es", new LocalizedText("Hidromiel Tradicional", "Miel y agua")));

        // Translation present for the requested locale's language
        assertThat(category.getName(Locale.forLanguageTag("pt"))).isEqualTo("Hidromel Tradicional");
        assertThat(category.getDescription(Locale.forLanguageTag("pt"))).isEqualTo("Mel e água");

        // No translation for the requested language -> English base
        assertThat(category.getName(Locale.ITALIAN)).isEqualTo("Traditional Mead");
        assertThat(category.getDescription(Locale.ITALIAN)).isEqualTo("Honey and water");

        // English itself uses the base values (no row stored for "en")
        assertThat(category.getName(Locale.ENGLISH)).isEqualTo("Traditional Mead");
        assertThat(category.getDescription(Locale.ENGLISH)).isEqualTo("Honey and water");
    }
}
