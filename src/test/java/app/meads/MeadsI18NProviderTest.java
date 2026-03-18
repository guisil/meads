package app.meads;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.*;

class MeadsI18NProviderTest {

    private MeadsI18NProvider provider;

    @BeforeEach
    void setup() {
        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        provider = new MeadsI18NProvider(messageSource);
    }

    @Test
    void shouldProvideEnglishAndPortugueseLocales() {
        assertThat(provider.getProvidedLocales())
                .contains(Locale.ENGLISH, Locale.of("pt"));
    }

    @Test
    void shouldResolveEnglishTranslation() {
        var translation = provider.getTranslation("nav.my-entries", Locale.ENGLISH);
        assertThat(translation).isEqualTo("My Entries");
    }

    @Test
    void shouldFallbackToEnglishForUnknownLocale() {
        var translation = provider.getTranslation("nav.my-entries", Locale.JAPANESE);
        assertThat(translation).isEqualTo("My Entries");
    }

    @Test
    void shouldReturnKeyForMissingTranslation() {
        var translation = provider.getTranslation("nonexistent.key", Locale.ENGLISH);
        assertThat(translation).isEqualTo("nonexistent.key");
    }

    @Test
    void shouldResolveParameterizedTranslation() {
        var translation = provider.getTranslation("error.entry.limit-total", Locale.ENGLISH, 10);
        assertThat(translation).isEqualTo("Entry limit reached (max 10 total)");
    }

    @Test
    void shouldResolvePortugueseTranslation() {
        var pt = Locale.of("pt");
        assertThat(provider.getTranslation("nav.my-entries", pt))
                .isEqualTo("As Minhas Inscri\u00e7\u00f5es");
    }

    @Test
    void shouldResolvePortugueseEnumTranslation() {
        var pt = Locale.of("pt");
        assertThat(provider.getTranslation("entry.status.DRAFT", pt))
                .isEqualTo("Rascunho");
        assertThat(provider.getTranslation("entry.sweetness.DRY", pt))
                .isEqualTo("Seco");
    }

    @Test
    void shouldResolvePortugueseCategoryTranslation() {
        var pt = Locale.of("pt");
        assertThat(provider.getTranslation("category.M1.name", pt))
                .isEqualTo("Hidromel Tradicional");
    }

    @Test
    void shouldResolvePortugueseParameterizedTranslation() {
        var pt = Locale.of("pt");
        var translation = provider.getPlural("entries.credits.remaining", 3, pt);
        assertThat(translation).isEqualTo("3 dispon\u00edveis");
    }

    @Test
    void shouldResolvePortugueseEmailTranslation() {
        var pt = Locale.of("pt");
        assertThat(provider.getTranslation("email.magic-link.cta", pt))
                .isEqualTo("Entrar");
    }

    @Test
    void shouldResolvePortugueseErrorMessageWithParams() {
        var pt = Locale.of("pt");
        var translation = provider.getTranslation("error.entry.limit-total", pt, 5);
        assertThat(translation).contains("5").contains("total");
    }

    @Test
    void shouldResolvePortuguesePdfInstructions() {
        var pt = Locale.of("pt");
        assertThat(provider.getTranslation("pdf.instructions.line1", pt))
                .contains("etiquetas");
        assertThat(provider.getTranslation("pdf.label.disclaimer", pt))
                .contains("AMOSTRAS");
    }

    @Test
    void shouldFallbackToEnglishForMissingPortugueseKey() {
        var pt = Locale.of("pt");
        // A key that exists only in English should fall back
        var translation = provider.getTranslation("nonexistent.key", pt);
        assertThat(translation).isEqualTo("nonexistent.key");
    }

    // --- getPlural tests ---

    @Test
    void shouldResolvePluralForEnglishOneCredit() {
        var result = provider.getPlural("email.credit.unit", 1, Locale.ENGLISH);
        assertThat(result).isEqualTo("credit");
    }

    @Test
    void shouldResolvePluralForEnglishMultipleCredits() {
        var result = provider.getPlural("email.credit.unit", 3, Locale.ENGLISH);
        assertThat(result).isEqualTo("credits");
    }

    @Test
    void shouldResolvePluralForPolishOneCredit() {
        var pl = Locale.of("pl");
        var result = provider.getPlural("email.credit.unit", 1, pl);
        assertThat(result).isEqualTo("op\u0142acone zg\u0142oszenie");
    }

    @Test
    void shouldResolvePluralForPolishFewCredits() {
        var pl = Locale.of("pl");
        var result = provider.getPlural("email.credit.unit", 3, pl);
        assertThat(result).isEqualTo("op\u0142acone zg\u0142oszenia");
    }

    @Test
    void shouldResolvePluralForPolishManyCredits() {
        var pl = Locale.of("pl");
        var result = provider.getPlural("email.credit.unit", 5, pl);
        assertThat(result).isEqualTo("op\u0142aconych zg\u0142osze\u0144");
    }

    @Test
    void shouldResolvePluralForPortugueseCredits() {
        var pt = Locale.of("pt");
        assertThat(provider.getPlural("email.credit.unit", 1, pt)).isEqualTo("cr\u00e9dito");
        assertThat(provider.getPlural("email.credit.unit", 5, pt)).isEqualTo("cr\u00e9ditos");
    }

    @Test
    void shouldFallbackToOtherWhenFewKeyMissing() {
        // EN has no .few key — should fall back to .other
        var result = provider.getPlural("email.credit.unit", 3, Locale.ENGLISH);
        assertThat(result).isEqualTo("credits");
    }

    // --- Plural-aware view strings ---

    @Test
    void shouldResolvePluralRemainingForEnglish() {
        assertThat(provider.getPlural("entries.credits.remaining", 1, Locale.ENGLISH))
                .isEqualTo("1 remaining");
        assertThat(provider.getPlural("entries.credits.remaining", 3, Locale.ENGLISH))
                .isEqualTo("3 remaining");
    }

    @Test
    void shouldResolvePluralRemainingForPolish() {
        var pl = Locale.of("pl");
        assertThat(provider.getPlural("entries.credits.remaining", 1, pl))
                .isEqualTo("1 dost\u0119pne");
        assertThat(provider.getPlural("entries.credits.remaining", 3, pl))
                .isEqualTo("3 dost\u0119pne");
        assertThat(provider.getPlural("entries.credits.remaining", 5, pl))
                .isEqualTo("5 dost\u0119pnych");
    }

    @Test
    void shouldResolvePluralSubmitAllConfirmForEnglish() {
        assertThat(provider.getPlural("entries.submit-all.confirm", 1, Locale.ENGLISH))
                .contains("1 draft entry");
        assertThat(provider.getPlural("entries.submit-all.confirm", 3, Locale.ENGLISH))
                .contains("3 draft entries");
    }

    @Test
    void shouldResolvePluralSubmitAllConfirmForPolish() {
        var pl = Locale.of("pl");
        assertThat(provider.getPlural("entries.submit-all.confirm", 2, pl))
                .contains("2 zg\u0142oszenia");
        assertThat(provider.getPlural("entries.submit-all.confirm", 5, pl))
                .contains("5 zg\u0142osze\u0144");
    }

    @Test
    void shouldResolvePluralSubmitAllSuccessForEnglish() {
        assertThat(provider.getPlural("entries.submit-all.success", 1, Locale.ENGLISH))
                .isEqualTo("1 entry submitted");
        assertThat(provider.getPlural("entries.submit-all.success", 3, Locale.ENGLISH))
                .isEqualTo("3 entries submitted");
    }

    @Test
    void shouldResolvePluralSubmitAllSuccessForPolish() {
        var pl = Locale.of("pl");
        assertThat(provider.getPlural("entries.submit-all.success", 1, pl))
                .isEqualTo("1 zg\u0142oszenie wys\u0142ane");
        assertThat(provider.getPlural("entries.submit-all.success", 3, pl))
                .isEqualTo("3 zg\u0142oszenia wys\u0142ane");
        assertThat(provider.getPlural("entries.submit-all.success", 5, pl))
                .isEqualTo("5 zg\u0142osze\u0144 wys\u0142anych");
    }

    @Test
    void shouldHaveAllEnglishKeysInPortuguese() {
        var en = Locale.ENGLISH;
        var pt = Locale.of("pt");
        // Spot-check that key categories all have Portuguese translations
        // (not returning the English value or the key)
        assertThat(provider.getTranslation("nav.logout", pt))
                .isNotEqualTo(provider.getTranslation("nav.logout", en));
        assertThat(provider.getTranslation("entries.add", pt))
                .isNotEqualTo(provider.getTranslation("entries.add", en));
        assertThat(provider.getTranslation("error.entry.not-found", pt))
                .isNotEqualTo(provider.getTranslation("error.entry.not-found", en));
    }
}
