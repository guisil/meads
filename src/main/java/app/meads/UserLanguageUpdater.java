package app.meads;

/**
 * Updates the preferred language for a user. Defined in root module to avoid
 * circular dependency between root (MainLayout) and identity module.
 */
public interface UserLanguageUpdater {
    void updatePreferredLanguage(String email, String language);
}
