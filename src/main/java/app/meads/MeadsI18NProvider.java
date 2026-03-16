package app.meads;

import com.vaadin.flow.i18n.I18NProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Slf4j
@Component
public class MeadsI18NProvider implements I18NProvider {

    private static final List<Locale> PROVIDED_LOCALES = List.of(
            Locale.ENGLISH,
            Locale.of("pt")
    );

    private final MessageSource messageSource;

    public MeadsI18NProvider(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    public List<Locale> getProvidedLocales() {
        return PROVIDED_LOCALES;
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
