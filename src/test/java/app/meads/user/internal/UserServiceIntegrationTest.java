package app.meads.user.internal;

import app.meads.TestcontainersConfiguration;
import app.meads.user.TokenPurpose;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@DisplayName("UserService Integration Tests")
class UserServiceIntegrationTest {

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

  @Nested
  @DisplayName("@Transactional Behavior Tests")
  class TransactionalTests {

    @Test
    @DisplayName("should commit user creation transaction")
    void shouldCommitUserCreationTransaction() {
      // When
      var createdUser = userService.createUser("transactional@example.com", "Transactional User", "USA", false);

      // Then - User should exist in database
      var foundUser = userRepository.findById(createdUser.id());
      assertThat(foundUser).isPresent();
      assertThat(foundUser.get().getEmail()).isEqualTo("transactional@example.com");
    }

    @Test
    @DisplayName("should rollback transaction on duplicate email")
    void shouldRollbackTransactionOnDuplicateEmail() {
      // Given
      userService.createUser("duplicate@example.com", "User 1", "USA", false);

      // When & Then
      assertThatThrownBy(() -> userService.createUser("duplicate@example.com", "User 2", "Canada", false))
          .isInstanceOf(IllegalArgumentException.class);

      // Verify only one user exists
      var allUsers = userRepository.findAll();
      assertThat(allUsers).hasSize(1);
      assertThat(allUsers.get(0).getDisplayName()).isEqualTo("User 1");
    }

    @Test
    @DisplayName("should commit user update transaction")
    void shouldCommitUserUpdateTransaction() {
      // Given
      var user = User.builder()
          .email("update@example.com")
          .displayName("Old Name")
          .isSystemAdmin(false)
          .build();
      var savedUser = userRepository.save(user);

      // When
      userService.updateUser(savedUser.getId(), "New Name", "Canada", true);

      // Then - Changes should be persisted
      var updatedUser = userRepository.findById(savedUser.getId());
      assertThat(updatedUser).isPresent();
      assertThat(updatedUser.get().getDisplayName()).isEqualTo("New Name");
      assertThat(updatedUser.get().getDisplayCountry()).isEqualTo("Canada");
      assertThat(updatedUser.get().getIsSystemAdmin()).isTrue();
    }

    @Test
    @DisplayName("should commit user deletion transaction")
    void shouldCommitUserDeletionTransaction() {
      // Given
      var user = User.builder()
          .email("delete@example.com")
          .displayName("To Delete")
          .isSystemAdmin(false)
          .build();
      var savedUser = userRepository.save(user);

      // When
      userService.deleteUser(savedUser.getId());

      // Then - User should not exist
      var deletedUser = userRepository.findById(savedUser.getId());
      assertThat(deletedUser).isEmpty();
    }

    @Test
    @DisplayName("should commit magic link token creation transaction")
    void shouldCommitMagicLinkTokenCreationTransaction() {
      // Given
      var user = User.builder()
          .email("magiclink@example.com")
          .displayName("Magic Link User")
          .isSystemAdmin(false)
          .build();
      var savedUser = userRepository.save(user);

      // When
      userService.sendMagicLink(savedUser.getId());

      // Then - Token should exist in database
      var tokens = accessTokenRepository.findByEmailAndUsedFalse("magiclink@example.com");
      assertThat(tokens).hasSize(1);
      assertThat(tokens.get(0).getEmail()).isEqualTo("magiclink@example.com");
      assertThat(tokens.get(0).getPurpose()).isEqualTo(TokenPurpose.LOGIN);
      assertThat(tokens.get(0).getUsed()).isFalse();
    }

    @Test
    @DisplayName("should commit bulk magic link tokens creation transaction")
    void shouldCommitBulkMagicLinkTokensCreationTransaction() {
      // Given
      var user1 = User.builder()
          .email("bulk1@example.com")
          .displayName("Bulk User 1")
          .isSystemAdmin(false)
          .build();
      var user2 = User.builder()
          .email("bulk2@example.com")
          .displayName("Bulk User 2")
          .isSystemAdmin(false)
          .build();
      var user3 = User.builder()
          .email("bulk3@example.com")
          .displayName("Bulk User 3")
          .isSystemAdmin(false)
          .build();
      var savedUser1 = userRepository.save(user1);
      var savedUser2 = userRepository.save(user2);
      var savedUser3 = userRepository.save(user3);

      var userIds = List.of(savedUser1.getId(), savedUser2.getId(), savedUser3.getId());

      // When
      userService.sendBulkMagicLinks(userIds);

      // Then - All tokens should exist in database
      var tokens1 = accessTokenRepository.findByEmailAndUsedFalse("bulk1@example.com");
      var tokens2 = accessTokenRepository.findByEmailAndUsedFalse("bulk2@example.com");
      var tokens3 = accessTokenRepository.findByEmailAndUsedFalse("bulk3@example.com");

      assertThat(tokens1).hasSize(1);
      assertThat(tokens2).hasSize(1);
      assertThat(tokens3).hasSize(1);
    }
  }

  @Nested
  @DisplayName("Cascade and Relationship Tests")
  class RelationshipTests {

    @Test
    @DisplayName("should create token with user relationship")
    void shouldCreateTokenWithUserRelationship() {
      // Given
      var user = User.builder()
          .email("relationship@example.com")
          .displayName("Relationship User")
          .isSystemAdmin(false)
          .build();
      var savedUser = userRepository.save(user);

      // When
      userService.sendMagicLink(savedUser.getId());

      // Then - Token should reference user
      var tokens = accessTokenRepository.findByEmailAndUsedFalse("relationship@example.com");
      assertThat(tokens).hasSize(1);

      // Fetch user separately to avoid lazy loading issue
      var tokenUserId = tokens.get(0).getUser() != null ? tokens.get(0).getUser().getId() : null;
      assertThat(tokenUserId).isNotNull().isEqualTo(savedUser.getId());

      // Verify by looking up user
      var tokenUser = userRepository.findById(tokenUserId);
      assertThat(tokenUser).isPresent();
      assertThat(tokenUser.get().getEmail()).isEqualTo("relationship@example.com");
    }

    @Test
    @DisplayName("should fail to delete user with associated tokens (FK constraint)")
    void shouldFailToDeleteUserWithAssociatedTokens() {
      // Given
      var user = User.builder()
          .email("cascade@example.com")
          .displayName("Cascade User")
          .isSystemAdmin(false)
          .build();
      var savedUser = userRepository.save(user);

      userService.sendMagicLink(savedUser.getId());

      var tokensBefore = accessTokenRepository.findByEmailAndUsedFalse("cascade@example.com");
      assertThat(tokensBefore).hasSize(1);

      // When & Then - Should fail due to foreign key constraint
      assertThatThrownBy(() -> userService.deleteUser(savedUser.getId()))
          .isInstanceOf(Exception.class); // DataIntegrityViolationException or similar

      // User should still exist
      var userStillExists = userRepository.findById(savedUser.getId());
      assertThat(userStillExists).isPresent();
    }
  }

  @Nested
  @DisplayName("Concurrent Access Tests")
  class ConcurrentAccessTests {

    @Test
    @DisplayName("should handle multiple magic link requests for same user")
    void shouldHandleMultipleMagicLinkRequestsForSameUser() {
      // Given
      var user = User.builder()
          .email("concurrent@example.com")
          .displayName("Concurrent User")
          .isSystemAdmin(false)
          .build();
      var savedUser = userRepository.save(user);

      // When - Send multiple magic links
      userService.sendMagicLink(savedUser.getId());
      userService.sendMagicLink(savedUser.getId());
      userService.sendMagicLink(savedUser.getId());

      // Then - All tokens should exist
      var tokens = accessTokenRepository.findByEmailAndUsedFalse("concurrent@example.com");
      assertThat(tokens).hasSize(3);

      // All tokens should be unique
      var hashes = tokens.stream().map(AccessToken::getTokenHash).toList();
      assertThat(hashes).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("should isolate tokens by email")
    void shouldIsolateTokensByEmail() {
      // Given
      var user1 = User.builder()
          .email("isolate1@example.com")
          .displayName("User 1")
          .isSystemAdmin(false)
          .build();
      var user2 = User.builder()
          .email("isolate2@example.com")
          .displayName("User 2")
          .isSystemAdmin(false)
          .build();
      var savedUser1 = userRepository.save(user1);
      var savedUser2 = userRepository.save(user2);

      // When
      userService.sendMagicLink(savedUser1.getId());
      userService.sendMagicLink(savedUser2.getId());

      // Then - Each user should have their own token
      var tokens1 = accessTokenRepository.findByEmailAndUsedFalse("isolate1@example.com");
      var tokens2 = accessTokenRepository.findByEmailAndUsedFalse("isolate2@example.com");

      assertThat(tokens1).hasSize(1);
      assertThat(tokens2).hasSize(1);
      assertThat(tokens1.get(0).getTokenHash()).isNotEqualTo(tokens2.get(0).getTokenHash());
    }
  }

  @Nested
  @DisplayName("Data Integrity Tests")
  class DataIntegrityTests {

    @Test
    @DisplayName("should maintain email uniqueness constraint")
    void shouldMaintainEmailUniquenessConstraint() {
      // Given
      userService.createUser("unique@example.com", "User 1", "USA", false);

      // When & Then - Attempting to create duplicate should fail at service layer
      assertThatThrownBy(() -> userService.createUser("unique@example.com", "User 2", "Canada", false))
          .isInstanceOf(IllegalArgumentException.class);

      // Verify only one user exists
      var users = userRepository.findAll();
      assertThat(users).hasSize(1);
    }

    @Test
    @DisplayName("should generate unique token hashes")
    void shouldGenerateUniqueTokenHashes() {
      // Given
      var user = User.builder()
          .email("hashes@example.com")
          .displayName("Hash User")
          .isSystemAdmin(false)
          .build();
      var savedUser = userRepository.save(user);

      // When - Generate multiple tokens
      userService.sendMagicLink(savedUser.getId());
      userService.sendMagicLink(savedUser.getId());
      userService.sendMagicLink(savedUser.getId());

      // Then - All hashes should be unique
      var tokens = accessTokenRepository.findByEmailAndUsedFalse("hashes@example.com");
      var hashes = tokens.stream().map(AccessToken::getTokenHash).toList();

      assertThat(hashes).hasSize(3);
      assertThat(hashes).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("should preserve timestamps on update")
    void shouldPreserveTimestampsOnUpdate() {
      // Given
      var user = User.builder()
          .email("timestamp@example.com")
          .displayName("Old Name")
          .isSystemAdmin(false)
          .build();
      var savedUser = userRepository.save(user);
      var originalCreatedAt = savedUser.getCreatedAt();

      // Wait a bit to ensure updatedAt would be different
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      // When
      userService.updateUser(savedUser.getId(), "New Name", "USA", false);

      // Then - createdAt should be preserved, updatedAt should be newer
      var updatedUser = userRepository.findById(savedUser.getId()).orElseThrow();
      assertThat(updatedUser.getCreatedAt()).isEqualTo(originalCreatedAt);
      assertThat(updatedUser.getUpdatedAt()).isAfterOrEqualTo(originalCreatedAt);
    }
  }

  @Nested
  @DisplayName("Query Method Tests")
  class QueryMethodTests {

    @Test
    @DisplayName("should find user by email case-sensitive")
    void shouldFindUserByEmailCaseSensitive() {
      // Given
      userService.createUser("CaseSensitive@Example.COM", "Case User", "USA", false);

      // When & Then
      var foundExact = userService.findUserByEmail("CaseSensitive@Example.COM");
      assertThat(foundExact).isPresent();

      var foundWrongCase = userService.findUserByEmail("casesensitive@example.com");
      assertThat(foundWrongCase).isEmpty();
    }

    @Test
    @DisplayName("should find all users in correct order")
    void shouldFindAllUsersInCorrectOrder() {
      // Given
      userService.createUser("user1@example.com", "User 1", "USA", false);
      userService.createUser("user2@example.com", "User 2", "Canada", true);
      userService.createUser("user3@example.com", "User 3", "UK", false);

      // When
      var allUsers = userService.findAllUsers();

      // Then
      assertThat(allUsers).hasSize(3);
      assertThat(allUsers.stream().map(dto -> dto.email()))
          .containsExactlyInAnyOrder("user1@example.com", "user2@example.com", "user3@example.com");
    }

    @Test
    @DisplayName("should check email existence correctly")
    void shouldCheckEmailExistenceCorrectly() {
      // Given
      userService.createUser("exists@example.com", "Exists User", "USA", false);

      // When & Then
      var exists = userRepository.existsByEmail("exists@example.com");
      assertThat(exists).isTrue();

      var notExists = userRepository.existsByEmail("notexists@example.com");
      assertThat(notExists).isFalse();
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("should throw exception when sending magic link to non-existent user")
    void shouldThrowExceptionWhenSendingMagicLinkToNonExistentUser() {
      // Given
      var nonExistentId = java.util.UUID.randomUUID();

      // When & Then
      assertThatThrownBy(() -> userService.sendMagicLink(nonExistentId))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("User not found with ID: " + nonExistentId);
    }

    @Test
    @DisplayName("should throw exception when bulk sending to non-existent user")
    void shouldThrowExceptionWhenBulkSendingToNonExistentUser() {
      // Given
      var user1 = User.builder()
          .email("exists@example.com")
          .displayName("Exists User")
          .isSystemAdmin(false)
          .build();
      var savedUser1 = userRepository.save(user1);
      var nonExistentId = java.util.UUID.randomUUID();

      var userIds = List.of(savedUser1.getId(), nonExistentId);

      // When & Then
      assertThatThrownBy(() -> userService.sendBulkMagicLinks(userIds))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("User not found with ID: " + nonExistentId);
    }

    @Test
    @DisplayName("should throw exception when updating non-existent user")
    void shouldThrowExceptionWhenUpdatingNonExistentUser() {
      // Given
      var nonExistentId = java.util.UUID.randomUUID();

      // When & Then
      assertThatThrownBy(() -> userService.updateUser(nonExistentId, "New Name", "USA", false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("User not found with ID: " + nonExistentId);
    }

    @Test
    @DisplayName("should throw exception when deleting non-existent user")
    void shouldThrowExceptionWhenDeletingNonExistentUser() {
      // Given
      var nonExistentId = java.util.UUID.randomUUID();

      // When & Then
      assertThatThrownBy(() -> userService.deleteUser(nonExistentId))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("User not found with ID: " + nonExistentId);
    }
  }
}
