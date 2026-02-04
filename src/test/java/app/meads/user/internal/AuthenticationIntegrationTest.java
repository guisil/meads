package app.meads.user.internal;

import app.meads.TestcontainersConfiguration;
import app.meads.user.TokenPurpose;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@DisplayName("End-to-End Authentication Integration Tests")
class AuthenticationIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserService userService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private AccessTokenRepository accessTokenRepository;

  @AfterEach
  void cleanUp() {
    accessTokenRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("should complete full authentication flow for admin user")
  void shouldCompleteFullAuthenticationFlowForAdminUser() throws Exception {
    // Given - Create admin user
    var admin = User.builder()
        .email("admin@example.com")
        .displayName("Admin User")
        .isSystemAdmin(true)
        .build();
    userRepository.save(admin);

    // When - Generate magic link (simulating email service)
    var capturedMagicLink = new String[1];
    var emailService = new app.meads.user.api.EmailService() {
      @Override
      public void sendMagicLinkEmail(String email, String magicLink) {
        capturedMagicLink[0] = magicLink;
      }

      @Override
      public void sendBulkMagicLinkEmails(java.util.List<app.meads.user.api.EmailService.EmailRecipient> recipients) {
      }
    };

    // Manually call the token generation logic
    userService.sendMagicLink(admin.getId());

    // Extract token from database
    var tokens = accessTokenRepository.findByEmailAndUsedFalse("admin@example.com");
    assertThat(tokens).hasSize(1);
    var tokenHash = tokens.get(0).getTokenHash();

    // Find the raw token by iterating through possible tokens (for testing)
    // In real scenario, the raw token would be in the email
    // For testing, we need to extract it from the magic link or use the hash directly
    // Since we can't reverse the hash, let's manually create a token for testing
    var rawToken = "test-token-" + UUID.randomUUID();
    var testTokenHash = hashToken(rawToken);

    // Create a test token directly
    var testToken = AccessToken.builder()
        .tokenHash(testTokenHash)
        .purpose(TokenPurpose.LOGIN)
        .email("admin@example.com")
        .expiresAt(Instant.now().plusSeconds(1800))
        .used(false)
        .user(admin)
        .build();
    accessTokenRepository.save(testToken);

    // Then - Verify the token
    mockMvc.perform(get("/auth/verify")
            .param("token", rawToken)
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/users"));

    // Verify token is marked as used
    var usedToken = accessTokenRepository.findById(testToken.getId()).orElseThrow();
    assertThat(usedToken.getUsed()).isTrue();
  }

  @Test
  @DisplayName("should complete full authentication flow for regular user")
  void shouldCompleteFullAuthenticationFlowForRegularUser() throws Exception {
    // Given - Create regular user
    var user = User.builder()
        .email("user@example.com")
        .displayName("Regular User")
        .isSystemAdmin(false)
        .build();
    userRepository.save(user);

    // When - Create token and verify
    var rawToken = "test-token-" + UUID.randomUUID();
    var testTokenHash = hashToken(rawToken);

    var testToken = AccessToken.builder()
        .tokenHash(testTokenHash)
        .purpose(TokenPurpose.LOGIN)
        .email("user@example.com")
        .expiresAt(Instant.now().plusSeconds(1800))
        .used(false)
        .user(user)
        .build();
    accessTokenRepository.save(testToken);

    // Then - Verify the token
    mockMvc.perform(get("/auth/verify")
            .param("token", rawToken)
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/"));

    // Verify token is marked as used
    var usedToken = accessTokenRepository.findById(testToken.getId()).orElseThrow();
    assertThat(usedToken.getUsed()).isTrue();
  }

  @Test
  @DisplayName("should reject expired token")
  void shouldRejectExpiredToken() throws Exception {
    // Given - Create user and expired token
    var user = User.builder()
        .email("expired@example.com")
        .displayName("Expired User")
        .isSystemAdmin(false)
        .build();
    userRepository.save(user);

    var rawToken = "expired-token-" + UUID.randomUUID();
    var testTokenHash = hashToken(rawToken);

    var expiredToken = AccessToken.builder()
        .tokenHash(testTokenHash)
        .purpose(TokenPurpose.LOGIN)
        .email("expired@example.com")
        .expiresAt(Instant.now().minusSeconds(1)) // Expired 1 second ago
        .used(false)
        .user(user)
        .build();
    accessTokenRepository.save(expiredToken);

    // When & Then - Verify should fail
    mockMvc.perform(get("/auth/verify")
            .param("token", rawToken)
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login-error"));

    // Token should still be unused
    var unchangedToken = accessTokenRepository.findById(expiredToken.getId()).orElseThrow();
    assertThat(unchangedToken.getUsed()).isFalse();
  }

  @Test
  @DisplayName("should reject token after expiration time elapses")
  void shouldRejectTokenAfterExpirationTimeElapses() throws Exception {
    // Given - Create user and token that expires in 1 second
    var user = User.builder()
        .email("awaitility@example.com")
        .displayName("Awaitility User")
        .isSystemAdmin(false)
        .build();
    userRepository.save(user);

    var rawToken = "awaitility-token-" + UUID.randomUUID();
    var testTokenHash = hashToken(rawToken);

    var expiringToken = AccessToken.builder()
        .tokenHash(testTokenHash)
        .purpose(TokenPurpose.LOGIN)
        .email("awaitility@example.com")
        .expiresAt(Instant.now().plusSeconds(1)) // Expires in 1 second
        .used(false)
        .user(user)
        .build();
    accessTokenRepository.save(expiringToken);

    // Wait for token to expire
    await()
        .atMost(Duration.ofSeconds(3))
        .pollInterval(Duration.ofMillis(200))
        .until(() -> expiringToken.getExpiresAt().isBefore(Instant.now()));

    // When - Token is now expired
    mockMvc.perform(get("/auth/verify")
            .param("token", rawToken)
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login-error"));

    // Verify the token remained unused (expired before use)
    var expiredToken = accessTokenRepository.findById(expiringToken.getId()).orElseThrow();
    assertThat(expiredToken.getUsed()).isFalse();
    assertThat(expiredToken.getExpiresAt()).isBefore(Instant.now());
  }

  @Test
  @DisplayName("should reject used token")
  void shouldRejectUsedToken() throws Exception {
    // Given - Create user and used token
    var user = User.builder()
        .email("used@example.com")
        .displayName("Used User")
        .isSystemAdmin(false)
        .build();
    userRepository.save(user);

    var rawToken = "used-token-" + UUID.randomUUID();
    var testTokenHash = hashToken(rawToken);

    var usedToken = AccessToken.builder()
        .tokenHash(testTokenHash)
        .purpose(TokenPurpose.LOGIN)
        .email("used@example.com")
        .expiresAt(Instant.now().plusSeconds(1800))
        .used(true) // Already used
        .user(user)
        .build();
    accessTokenRepository.save(usedToken);

    // When & Then - Verify should fail
    mockMvc.perform(get("/auth/verify")
            .param("token", rawToken)
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login-error"));

    // Token should still be marked as used
    var unchangedToken = accessTokenRepository.findById(usedToken.getId()).orElseThrow();
    assertThat(unchangedToken.getUsed()).isTrue();
  }

  @Test
  @DisplayName("should reject invalid token")
  void shouldRejectInvalidToken() throws Exception {
    // Given - No token in database
    var rawToken = "invalid-token-" + UUID.randomUUID();

    // When & Then - Verify should fail
    mockMvc.perform(get("/auth/verify")
            .param("token", rawToken)
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login-error"));
  }

  @Test
  @DisplayName("should reject token for non-existent user")
  void shouldRejectTokenForNonExistentUser() throws Exception {
    // Given - Token without user (user was deleted or never existed)
    var rawToken = "orphan-token-" + UUID.randomUUID();
    var testTokenHash = hashToken(rawToken);

    var orphanToken = AccessToken.builder()
        .tokenHash(testTokenHash)
        .purpose(TokenPurpose.LOGIN)
        .email("nonexistent@example.com")
        .expiresAt(Instant.now().plusSeconds(1800))
        .used(false)
        .user(null) // No user
        .build();
    accessTokenRepository.save(orphanToken);

    // When & Then - Should fail because user doesn't exist
    mockMvc.perform(get("/auth/verify")
            .param("token", rawToken)
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login-error"));

    // Token should not be marked as used (failed before marking)
    var unusedToken = accessTokenRepository.findById(orphanToken.getId()).orElseThrow();
    assertThat(unusedToken.getUsed()).isFalse();
  }

  @Test
  @DisplayName("should not allow token reuse")
  void shouldNotAllowTokenReuse() throws Exception {
    // Given - Create user and token
    var user = User.builder()
        .email("reuse@example.com")
        .displayName("Reuse User")
        .isSystemAdmin(false)
        .build();
    userRepository.save(user);

    var rawToken = "reuse-token-" + UUID.randomUUID();
    var testTokenHash = hashToken(rawToken);

    var token = AccessToken.builder()
        .tokenHash(testTokenHash)
        .purpose(TokenPurpose.LOGIN)
        .email("reuse@example.com")
        .expiresAt(Instant.now().plusSeconds(1800))
        .used(false)
        .user(user)
        .build();
    accessTokenRepository.save(token);

    // When - First verification succeeds
    mockMvc.perform(get("/auth/verify")
            .param("token", rawToken)
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/"));

    // Then - Second verification should fail
    mockMvc.perform(get("/auth/verify")
            .param("token", rawToken)
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login-error"));
  }

  @Test
  @DisplayName("should isolate tokens between users")
  void shouldIsolateTokensBetweenUsers() throws Exception {
    // Given - Create two users with tokens
    var user1 = User.builder()
        .email("user1@example.com")
        .displayName("User 1")
        .isSystemAdmin(false)
        .build();
    var user2 = User.builder()
        .email("user2@example.com")
        .displayName("User 2")
        .isSystemAdmin(true)
        .build();
    userRepository.saveAll(java.util.List.of(user1, user2));

    var rawToken1 = "token1-" + UUID.randomUUID();
    var rawToken2 = "token2-" + UUID.randomUUID();

    var token1 = AccessToken.builder()
        .tokenHash(hashToken(rawToken1))
        .purpose(TokenPurpose.LOGIN)
        .email("user1@example.com")
        .expiresAt(Instant.now().plusSeconds(1800))
        .used(false)
        .user(user1)
        .build();

    var token2 = AccessToken.builder()
        .tokenHash(hashToken(rawToken2))
        .purpose(TokenPurpose.LOGIN)
        .email("user2@example.com")
        .expiresAt(Instant.now().plusSeconds(1800))
        .used(false)
        .user(user2)
        .build();

    accessTokenRepository.saveAll(java.util.List.of(token1, token2));

    // When & Then - Each token should authenticate its own user
    mockMvc.perform(get("/auth/verify")
            .param("token", rawToken1)
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/")); // Regular user

    mockMvc.perform(get("/auth/verify")
            .param("token", rawToken2)
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/users")); // Admin user

    // Both tokens should be used
    var usedToken1 = accessTokenRepository.findById(token1.getId()).orElseThrow();
    var usedToken2 = accessTokenRepository.findById(token2.getId()).orElseThrow();
    assertThat(usedToken1.getUsed()).isTrue();
    assertThat(usedToken2.getUsed()).isTrue();
  }

  @Test
  @DisplayName("should reject JUDGING_SESSION token type (not yet supported)")
  void shouldRejectJudgingSessionTokenType() throws Exception {
    // Given - Create user with judging session token
    var judge = User.builder()
        .email("judge@example.com")
        .displayName("Judge User")
        .isSystemAdmin(false)
        .build();
    userRepository.save(judge);

    var rawToken = "judging-token-" + UUID.randomUUID();
    var testTokenHash = hashToken(rawToken);

    var judgingToken = AccessToken.builder()
        .tokenHash(testTokenHash)
        .purpose(TokenPurpose.JUDGING_SESSION)
        .email("judge@example.com")
        .expiresAt(Instant.now().plusSeconds(64800)) // 18 hours
        .used(false)
        .user(judge)
        .competitionId(UUID.randomUUID())
        .build();
    accessTokenRepository.save(judgingToken);

    // When & Then - Should be rejected (not yet supported)
    mockMvc.perform(get("/auth/verify")
            .param("token", rawToken)
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login-error"));

    // Token should not be marked as used (rejected before marking)
    var unusedToken = accessTokenRepository.findById(judgingToken.getId()).orElseThrow();
    assertThat(unusedToken.getUsed()).isFalse();
  }

  /**
   * Helper method to hash tokens using SHA-256 with Base64 encoding (same as UserService and AuthController)
   */
  private String hashToken(String rawToken) {
    try {
      var digest = java.security.MessageDigest.getInstance("SHA-256");
      var hashBytes = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return java.util.Base64.getEncoder().encodeToString(hashBytes);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 algorithm not available", e);
    }
  }
}
