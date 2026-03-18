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
            Locale.ENGLISH,
            Locale.of("es"),
            Locale.ITALIAN,
            Locale.of("pl"),
            Locale.of("pt")
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

    /**
     * Resolves a plural-aware message key based on CLDR plural rules.
     * Looks up {@code keyPrefix.one}, {@code keyPrefix.few}, {@code keyPrefix.many},
     * or {@code keyPrefix.other} depending on count and locale.
     * Falls back to {@code keyPrefix.other} when a specific category key is missing.
     */
    public String getPlural(String keyPrefix, int count, Locale locale) {
        var category = PluralRules.getCategory(count, locale);
        var specificKey = keyPrefix + "." + category;
        try {
            return messageSource.getMessage(specificKey, null, locale);
        } catch (NoSuchMessageException e) {
            // Fall back to .other
            return messageSource.getMessage(keyPrefix + ".other", null, locale);
        }
    }
}
