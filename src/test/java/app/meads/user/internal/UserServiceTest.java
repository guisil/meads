package app.meads.user.internal;

import app.meads.user.TokenPurpose;
import app.meads.user.api.EmailService;
import app.meads.user.api.EmailService.EmailRecipient;
import app.meads.user.api.UserDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private AccessTokenRepository accessTokenRepository;

  @Mock
  private EmailService emailService;

  @InjectMocks
  private UserService userService;

  @Captor
  private ArgumentCaptor<User> userCaptor;

  @Captor
  private ArgumentCaptor<AccessToken> tokenCaptor;

  @Captor
  private ArgumentCaptor<String> stringCaptor;

  @Captor
  private ArgumentCaptor<List<EmailRecipient>> recipientsCaptor;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(userService, "magicLinkExpiryMinutes", 30);
    ReflectionTestUtils.setField(userService, "baseUrl", "http://localhost:8080");
  }

  @Nested
  @DisplayName("createUser()")
  class CreateUserTests {

    @Test
    @DisplayName("should create user with all fields")
    void shouldCreateUserWithAllFields() {
      // Given
      var savedUser = User.builder()
          .id(UUID.randomUUID())
          .email("test@example.com")
          .displayName("Test User")
          .displayCountry("USA")
          .isSystemAdmin(false)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
      when(userRepository.save(any(User.class))).thenReturn(savedUser);

      // When
      var result = userService.createUser("test@example.com", "Test User", "USA", false);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.email()).isEqualTo("test@example.com");
      assertThat(result.displayName()).isEqualTo("Test User");
      assertThat(result.displayCountry()).isEqualTo("USA");
      assertThat(result.isSystemAdmin()).isFalse();

      verify(userRepository).existsByEmail("test@example.com");
      verify(userRepository).save(userCaptor.capture());

      var capturedUser = userCaptor.getValue();
      assertThat(capturedUser.getEmail()).isEqualTo("test@example.com");
      assertThat(capturedUser.getDisplayName()).isEqualTo("Test User");
      assertThat(capturedUser.getDisplayCountry()).isEqualTo("USA");
      assertThat(capturedUser.getIsSystemAdmin()).isFalse();
    }

    @Test
    @DisplayName("should create user with minimal fields")
    void shouldCreateUserWithMinimalFields() {
      // Given
      var savedUser = User.builder()
          .id(UUID.randomUUID())
          .email("minimal@example.com")
          .isSystemAdmin(false)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      when(userRepository.existsByEmail("minimal@example.com")).thenReturn(false);
      when(userRepository.save(any(User.class))).thenReturn(savedUser);

      // When
      var result = userService.createUser("minimal@example.com", null, null, false);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.email()).isEqualTo("minimal@example.com");
      assertThat(result.displayName()).isNull();
      assertThat(result.displayCountry()).isNull();

      verify(userRepository).save(userCaptor.capture());
      var capturedUser = userCaptor.getValue();
      assertThat(capturedUser.getDisplayName()).isNull();
      assertThat(capturedUser.getDisplayCountry()).isNull();
    }

    @Test
    @DisplayName("should throw exception when email already exists")
    void shouldThrowExceptionWhenEmailExists() {
      // Given
      when(userRepository.existsByEmail("duplicate@example.com")).thenReturn(true);

      // When & Then
      assertThatThrownBy(() -> userService.createUser("duplicate@example.com", "Test User", "USA", false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("User with email duplicate@example.com already exists");

      verify(userRepository).existsByEmail("duplicate@example.com");
      verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("should create system admin user")
    void shouldCreateSystemAdminUser() {
      // Given
      var savedUser = User.builder()
          .id(UUID.randomUUID())
          .email("admin@example.com")
          .displayName("Admin User")
          .isSystemAdmin(true)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
      when(userRepository.save(any(User.class))).thenReturn(savedUser);

      // When
      var result = userService.createUser("admin@example.com", "Admin User", null, true);

      // Then
      assertThat(result.isSystemAdmin()).isTrue();
      verify(userRepository).save(userCaptor.capture());
      assertThat(userCaptor.getValue().getIsSystemAdmin()).isTrue();
    }
  }

  @Nested
  @DisplayName("updateUser()")
  class UpdateUserTests {

    @Test
    @DisplayName("should update existing user")
    void shouldUpdateExistingUser() {
      // Given
      var userId = UUID.randomUUID();
      var existingUser = User.builder()
          .id(userId)
          .email("old@example.com")
          .displayName("Old Name")
          .displayCountry("USA")
          .isSystemAdmin(false)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      var updatedUser = User.builder()
          .id(userId)
          .email("old@example.com")
          .displayName("New Name")
          .displayCountry("Canada")
          .isSystemAdmin(true)
          .createdAt(existingUser.getCreatedAt())
          .updatedAt(Instant.now())
          .build();

      when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
      when(userRepository.save(any(User.class))).thenReturn(updatedUser);

      // When
      var result = userService.updateUser(userId, "New Name", "Canada", true);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.displayName()).isEqualTo("New Name");
      assertThat(result.displayCountry()).isEqualTo("Canada");
      assertThat(result.isSystemAdmin()).isTrue();

      verify(userRepository).findById(userId);
      verify(userRepository).save(existingUser);
    }

    @Test
    @DisplayName("should throw exception when user not found")
    void shouldThrowExceptionWhenUserNotFound() {
      // Given
      var userId = UUID.randomUUID();
      when(userRepository.findById(userId)).thenReturn(Optional.empty());

      // When & Then
      assertThatThrownBy(() -> userService.updateUser(userId, "Test User", "USA", false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("User not found with ID: " + userId);

      verify(userRepository).findById(userId);
      verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("should allow clearing optional fields")
    void shouldAllowClearingOptionalFields() {
      // Given
      var userId = UUID.randomUUID();
      var existingUser = User.builder()
          .id(userId)
          .email("test@example.com")
          .displayName("Test User")
          .displayCountry("USA")
          .isSystemAdmin(false)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      var updatedUser = User.builder()
          .id(userId)
          .email("test@example.com")
          .displayName(null)
          .displayCountry(null)
          .isSystemAdmin(false)
          .createdAt(existingUser.getCreatedAt())
          .updatedAt(Instant.now())
          .build();

      when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
      when(userRepository.save(any(User.class))).thenReturn(updatedUser);

      // When
      var result = userService.updateUser(userId, null, null, false);

      // Then
      assertThat(result.displayName()).isNull();
      assertThat(result.displayCountry()).isNull();
      verify(userRepository).save(existingUser);
    }
  }

  @Nested
  @DisplayName("deleteUser()")
  class DeleteUserTests {

    @Test
    @DisplayName("should delete existing user")
    void shouldDeleteExistingUser() {
      // Given
      var userId = UUID.randomUUID();
      when(userRepository.existsById(userId)).thenReturn(true);

      // When
      userService.deleteUser(userId);

      // Then
      verify(userRepository).existsById(userId);
      verify(userRepository).deleteById(userId);
    }

    @Test
    @DisplayName("should throw exception when user not found")
    void shouldThrowExceptionWhenUserNotFound() {
      // Given
      var userId = UUID.randomUUID();
      when(userRepository.existsById(userId)).thenReturn(false);

      // When & Then
      assertThatThrownBy(() -> userService.deleteUser(userId))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("User not found with ID: " + userId);

      verify(userRepository).existsById(userId);
      verify(userRepository, never()).deleteById(any(UUID.class));
    }
  }

  @Nested
  @DisplayName("findUserById()")
  class FindUserByIdTests {

    @Test
    @DisplayName("should find user by id")
    void shouldFindUserById() {
      // Given
      var userId = UUID.randomUUID();
      var user = User.builder()
          .id(userId)
          .email("test@example.com")
          .displayName("Test User")
          .displayCountry("USA")
          .isSystemAdmin(false)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      when(userRepository.findById(userId)).thenReturn(Optional.of(user));

      // When
      var result = userService.findUserById(userId);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().id()).isEqualTo(userId);
      assertThat(result.get().email()).isEqualTo("test@example.com");
      assertThat(result.get().displayName()).isEqualTo("Test User");

      verify(userRepository).findById(userId);
    }

    @Test
    @DisplayName("should return empty when user not found")
    void shouldReturnEmptyWhenUserNotFound() {
      // Given
      var userId = UUID.randomUUID();
      when(userRepository.findById(userId)).thenReturn(Optional.empty());

      // When
      var result = userService.findUserById(userId);

      // Then
      assertThat(result).isEmpty();
      verify(userRepository).findById(userId);
    }
  }

  @Nested
  @DisplayName("findUserByEmail()")
  class FindUserByEmailTests {

    @Test
    @DisplayName("should find user by email")
    void shouldFindUserByEmail() {
      // Given
      var user = User.builder()
          .id(UUID.randomUUID())
          .email("test@example.com")
          .displayName("Test User")
          .displayCountry("USA")
          .isSystemAdmin(false)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

      // When
      var result = userService.findUserByEmail("test@example.com");

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().email()).isEqualTo("test@example.com");
      assertThat(result.get().displayName()).isEqualTo("Test User");

      verify(userRepository).findByEmail("test@example.com");
    }

    @Test
    @DisplayName("should return empty when user not found")
    void shouldReturnEmptyWhenUserNotFound() {
      // Given
      when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

      // When
      var result = userService.findUserByEmail("notfound@example.com");

      // Then
      assertThat(result).isEmpty();
      verify(userRepository).findByEmail("notfound@example.com");
    }
  }

  @Nested
  @DisplayName("findAllUsers()")
  class FindAllUsersTests {

    @Test
    @DisplayName("should find all users")
    void shouldFindAllUsers() {
      // Given
      var user1 = User.builder()
          .id(UUID.randomUUID())
          .email("user1@example.com")
          .displayName("User 1")
          .isSystemAdmin(false)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      var user2 = User.builder()
          .id(UUID.randomUUID())
          .email("user2@example.com")
          .displayName("User 2")
          .isSystemAdmin(true)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      when(userRepository.findAll()).thenReturn(List.of(user1, user2));

      // When
      var result = userService.findAllUsers();

      // Then
      assertThat(result).hasSize(2);
      assertThat(result.get(0).email()).isEqualTo("user1@example.com");
      assertThat(result.get(1).email()).isEqualTo("user2@example.com");

      verify(userRepository).findAll();
    }

    @Test
    @DisplayName("should return empty list when no users")
    void shouldReturnEmptyListWhenNoUsers() {
      // Given
      when(userRepository.findAll()).thenReturn(List.of());

      // When
      var result = userService.findAllUsers();

      // Then
      assertThat(result).isEmpty();
      verify(userRepository).findAll();
    }
  }

  @Nested
  @DisplayName("sendMagicLink()")
  class SendMagicLinkTests {

    @Test
    @DisplayName("should generate and send magic link for existing user")
    void shouldGenerateAndSendMagicLinkForExistingUser() {
      // Given
      var userId = UUID.randomUUID();
      var user = User.builder()
          .id(userId)
          .email("test@example.com")
          .displayName("Test User")
          .isSystemAdmin(false)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      when(userRepository.findById(userId)).thenReturn(Optional.of(user));
      when(accessTokenRepository.save(any(AccessToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

      // When
      userService.sendMagicLink(userId);

      // Then
      verify(userRepository).findById(userId);
      verify(accessTokenRepository).save(tokenCaptor.capture());
      verify(emailService).sendMagicLinkEmail(eq("test@example.com"), stringCaptor.capture());

      var capturedToken = tokenCaptor.getValue();
      assertThat(capturedToken.getEmail()).isEqualTo("test@example.com");
      assertThat(capturedToken.getPurpose()).isEqualTo(TokenPurpose.LOGIN);
      assertThat(capturedToken.getTokenHash()).isNotNull();
      assertThat(capturedToken.getUsed()).isFalse();
      assertThat(capturedToken.getExpiresAt()).isAfter(Instant.now());
      assertThat(capturedToken.getUser()).isEqualTo(user);

      var capturedMagicLink = stringCaptor.getValue();
      assertThat(capturedMagicLink).startsWith("http://localhost:8080/auth/verify?token=");
    }

    @Test
    @DisplayName("should throw exception when user not found")
    void shouldThrowExceptionWhenUserNotFound() {
      // Given
      var userId = UUID.randomUUID();
      when(userRepository.findById(userId)).thenReturn(Optional.empty());

      // When & Then
      assertThatThrownBy(() -> userService.sendMagicLink(userId))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("User not found with ID: " + userId);

      verify(userRepository).findById(userId);
      verify(accessTokenRepository, never()).save(any(AccessToken.class));
      verify(emailService, never()).sendMagicLinkEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("should generate unique tokens for multiple calls")
    void shouldGenerateUniqueTokensForMultipleCalls() {
      // Given
      var userId = UUID.randomUUID();
      var user = User.builder()
          .id(userId)
          .email("test@example.com")
          .displayName("Test User")
          .isSystemAdmin(false)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      when(userRepository.findById(userId)).thenReturn(Optional.of(user));
      when(accessTokenRepository.save(any(AccessToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

      // When
      userService.sendMagicLink(userId);
      userService.sendMagicLink(userId);

      // Then
      verify(accessTokenRepository, times(2)).save(tokenCaptor.capture());
      var tokens = tokenCaptor.getAllValues();

      assertThat(tokens).hasSize(2);
      assertThat(tokens.get(0).getTokenHash()).isNotEqualTo(tokens.get(1).getTokenHash());
    }

    @Test
    @DisplayName("should generate URL-safe tokens")
    void shouldGenerateUrlSafeTokens() {
      // Given
      var userId = UUID.randomUUID();
      var user = User.builder()
          .id(userId)
          .email("test@example.com")
          .displayName("Test User")
          .isSystemAdmin(false)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      when(userRepository.findById(userId)).thenReturn(Optional.of(user));
      when(accessTokenRepository.save(any(AccessToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

      // When
      userService.sendMagicLink(userId);

      // Then
      verify(emailService).sendMagicLinkEmail(anyString(), stringCaptor.capture());
      var magicLink = stringCaptor.getValue();

      // Extract token from URL
      var token = magicLink.substring(magicLink.indexOf("token=") + 6);
      assertThat(token).matches("^[A-Za-z0-9_-]+$"); // URL-safe Base64
    }
  }

  @Nested
  @DisplayName("sendBulkMagicLinks()")
  class SendBulkMagicLinksTests {

    @Test
    @DisplayName("should generate and send magic links for multiple users")
    void shouldGenerateAndSendMagicLinksForMultipleUsers() {
      // Given
      var userId1 = UUID.randomUUID();
      var userId2 = UUID.randomUUID();
      var userId3 = UUID.randomUUID();

      var user1 = User.builder()
          .id(userId1)
          .email("user1@example.com")
          .displayName("User 1")
          .isSystemAdmin(false)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      var user2 = User.builder()
          .id(userId2)
          .email("user2@example.com")
          .displayName("User 2")
          .isSystemAdmin(false)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      var user3 = User.builder()
          .id(userId3)
          .email("user3@example.com")
          .displayName("User 3")
          .isSystemAdmin(false)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      var userIds = List.of(userId1, userId2, userId3);

      when(userRepository.findById(userId1)).thenReturn(Optional.of(user1));
      when(userRepository.findById(userId2)).thenReturn(Optional.of(user2));
      when(userRepository.findById(userId3)).thenReturn(Optional.of(user3));
      when(accessTokenRepository.save(any(AccessToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

      // When
      userService.sendBulkMagicLinks(userIds);

      // Then
      verify(accessTokenRepository, times(3)).save(any(AccessToken.class));
      verify(emailService).sendBulkMagicLinkEmails(recipientsCaptor.capture());

      var recipients = recipientsCaptor.getValue();
      assertThat(recipients).hasSize(3);
      assertThat(recipients.get(0).email()).isEqualTo("user1@example.com");
      assertThat(recipients.get(1).email()).isEqualTo("user2@example.com");
      assertThat(recipients.get(2).email()).isEqualTo("user3@example.com");

      recipients.forEach(recipient -> {
        assertThat(recipient.magicLink()).startsWith("http://localhost:8080/auth/verify?token=");
      });
    }

    @Test
    @DisplayName("should handle empty user list")
    void shouldHandleEmptyUserList() {
      // Given
      var userIds = List.<UUID>of();

      // When
      userService.sendBulkMagicLinks(userIds);

      // Then
      verify(accessTokenRepository, never()).save(any(AccessToken.class));
      verify(emailService).sendBulkMagicLinkEmails(recipientsCaptor.capture());

      var recipients = recipientsCaptor.getValue();
      assertThat(recipients).isEmpty();
    }

    @Test
    @DisplayName("should generate unique tokens for each user in bulk")
    void shouldGenerateUniqueTokensForEachUserInBulk() {
      // Given
      var userId1 = UUID.randomUUID();
      var userId2 = UUID.randomUUID();

      var user1 = User.builder()
          .id(userId1)
          .email("user1@example.com")
          .displayName("User 1")
          .isSystemAdmin(false)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      var user2 = User.builder()
          .id(userId2)
          .email("user2@example.com")
          .displayName("User 2")
          .isSystemAdmin(false)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      var userIds = List.of(userId1, userId2);

      when(userRepository.findById(userId1)).thenReturn(Optional.of(user1));
      when(userRepository.findById(userId2)).thenReturn(Optional.of(user2));
      when(accessTokenRepository.save(any(AccessToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

      // When
      userService.sendBulkMagicLinks(userIds);

      // Then
      verify(accessTokenRepository, times(2)).save(tokenCaptor.capture());
      var tokens = tokenCaptor.getAllValues();

      assertThat(tokens).hasSize(2);
      assertThat(tokens.get(0).getTokenHash()).isNotEqualTo(tokens.get(1).getTokenHash());
    }

    @Test
    @DisplayName("should throw exception when any user not found")
    void shouldThrowExceptionWhenAnyUserNotFound() {
      // Given
      var userId1 = UUID.randomUUID();
      var userId2 = UUID.randomUUID();

      var user1 = User.builder()
          .id(userId1)
          .email("user1@example.com")
          .displayName("User 1")
          .isSystemAdmin(false)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      var userIds = List.of(userId1, userId2);

      when(userRepository.findById(userId1)).thenReturn(Optional.of(user1));
      when(userRepository.findById(userId2)).thenReturn(Optional.empty());

      // Mock save to return token with ID set
      when(accessTokenRepository.save(any(AccessToken.class))).thenAnswer(invocation -> {
        var token = (AccessToken) invocation.getArgument(0);
        return AccessToken.builder()
            .id(UUID.randomUUID())
            .tokenHash(token.getTokenHash())
            .purpose(token.getPurpose())
            .email(token.getEmail())
            .expiresAt(token.getExpiresAt())
            .used(token.getUsed())
            .user(token.getUser())
            .createdAt(Instant.now())
            .build();
      });

      // When & Then
      assertThatThrownBy(() -> userService.sendBulkMagicLinks(userIds))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("User not found with ID: " + userId2);

      verify(emailService, never()).sendBulkMagicLinkEmails(any());
    }
  }
}
