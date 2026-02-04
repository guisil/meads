package app.meads.user;

/**
 * Defines the purpose of an authentication token.
 */
public enum TokenPurpose {
  /**
   * General login token for authentication.
   * Short-lived (15-30 minutes).
   */
  LOGIN,

  /**
   * Token for judging session access.
   * Longer-lived (12-18 hours), scoped to a specific competition.
   */
  JUDGING_SESSION
}
