package app.meads;

import com.vaadin.flow.i18n.I18NProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
public class MeadsI18NProvider implements I18NProvider {

    private static final List<Locale> PROVIDED_LOCALES = List.of(
            Locale.ENGLISH,        // English
            Locale.of("es"),       // Espa\u00f1ol
            Locale.of("it"),       // Italiano
            Locale.of("pl"),       // Polski
            Locale.of("pt")        // Portugu\u00eas
    );

    // Native language names — displayed in the language switcher regardless of current locale
    private static final Map<String, String> LANGUAGE_LABELS = Map.of(
            "en", "English",
            "pt", "Portugu\u00eas",
            "es", "Espa\u00f1ol",
            "it", "Italiano",
            "pl", "Polski"
    );

    private final MessageSource messageSource;

    public MeadsI18NProvider(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    public List<Locale> getProvidedLocales() {
        return PROVIDED_LOCALES;
    }

    public static String getLanguageLabel(String languageCode) {
        return LANGUAGE_LABELS.getOrDefault(languageCode, languageCode);
    }

    @Override
    public String getTranslation(String key, Locale locale, Object... params) {
        try {
            return messageSource.getMessage(key, params, locale);
        } catch (NoSuchMessageException e) {
            log.warn("Missing translation key: {} for locale: {}", key, locale);
            return key;
        }
    }
}
