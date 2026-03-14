package app.meads;

/**
 * Checks if a user has admin competitions. Defined in root module to avoid
 * circular dependency between root (MainLayout) and competition module.
 */
public interface CompetitionAdminChecker {
    boolean hasAdminCompetitions(String email);
}
