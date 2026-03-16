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
        assertThat(translation).isEqualTo("Entry limit reached for this division (max 10 total)");
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
        var translation = provider.getTranslation("entries.credits.remaining", pt, 3);
        assertThat(translation).isEqualTo("3 disponíveis");
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

    @Test
    void shouldResolveSpanishTranslation() {
        var es = Locale.of("es");
        assertThat(provider.getTranslation("nav.my-entries", es))
                .isEqualTo("Mis Inscripciones");
        assertThat(provider.getTranslation("entry.status.DRAFT", es))
                .isEqualTo("Borrador");
        assertThat(provider.getTranslation("category.M1.name", es))
                .isEqualTo("Hidromiel Tradicional");
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
