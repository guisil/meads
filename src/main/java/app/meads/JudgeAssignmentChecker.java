package app.meads;

/**
 * Checks if a user has any JudgeAssignment. Defined in root module to avoid
 * circular dependency between root (MainLayout) and judging module.
 */
public interface JudgeAssignmentChecker {
    boolean hasAnyJudgeAssignment(String email);
}
