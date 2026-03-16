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

    private LanguageMapping() {}

    public static Locale languageForCountry(String countryCode) {
        if (countryCode == null) {
            return Locale.ENGLISH;
        }
        for (var entry : LANGUAGE_COUNTRIES.entrySet()) {
            if (entry.getValue().contains(countryCode)) {
                return Locale.of(entry.getKey());
            }
        }
        return Locale.ENGLISH;
    }

    public static Locale resolveLocale(String preferredLanguage, String countryCode) {
        if (preferredLanguage != null) {
            return Locale.of(preferredLanguage);
        }
        return languageForCountry(countryCode);
    }
}
