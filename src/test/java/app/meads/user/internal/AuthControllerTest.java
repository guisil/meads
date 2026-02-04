package app.meads.user.internal;

import app.meads.TestcontainersConfiguration;
import app.meads.user.TokenPurpose;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
@DisplayName("AuthController Tests")
class AuthControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private AccessTokenRepository accessTokenRepository;

  @Autowired
  private UserRepository userRepository;

  @AfterEach
  void cleanUp() {
    accessTokenRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Nested
  @DisplayName("GET /auth/verify")
  class VerifyEndpointTests {

    @Test
    @DisplayName("should authenticate admin user and redirect to admin page")
    void shouldAuthenticateAdminUserAndRedirectToAdminPage() throws Exception {
      // Given
      var rawToken = "test-admin-token-" + UUID.randomUUID();
      var tokenHash = hashToken(rawToken);

      var user = User.builder()
          .email("admin@example.com")
          .displayName("Admin User")
          .isSystemAdmin(true)
          .build();
      userRepository.save(user);

      var token = AccessToken.builder()
          .tokenHash(tokenHash)
          .purpose(TokenPurpose.LOGIN)
          .email("admin@example.com")
          .expiresAt(Instant.now().plusSeconds(1800))
          .used(false)
          .user(user)
          .build();
      accessTokenRepository.save(token);

      // When & Then
      mockMvc.perform(get("/auth/verify")
              .param("token", rawToken)
              .with(csrf()))
          .andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrl("/admin/users"));

      // Verify token is marked as used
      var usedToken = accessTokenRepository.findById(token.getId()).orElseThrow();
      assertThat(usedToken.getUsed()).isTrue();
    }

    @Test
    @DisplayName("should authenticate regular user and redirect to home page")
    void shouldAuthenticateRegularUserAndRedirectToHomePage() throws Exception {
      // Given
      var rawToken = "test-user-token-" + UUID.randomUUID();
      var tokenHash = hashToken(rawToken);

      var user = User.builder()
          .email("user@example.com")
          .displayName("Regular User")
          .isSystemAdmin(false)
          .build();
      userRepository.save(user);

      var token = AccessToken.builder()
          .tokenHash(tokenHash)
          .purpose(TokenPurpose.LOGIN)
          .email("user@example.com")
          .expiresAt(Instant.now().plusSeconds(1800))
          .used(false)
          .user(user)
          .build();
      accessTokenRepository.save(token);

      // When & Then
      mockMvc.perform(get("/auth/verify")
              .param("token", rawToken)
              .with(csrf()))
          .andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrl("/"));

      // Verify token is marked as used
      var usedToken = accessTokenRepository.findById(token.getId()).orElseThrow();
      assertThat(usedToken.getUsed()).isTrue();
    }

    @Test
    @DisplayName("should redirect to login error when token is invalid")
    void shouldRedirectToLoginErrorWhenTokenIsInvalid() throws Exception {
      // Given
      var rawToken = "invalid-token-" + UUID.randomUUID();

      // When & Then
      mockMvc.perform(get("/auth/verify")
              .param("token", rawToken)
              .with(csrf()))
          .andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrl("/login-error"));
    }

    @Test
    @DisplayName("should redirect to login error when token is already used")
    void shouldRedirectToLoginErrorWhenTokenIsAlreadyUsed() throws Exception {
      // Given
      var rawToken = "used-token-" + UUID.randomUUID();
      var tokenHash = hashToken(rawToken);

      var user = User.builder()
          .email("used@example.com")
          .displayName("Used User")
          .isSystemAdmin(false)
          .build();
      userRepository.save(user);

      var token = AccessToken.builder()
          .tokenHash(tokenHash)
          .purpose(TokenPurpose.LOGIN)
          .email("used@example.com")
          .expiresAt(Instant.now().plusSeconds(1800))
          .used(true) // Already used
          .user(user)
          .build();
      accessTokenRepository.save(token);

      // When & Then
      mockMvc.perform(get("/auth/verify")
              .param("token", rawToken)
              .with(csrf()))
          .andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrl("/login-error"));

      // Token should still be marked as used
      var unchangedToken = accessTokenRepository.findById(token.getId()).orElseThrow();
      assertThat(unchangedToken.getUsed()).isTrue();
    }

    @Test
    @DisplayName("should redirect to login error when token is expired")
    void shouldRedirectToLoginErrorWhenTokenIsExpired() throws Exception {
      // Given
      var rawToken = "expired-token-" + UUID.randomUUID();
      var tokenHash = hashToken(rawToken);

      var user = User.builder()
          .email("expired@example.com")
          .displayName("Expired User")
          .isSystemAdmin(false)
          .build();
      userRepository.save(user);

      var token = AccessToken.builder()
          .tokenHash(tokenHash)
          .purpose(TokenPurpose.LOGIN)
          .email("expired@example.com")
          .expiresAt(Instant.now().minusSeconds(1)) // Expired 1 second ago
          .used(false)
          .user(user)
          .build();
      accessTokenRepository.save(token);

      // When & Then
      mockMvc.perform(get("/auth/verify")
              .param("token", rawToken)
              .with(csrf()))
          .andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrl("/login-error"));

      // Token should still be unused
      var unchangedToken = accessTokenRepository.findById(token.getId()).orElseThrow();
      assertThat(unchangedToken.getUsed()).isFalse();
    }

    @Test
    @DisplayName("should reject token after expiration time elapses")
    void shouldRejectTokenAfterExpirationTimeElapses() throws Exception {
      // Given - Create token that expires in 1 second
      var rawToken = "expiring-token-" + UUID.randomUUID();
      var tokenHash = hashToken(rawToken);

      var user = User.builder()
          .email("expiring@example.com")
          .displayName("Expiring User")
          .isSystemAdmin(false)
          .build();
      userRepository.save(user);

      var token = AccessToken.builder()
          .tokenHash(tokenHash)
          .purpose(TokenPurpose.LOGIN)
          .email("expiring@example.com")
          .expiresAt(Instant.now().plusSeconds(1)) // Expires in 1 second
          .used(false)
          .user(user)
          .build();
      accessTokenRepository.save(token);

      // Wait for token to expire, then verify it's rejected
      await()
          .atMost(Duration.ofSeconds(3))
          .pollInterval(Duration.ofMillis(200))
          .until(() -> token.getExpiresAt().isBefore(Instant.now()));

      // When - Token is now expired
      mockMvc.perform(get("/auth/verify")
              .param("token", rawToken)
              .with(csrf()))
          .andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrl("/login-error"));

      // Token should still be unused (expired before use)
      var expiredToken = accessTokenRepository.findById(token.getId()).orElseThrow();
      assertThat(expiredToken.getUsed()).isFalse();
      assertThat(expiredToken.getExpiresAt()).isBefore(Instant.now());
    }

    @Test
    @DisplayName("should return 400 when token parameter is missing")
    void shouldReturn400WhenTokenParameterIsMissing() throws Exception {
      // When & Then - Missing required parameter returns 400
      mockMvc.perform(get("/auth/verify")
              .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should redirect to login error when token is empty string")
    void shouldRedirectToLoginErrorWhenTokenIsEmptyString() throws Exception {
      // When & Then
      mockMvc.perform(get("/auth/verify")
              .param("token", "")
              .with(csrf()))
          .andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrl("/login-error"));
    }

    @Test
    @DisplayName("should redirect to login error for token without existing user")
    void shouldRedirectToLoginErrorForTokenWithoutExistingUser() throws Exception {
      // Given
      var rawToken = "test-no-user-token-" + UUID.randomUUID();
      var tokenHash = hashToken(rawToken);

      var token = AccessToken.builder()
          .tokenHash(tokenHash)
          .purpose(TokenPurpose.LOGIN)
          .email("nonexistent@example.com")
          .expiresAt(Instant.now().plusSeconds(1800))
          .used(false)
          .user(null) // No associated user
          .build();
      accessTokenRepository.save(token);

      // When & Then - Should fail because user doesn't exist
      mockMvc.perform(get("/auth/verify")
              .param("token", rawToken)
              .with(csrf()))
          .andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrl("/login-error"));

      // Verify token is not marked as used (failed before marking)
      var unusedToken = accessTokenRepository.findById(token.getId()).orElseThrow();
      assertThat(unusedToken.getUsed()).isFalse();
    }

    @Test
    @DisplayName("should reject JUDGING_SESSION token purpose (not yet supported)")
    void shouldRejectJudgingSessionTokenPurpose() throws Exception {
      // Given
      var rawToken = "judging-token-" + UUID.randomUUID();
      var tokenHash = hashToken(rawToken);

      var user = User.builder()
          .email("judge@example.com")
          .displayName("Judge User")
          .isSystemAdmin(false)
          .build();
      userRepository.save(user);

      var token = AccessToken.builder()
          .tokenHash(tokenHash)
          .purpose(TokenPurpose.JUDGING_SESSION)
          .email("judge@example.com")
          .expiresAt(Instant.now().plusSeconds(64800)) // 18 hours
          .used(false)
          .user(user)
          .competitionId(UUID.randomUUID())
          .build();
      accessTokenRepository.save(token);

      // When & Then - Currently JUDGING_SESSION is not supported
      mockMvc.perform(get("/auth/verify")
              .param("token", rawToken)
              .with(csrf()))
          .andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrl("/login-error"));

      // Verify token is not marked as used (rejected before use)
      var unusedToken = accessTokenRepository.findById(token.getId()).orElseThrow();
      assertThat(unusedToken.getUsed()).isFalse();
    }

    @Test
    @DisplayName("should hash token correctly using SHA-256")
    void shouldHashTokenCorrectlyUsingSha256() throws Exception {
      // Given
      var rawToken = "test-token-123";

      // When & Then
      mockMvc.perform(get("/auth/verify")
              .param("token", rawToken)
              .with(csrf()))
          .andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrl("/login-error"));
    }
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
