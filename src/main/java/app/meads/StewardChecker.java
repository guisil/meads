package app.meads;

/**
 * Checks whether a user holds the STEWARD role in any competition. Defined in the
 * root module to avoid a circular dependency between root (MainLayout) and the
 * competition module.
 */
public interface StewardChecker {
    boolean isStewardSomewhere(String email);
}
