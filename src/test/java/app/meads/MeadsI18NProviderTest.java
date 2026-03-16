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
}
