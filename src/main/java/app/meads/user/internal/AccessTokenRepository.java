package app.meads.user.internal;

import app.meads.user.TokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for AccessToken entity operations.
 */
@Repository
public interface AccessTokenRepository extends JpaRepository<AccessToken, UUID> {

  /**
   * Finds an access token by its hash.
   *
   * @param tokenHash the hashed token to search for
   * @return optional containing the token if found
   */
  Optional<AccessToken> findByTokenHash(String tokenHash);

  /**
   * Finds a valid (unused and not expired) token by its hash.
   *
   * @param tokenHash the hashed token to search for
   * @param now the current time to check expiration
   * @return optional containing the token if found and valid
   */
  Optional<AccessToken> findByTokenHashAndUsedFalseAndExpiresAtAfter(
      String tokenHash, Instant now);

  /**
   * Finds all unused tokens for a given email address.
   * Useful for cleanup or validation.
   *
   * @param email the email address
   * @return list of unused tokens for this email
   */
  List<AccessToken> findByEmailAndUsedFalse(String email);

  /**
   * Finds unused tokens by email and purpose.
   *
   * @param email the email address
   * @param purpose the token purpose
   * @return list of unused tokens matching the criteria
   */
  List<AccessToken> findByEmailAndPurposeAndUsedFalse(String email, TokenPurpose purpose);

  /**
   * Deletes all expired tokens.
   * Useful for periodic cleanup.
   *
   * @param now the current time
   * @return number of deleted tokens
   */
  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM AccessToken t WHERE t.expiresAt < :now")
  int deleteExpiredTokens(@Param("now") Instant now);

  /**
   * Marks a token as used.
   *
   * @param tokenHash the hashed token
   * @return number of updated records (should be 1 if successful)
   */
  @Modifying(clearAutomatically = true)
  @Query("UPDATE AccessToken t SET t.used = true WHERE t.tokenHash = :tokenHash")
  int markTokenAsUsed(@Param("tokenHash") String tokenHash);
}
