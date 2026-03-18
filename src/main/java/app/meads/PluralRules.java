package app.meads;

import java.util.Locale;

/**
 * CLDR-based plural category resolution for supported locales.
 * Returns "one", "few", "many", or "other" based on the count and locale.
 */
public final class PluralRules {

    private PluralRules() {}

    public static String getCategory(int count, Locale locale) {
        if ("pl".equals(locale.getLanguage())) {
            return polishCategory(count);
        }
        return simpleCategory(count);
    }

    // EN, PT, ES, IT and fallback: one (1) / other (everything else)
    private static String simpleCategory(int count) {
        return count == 1 ? "one" : "other";
    }

    // Polish: one (1) / few (2-4, 22-24, 32-34, ...) / many (everything else)
    private static String polishCategory(int count) {
        if (count == 1) {
            return "one";
        }
        var mod10 = count % 10;
        var mod100 = count % 100;
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
            return "few";
        }
        return "many";
    }
}
