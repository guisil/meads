package app.meads.user.internal;

import app.meads.TestcontainersConfiguration;
import app.meads.user.TokenPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AccessTokenRepository.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@ActiveProfiles("test")
class AccessTokenRepositoryTest {

  @Autowired
  private AccessTokenRepository accessTokenRepository;

  @Autowired
  private UserRepository userRepository;

  private User testUser;

  @BeforeEach
  void setUp() {
    accessTokenRepository.deleteAll();
    userRepository.deleteAll();
    userRepository.flush();

    testUser = User.builder()
        .email("test@example.com")
        .displayName("Test User")
        .build();
    testUser = userRepository.save(testUser);
    userRepository.flush();
  }

  @Test
  void shouldSaveAndFindAccessTokenById() {
    // Given
    AccessToken token = AccessToken.builder()
        .tokenHash("hash123")
        .purpose(TokenPurpose.LOGIN)
        .email("test@example.com")
        .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
        .user(testUser)
        .build();

    // When
    AccessToken savedToken = accessTokenRepository.save(token);
    Optional<AccessToken> foundToken = accessTokenRepository.findById(savedToken.getId());

    // Then
    assertThat(foundToken).isPresent();
    assertThat(foundToken.get().getTokenHash()).isEqualTo("hash123");
    assertThat(foundToken.get().getPurpose()).isEqualTo(TokenPurpose.LOGIN);
    assertThat(foundToken.get().getEmail()).isEqualTo("test@example.com");
    assertThat(foundToken.get().getUsed()).isFalse();
    assertThat(foundToken.get().getUser().getId()).isEqualTo(testUser.getId());
  }

  @Test
  void shouldFindTokenByTokenHash() {
    // Given
    AccessToken token = AccessToken.builder()
        .tokenHash("unique-hash-456")
        .purpose(TokenPurpose.LOGIN)
        .email("test@example.com")
        .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
        .build();
    accessTokenRepository.save(token);

    // When
    Optional<AccessToken> foundToken = accessTokenRepository.findByTokenHash("unique-hash-456");

    // Then
    assertThat(foundToken).isPresent();
    assertThat(foundToken.get().getTokenHash()).isEqualTo("unique-hash-456");
  }

  @Test
  void shouldReturnEmptyWhenTokenHashNotFound() {
    // Given
    // No tokens in database

    // When
    Optional<AccessToken> foundToken = accessTokenRepository.findByTokenHash("nonexistent");

    // Then
    assertThat(foundToken).isEmpty();
  }

  @Test
  void shouldFindValidUnusedAndNotExpiredToken() {
    // Given
    Instant now = Instant.now();
    AccessToken validToken = AccessToken.builder()
        .tokenHash("valid-token")
        .purpose(TokenPurpose.LOGIN)
        .email("test@example.com")
        .expiresAt(now.plus(30, ChronoUnit.MINUTES))
        .used(false)
        .build();
    accessTokenRepository.save(validToken);

    // When
    Optional<AccessToken> foundToken = accessTokenRepository
        .findByTokenHashAndUsedFalseAndExpiresAtAfter("valid-token", now);

    // Then
    assertThat(foundToken).isPresent();
    assertThat(foundToken.get().getTokenHash()).isEqualTo("valid-token");
  }

  @Test
  void shouldNotFindUsedToken() {
    // Given
    Instant now = Instant.now();
    AccessToken usedToken = AccessToken.builder()
        .tokenHash("used-token")
        .purpose(TokenPurpose.LOGIN)
        .email("test@example.com")
        .expiresAt(now.plus(30, ChronoUnit.MINUTES))
        .used(true)
        .build();
    accessTokenRepository.save(usedToken);

    // When
    Optional<AccessToken> foundToken = accessTokenRepository
        .findByTokenHashAndUsedFalseAndExpiresAtAfter("used-token", now);

    // Then
    assertThat(foundToken).isEmpty();
  }

  @Test
  void shouldNotFindExpiredToken() {
    // Given
    Instant now = Instant.now();
    AccessToken expiredToken = AccessToken.builder()
        .tokenHash("expired-token")
        .purpose(TokenPurpose.LOGIN)
        .email("test@example.com")
        .expiresAt(now.minus(1, ChronoUnit.HOURS))
        .used(false)
        .build();
    accessTokenRepository.save(expiredToken);

    // When
    Optional<AccessToken> foundToken = accessTokenRepository
        .findByTokenHashAndUsedFalseAndExpiresAtAfter("expired-token", now);

    // Then
    assertThat(foundToken).isEmpty();
  }

  @Test
  void shouldFindUnusedTokensByEmail() {
    // Given
    String email = "user@example.com";
    AccessToken token1 = AccessToken.builder()
        .tokenHash("hash1")
        .purpose(TokenPurpose.LOGIN)
        .email(email)
        .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
        .used(false)
        .build();
    AccessToken token2 = AccessToken.builder()
        .tokenHash("hash2")
        .purpose(TokenPurpose.LOGIN)
        .email(email)
        .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
        .used(false)
        .build();
    AccessToken usedToken = AccessToken.builder()
        .tokenHash("hash3")
        .purpose(TokenPurpose.LOGIN)
        .email(email)
        .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
        .used(true)
        .build();
    accessTokenRepository.save(token1);
    accessTokenRepository.save(token2);
    accessTokenRepository.save(usedToken);

    // When
    List<AccessToken> foundTokens = accessTokenRepository.findByEmailAndUsedFalse(email);

    // Then
    assertThat(foundTokens).hasSize(2);
    assertThat(foundTokens).extracting(AccessToken::getTokenHash)
        .containsExactlyInAnyOrder("hash1", "hash2");
  }

  @Test
  void shouldFindUnusedTokensByEmailAndPurpose() {
    // Given
    String email = "judge@example.com";
    AccessToken loginToken = AccessToken.builder()
        .tokenHash("login-hash")
        .purpose(TokenPurpose.LOGIN)
        .email(email)
        .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
        .used(false)
        .build();
    AccessToken judgingToken = AccessToken.builder()
        .tokenHash("judging-hash")
        .purpose(TokenPurpose.JUDGING_SESSION)
        .email(email)
        .expiresAt(Instant.now().plus(12, ChronoUnit.HOURS))
        .used(false)
        .build();
    accessTokenRepository.save(loginToken);
    accessTokenRepository.save(judgingToken);

    // When
    List<AccessToken> loginTokens = accessTokenRepository
        .findByEmailAndPurposeAndUsedFalse(email, TokenPurpose.LOGIN);
    List<AccessToken> judgingTokens = accessTokenRepository
        .findByEmailAndPurposeAndUsedFalse(email, TokenPurpose.JUDGING_SESSION);

    // Then
    assertThat(loginTokens).hasSize(1);
    assertThat(loginTokens.get(0).getTokenHash()).isEqualTo("login-hash");
    assertThat(judgingTokens).hasSize(1);
    assertThat(judgingTokens.get(0).getTokenHash()).isEqualTo("judging-hash");
  }

  @Test
  void shouldMarkTokenAsUsed() {
    // Given
    AccessToken token = AccessToken.builder()
        .tokenHash("mark-used-hash")
        .purpose(TokenPurpose.LOGIN)
        .email("test@example.com")
        .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
        .used(false)
        .build();
    accessTokenRepository.saveAndFlush(token);

    // When
    int updated = accessTokenRepository.markTokenAsUsed("mark-used-hash");
    accessTokenRepository.flush();

    // Then
    assertThat(updated).isEqualTo(1);
    Optional<AccessToken> updatedToken = accessTokenRepository.findByTokenHash("mark-used-hash");
    assertThat(updatedToken).isPresent();
    assertThat(updatedToken.get().getUsed()).isTrue();
  }

  @Test
  void shouldReturnZeroWhenMarkingNonexistentTokenAsUsed() {
    // Given
    // No tokens in database

    // When
    int updated = accessTokenRepository.markTokenAsUsed("nonexistent-hash");

    // Then
    assertThat(updated).isEqualTo(0);
  }

  @Test
  void shouldDeleteExpiredTokens() {
    // Given
    Instant now = Instant.now();
    AccessToken expiredToken1 = AccessToken.builder()
        .tokenHash("expired1")
        .purpose(TokenPurpose.LOGIN)
        .email("test@example.com")
        .expiresAt(now.minus(1, ChronoUnit.HOURS))
        .build();
    AccessToken expiredToken2 = AccessToken.builder()
        .tokenHash("expired2")
        .purpose(TokenPurpose.LOGIN)
        .email("test@example.com")
        .expiresAt(now.minus(2, ChronoUnit.HOURS))
        .build();
    AccessToken validToken = AccessToken.builder()
        .tokenHash("valid")
        .purpose(TokenPurpose.LOGIN)
        .email("test@example.com")
        .expiresAt(now.plus(30, ChronoUnit.MINUTES))
        .build();
    accessTokenRepository.save(expiredToken1);
    accessTokenRepository.save(expiredToken2);
    accessTokenRepository.saveAndFlush(validToken);

    // When
    int deletedCount = accessTokenRepository.deleteExpiredTokens(now);
    accessTokenRepository.flush();

    // Then
    assertThat(deletedCount).isEqualTo(2);
    assertThat(accessTokenRepository.findByTokenHash("expired1")).isEmpty();
    assertThat(accessTokenRepository.findByTokenHash("expired2")).isEmpty();
    assertThat(accessTokenRepository.findByTokenHash("valid")).isPresent();
  }

  @Test
  void shouldSaveTokenWithoutUser() {
    // Given
    AccessToken token = AccessToken.builder()
        .tokenHash("no-user-hash")
        .purpose(TokenPurpose.LOGIN)
        .email("newuser@example.com")
        .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
        .user(null)
        .build();

    // When
    AccessToken savedToken = accessTokenRepository.save(token);
    Optional<AccessToken> foundToken = accessTokenRepository.findById(savedToken.getId());

    // Then
    assertThat(foundToken).isPresent();
    assertThat(foundToken.get().getUser()).isNull();
    assertThat(foundToken.get().getEmail()).isEqualTo("newuser@example.com");
  }

  @Test
  void shouldSaveTokenWithCompetitionId() {
    // Given
    UUID competitionId = UUID.randomUUID();
    AccessToken token = AccessToken.builder()
        .tokenHash("judging-hash")
        .purpose(TokenPurpose.JUDGING_SESSION)
        .email("judge@example.com")
        .expiresAt(Instant.now().plus(12, ChronoUnit.HOURS))
        .competitionId(competitionId)
        .build();

    // When
    AccessToken savedToken = accessTokenRepository.save(token);
    Optional<AccessToken> foundToken = accessTokenRepository.findById(savedToken.getId());

    // Then
    assertThat(foundToken).isPresent();
    assertThat(foundToken.get().getCompetitionId()).isEqualTo(competitionId);
    assertThat(foundToken.get().getPurpose()).isEqualTo(TokenPurpose.JUDGING_SESSION);
  }

  @Test
  void shouldGenerateUniqueIds() {
    // Given
    AccessToken token1 = AccessToken.builder()
        .tokenHash("hash1")
        .purpose(TokenPurpose.LOGIN)
        .email("test@example.com")
        .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
        .build();
    AccessToken token2 = AccessToken.builder()
        .tokenHash("hash2")
        .purpose(TokenPurpose.LOGIN)
        .email("test@example.com")
        .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
        .build();

    // When
    AccessToken savedToken1 = accessTokenRepository.save(token1);
    AccessToken savedToken2 = accessTokenRepository.save(token2);

    // Then
    assertThat(savedToken1.getId()).isNotNull();
    assertThat(savedToken2.getId()).isNotNull();
    assertThat(savedToken1.getId()).isNotEqualTo(savedToken2.getId());
  }
}
