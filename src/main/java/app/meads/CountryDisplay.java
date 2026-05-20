package app.meads;

import java.util.Locale;

/**
 * Localizes ISO 3166-1 alpha-2 country codes to their display names.
 * Country codes are stored on entities (e.g. {@code User.country}); this
 * renders them in whichever language the UI is currently showing, instead
 * of a fixed language.
 */
public final class CountryDisplay {

    private CountryDisplay() {}

    /**
     * Display name of an ISO 3166-1 alpha-2 country code in {@code locale} —
     * e.g. {@code name("DE", Locale.ITALIAN)} returns "Germania". Callers are
     * expected to guard against null codes (entities may have no country set).
     */
    public static String name(String countryCode, Locale locale) {
        return Locale.of("", countryCode).getDisplayCountry(displayLocale(locale));
    }

    /**
     * MEADS targets European Portuguese, but the bare {@code pt} locale
     * resolves to Brazilian country names in CLDR (e.g. "Polônia", "Romênia").
     * Pin a region-less {@code pt} locale to {@code pt-PT} so country names
     * match the rest of the European-Portuguese UI ("Polónia", "Roménia").
     */
    private static Locale displayLocale(Locale locale) {
        if ("pt".equals(locale.getLanguage()) && locale.getCountry().isEmpty()) {
            return Locale.of("pt", "PT");
        }
        return locale;
    }
}
