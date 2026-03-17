package app.meads;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class LanguageMapping {

    private static final Map<String, Set<String>> LANGUAGE_COUNTRIES = Map.of(
            "pt", Set.of("PT", "BR"),
            "es", Set.of("ES", "MX", "AR", "CL", "CO", "PE", "VE", "EC", "UY", "PY",
                    "BO", "CU", "DO", "HN", "SV", "GT", "NI", "CR", "PA"),
            "it", Set.of("IT"),
            "pl", Set.of("PL")
    );

    /** Languages currently active in the application (must match MeadsI18NProvider). */
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("en", "es", "it", "pl", "pt");

    private LanguageMapping() {}

    public static Locale languageForCountry(String countryCode) {
        if (countryCode == null) {
            return Locale.ENGLISH;
        }
        for (var entry : LANGUAGE_COUNTRIES.entrySet()) {
            if (entry.getValue().contains(countryCode)
                    && SUPPORTED_LANGUAGES.contains(entry.getKey())) {
                return Locale.of(entry.getKey());
            }
        }
        return Locale.ENGLISH;
    }

    public static Locale resolveLocale(String preferredLanguage, String countryCode) {
        if (preferredLanguage != null
                && SUPPORTED_LANGUAGES.contains(preferredLanguage)) {
            return Locale.of(preferredLanguage);
        }
        return languageForCountry(countryCode);
    }
}
