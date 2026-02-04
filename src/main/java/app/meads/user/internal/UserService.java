package app.meads.user.internal;

import app.meads.user.TokenPurpose;
import app.meads.user.api.EmailService;
import app.meads.user.api.UserDto;
import app.meads.user.api.UserManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing users and authentication tokens.
 * Implements the public UserManagementService API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserManagementService {

  private final UserRepository userRepository;
  private final AccessTokenRepository accessTokenRepository;
  private final EmailService emailService;
  private final SecureRandom secureRandom = new SecureRandom();

  @Value("${meads.base-url:http://localhost:8080}")
  private String baseUrl;

  @Value("${meads.magic-link.expiry-minutes:30}")
  private int magicLinkExpiryMinutes;

  @Override
  @Transactional
  public UserDto createUser(String email, String displayName, String displayCountry, boolean isSystemAdmin) {
    if (userRepository.existsByEmail(email)) {
      throw new IllegalArgumentException("User with email " + email + " already exists");
    }

    var user = User.builder()
        .email(email)
        .displayName(displayName)
        .displayCountry(displayCountry)
        .isSystemAdmin(isSystemAdmin)
        .build();

    var savedUser = userRepository.save(user);
    return toDto(savedUser);
  }

  @Override
  @Transactional
  public UserDto updateUser(UUID userId, String displayName, String displayCountry, boolean isSystemAdmin) {
    var user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

    user.setDisplayName(displayName);
    user.setDisplayCountry(displayCountry);
    user.setIsSystemAdmin(isSystemAdmin);
    user.setUpdatedAt(Instant.now());

    var savedUser = userRepository.save(user);
    return toDto(savedUser);
  }

  @Override
  @Transactional
  public void deleteUser(UUID userId) {
    if (!userRepository.existsById(userId)) {
      throw new IllegalArgumentException("User not found with ID: " + userId);
    }
    userRepository.deleteById(userId);
  }

  @Override
  public Optional<UserDto> findUserById(UUID userId) {
    return userRepository.findById(userId).map(this::toDto);
  }

  @Override
  public Optional<UserDto> findUserByEmail(String email) {
    return userRepository.findByEmail(email).map(this::toDto);
  }

  @Override
  public List<UserDto> findAllUsers() {
    return userRepository.findAll().stream()
        .map(this::toDto)
        .toList();
  }

  @Override
  @Transactional
  public void sendMagicLink(UUID userId) {
    var user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

    var magicLink = generateAndSaveMagicLinkToken(user);
    log.info("Magic link token saved to database for user: {}", user.getEmail());

    emailService.sendMagicLinkEmail(user.getEmail(), magicLink);
    log.info("Magic link sent to user: {}", user.getEmail());
  }

  @Override
  @Transactional
  public void sendBulkMagicLinks(List<UUID> userIds) {
    var recipients = userIds.stream()
        .map(userId -> {
          var user = userRepository.findById(userId)
              .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
          var magicLink = generateAndSaveMagicLinkToken(user);
          return new EmailService.EmailRecipient(user.getEmail(), magicLink);
        })
        .toList();

    emailService.sendBulkMagicLinkEmails(recipients);
    log.info("Bulk magic links sent to {} users", recipients.size());
  }

  /**
   * Generates a magic link token for a user and saves it to the database.
   *
   * @param user the user
   * @return the magic link URL
   */
  private String generateAndSaveMagicLinkToken(User user) {
    // Generate a secure random token
    byte[] tokenBytes = new byte[32];
    secureRandom.nextBytes(tokenBytes);
    String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

    // Hash the token for storage
    String tokenHash = hashToken(rawToken);

    // Create and save the access token
    var expiresAt = Instant.now().plus(Duration.ofMinutes(magicLinkExpiryMinutes));
    var accessToken = AccessToken.builder()
        .tokenHash(tokenHash)
        .purpose(TokenPurpose.LOGIN)
        .email(user.getEmail())
        .expiresAt(expiresAt)
        .user(user)
        .build();

    var savedToken = accessTokenRepository.save(accessToken);
    log.debug("Saved access token: id={}, tokenHash={}, expiresAt={}",
        savedToken.getId(), savedToken.getTokenHash(), savedToken.getExpiresAt());

    // Build the magic link URL
    return baseUrl + "/auth/verify?token=" + rawToken;
  }

  /**
   * Hashes a token using SHA-256.
   *
   * @param token the raw token
   * @return the hashed token
   */
  private String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 algorithm not available", e);
    }
  }

  /**
   * Converts a User entity to a UserDto.
   *
   * @param user the user entity
   * @return the user DTO
   */
  private UserDto toDto(User user) {
    return new UserDto(
        user.getId(),
        user.getEmail(),
        user.getDisplayName(),
        user.getDisplayCountry(),
        user.getIsSystemAdmin(),
        user.getCreatedAt(),
        user.getUpdatedAt()
    );
  }
}
