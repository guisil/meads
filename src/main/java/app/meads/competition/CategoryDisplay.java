package app.meads.competition;

import java.util.Locale;
import java.util.function.Function;

/**
 * Two-tier localization of a {@link DivisionCategory} name, shared by every
 * surface that shows a category to a user (entrant + admin + judge views, judge
 * emails, scoresheet PDF, awards results).
 *
 * <p>Resolution order:
 * <ol>
 *   <li>an admin-provided per-category translation row ({@link DivisionCategory#getName(Locale)}),
 *       which wins when it differs from the English base — covers custom categories;</li>
 *   <li>the catalog properties translation keyed {@code category.<code>.name} from the
 *       {@code messages_*.properties} bundles — covers standard catalog categories (M1, M1A, …);</li>
 *   <li>the English base name.</li>
 * </ol>
 *
 * <p>The catalog tier lives in the i18n bundles, which only the UI/service layer can
 * read, so callers pass a {@code keyTranslator} that maps an i18n key to its
 * translation for the desired locale and returns the key itself when no translation
 * exists — the contract of both Vaadin's {@code Component#getTranslation} and a
 * {@code MessageSource} called with the key as its default message.
 */
public final class CategoryDisplay {

    private CategoryDisplay() {}

    /** Localized category name (see class doc for the resolution order). */
    public static String name(DivisionCategory category, Locale locale,
                              Function<String, String> keyTranslator) {
        var perCategory = category.getName(locale);
        if (!perCategory.equals(category.getName())) {
            return perCategory;
        }
        var key = "category." + category.getCode() + ".name";
        var translated = keyTranslator.apply(key);
        return (translated == null || translated.equals(key)) ? category.getName() : translated;
    }

    /** {@code code + " — " + name(...)} — the common "M1A — Traditional Mead" label. */
    public static String codeAndName(DivisionCategory category, Locale locale,
                                     Function<String, String> keyTranslator) {
        return category.getCode() + " — " + name(category, locale, keyTranslator);
    }
}
