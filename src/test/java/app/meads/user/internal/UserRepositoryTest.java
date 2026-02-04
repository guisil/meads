package app.meads.user.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import app.meads.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for UserRepository.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@ActiveProfiles("test")
class UserRepositoryTest {

  @Autowired
  private UserRepository userRepository;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
  }

  @Test
  void shouldSaveAndFindUserById() {
    // Given
    User user = User.builder()
        .email("test@example.com")
        .displayName("Test User")
        .displayCountry("US")
        .build();

    // When
    User savedUser = userRepository.save(user);
    Optional<User> foundUser = userRepository.findById(savedUser.getId());

    // Then
    assertThat(foundUser).isPresent();
    assertThat(foundUser.get().getEmail()).isEqualTo("test@example.com");
    assertThat(foundUser.get().getDisplayName()).isEqualTo("Test User");
    assertThat(foundUser.get().getDisplayCountry()).isEqualTo("US");
    assertThat(foundUser.get().getIsSystemAdmin()).isFalse();
    assertThat(foundUser.get().getCreatedAt()).isNotNull();
    assertThat(foundUser.get().getUpdatedAt()).isNotNull();
  }

  @Test
  void shouldFindUserByEmail() {
    // Given
    User user = User.builder()
        .email("john@example.com")
        .displayName("John Doe")
        .build();
    userRepository.save(user);

    // When
    Optional<User> foundUser = userRepository.findByEmail("john@example.com");

    // Then
    assertThat(foundUser).isPresent();
    assertThat(foundUser.get().getEmail()).isEqualTo("john@example.com");
    assertThat(foundUser.get().getDisplayName()).isEqualTo("John Doe");
  }

  @Test
  void shouldReturnEmptyWhenUserEmailNotFound() {
    // Given
    // No users in database

    // When
    Optional<User> foundUser = userRepository.findByEmail("nonexistent@example.com");

    // Then
    assertThat(foundUser).isEmpty();
  }

  @Test
  void shouldReturnTrueWhenUserExistsByEmail() {
    // Given
    User user = User.builder()
        .email("exists@example.com")
        .build();
    userRepository.save(user);

    // When
    boolean exists = userRepository.existsByEmail("exists@example.com");

    // Then
    assertThat(exists).isTrue();
  }

  @Test
  void shouldReturnFalseWhenUserDoesNotExistByEmail() {
    // Given
    // No users in database

    // When
    boolean exists = userRepository.existsByEmail("nonexistent@example.com");

    // Then
    assertThat(exists).isFalse();
  }

  @Test
  void shouldSaveUserWithSystemAdminFlag() {
    // Given
    User admin = User.builder()
        .email("admin@example.com")
        .displayName("Admin User")
        .isSystemAdmin(true)
        .build();

    // When
    User savedAdmin = userRepository.save(admin);
    Optional<User> foundAdmin = userRepository.findById(savedAdmin.getId());

    // Then
    assertThat(foundAdmin).isPresent();
    assertThat(foundAdmin.get().getIsSystemAdmin()).isTrue();
  }

  @Test
  void shouldSaveUserWithMinimalFields() {
    // Given
    User user = User.builder()
        .email("minimal@example.com")
        .build();

    // When
    User savedUser = userRepository.save(user);
    Optional<User> foundUser = userRepository.findById(savedUser.getId());

    // Then
    assertThat(foundUser).isPresent();
    assertThat(foundUser.get().getEmail()).isEqualTo("minimal@example.com");
    assertThat(foundUser.get().getDisplayName()).isNull();
    assertThat(foundUser.get().getDisplayCountry()).isNull();
    assertThat(foundUser.get().getIsSystemAdmin()).isFalse();
  }

  @Test
  void shouldUpdateUserFields() {
    // Given
    User user = User.builder()
        .email("update@example.com")
        .displayName("Original Name")
        .build();
    User savedUser = userRepository.save(user);

    // When
    savedUser.setDisplayName("Updated Name");
    savedUser.setDisplayCountry("PT");
    savedUser.setUpdatedAt(Instant.now());
    User updatedUser = userRepository.save(savedUser);

    // Then
    assertThat(updatedUser.getDisplayName()).isEqualTo("Updated Name");
    assertThat(updatedUser.getDisplayCountry()).isEqualTo("PT");
  }

  @Test
  void shouldDeleteUser() {
    // Given
    User user = User.builder()
        .email("delete@example.com")
        .build();
    User savedUser = userRepository.save(user);
    UUID userId = savedUser.getId();

    // When
    userRepository.deleteById(userId);
    Optional<User> foundUser = userRepository.findById(userId);

    // Then
    assertThat(foundUser).isEmpty();
  }

  @Test
  void shouldGenerateUniqueIds() {
    // Given
    User user1 = User.builder().email("user1@example.com").build();
    User user2 = User.builder().email("user2@example.com").build();

    // When
    User savedUser1 = userRepository.save(user1);
    User savedUser2 = userRepository.save(user2);

    // Then
    assertThat(savedUser1.getId()).isNotNull();
    assertThat(savedUser2.getId()).isNotNull();
    assertThat(savedUser1.getId()).isNotEqualTo(savedUser2.getId());
  }
}
