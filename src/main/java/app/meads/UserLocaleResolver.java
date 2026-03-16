package app.meads;

import java.util.Locale;

/**
 * Resolves the preferred locale for a user. Defined in root module to avoid
 * circular dependency between root (MainLayout) and identity module.
 */
public interface UserLocaleResolver {
    Locale resolveLocale(String email);
}
