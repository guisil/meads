package app.meads;

import java.util.Optional;

/**
 * Resolves where an entrant should land for published results — defined in the
 * root module so {@code RootView} and {@code MainLayout} can use it without
 * depending on the entry/awards modules directly.
 */
public interface EntrantResultsChecker {

    /**
     * The results landing path for an entrant (by email):
     * <ul>
     *   <li>empty when none of their divisions has published results;</li>
     *   <li>the division's {@code …/my-results} path when exactly one has;</li>
     *   <li>the {@code my-entries} hub path when several have (the hub lists per-division results links).</li>
     * </ul>
     */
    Optional<String> resultsLandingPath(String email);
}
