package app.meads.identity;

import app.meads.identity.internal.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    UserService userService;

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Test
    void shouldSoftDeleteUserWhenStatusIsNotDisabled() {
        // Arrange
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "user@example.com", "Test User", UserStatus.ACTIVE, Role.USER);
        String currentUserEmail = "admin@example.com";

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        // Act
        userService.deleteUser(userId, currentUserEmail);

        // Assert
        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        then(userRepository).should().save(user);
        then(userRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    void shouldHardDeleteUserWhenStatusIsDisabled() {
        // Arrange
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "user@example.com", "Test User", UserStatus.DISABLED, Role.USER);
        String currentUserEmail = "admin@example.com";

        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // Act
        userService.deleteUser(userId, currentUserEmail);

        // Assert
        then(userRepository).should().delete(user);
        then(userRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    void shouldRejectDeletingOwnAccount() {
        // Arrange
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "user@example.com", "Test User", UserStatus.ACTIVE, Role.USER);
        String currentUserEmail = "user@example.com";

        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // Act & Assert
        assertThatThrownBy(() -> userService.deleteUser(userId, currentUserEmail))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot disable or delete your own account");
    }

    // --- createUser tests ---

    @Test
    void shouldCreateUserSuccessfully() {
        String email = "new@example.com";
        String name = "New User";
        given(userRepository.existsByEmail(email)).willReturn(false);
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        User result = userService.createUser(email, name, UserStatus.PENDING, Role.USER);

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.getName()).isEqualTo(name);
        assertThat(result.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(result.getRole()).isEqualTo(Role.USER);
        assertThat(result.getId()).isNotNull();
        then(userRepository).should().existsByEmail(email);
        then(userRepository).should().save(any(User.class));
    }

    @Test
    void shouldRejectCreateWhenEmailAlreadyExists() {
        String email = "existing@example.com";
        given(userRepository.existsByEmail(email)).willReturn(true);

        assertThatThrownBy(() -> userService.createUser(email, "Name", UserStatus.PENDING, Role.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already exists");

        then(userRepository).should(never()).save(any());
    }

    // --- updateUser tests ---

    @Test
    void shouldUpdateUserSuccessfully() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "user@example.com", "Old Name", UserStatus.ACTIVE, Role.USER);
        String currentUserEmail = "admin@example.com";
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        User result = userService.updateUser(userId, "New Name", Role.SYSTEM_ADMIN, UserStatus.ACTIVE, currentUserEmail);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getRole()).isEqualTo(Role.SYSTEM_ADMIN);
        then(userRepository).should().save(user);
    }

    @Test
    void shouldRejectSelfRoleChange() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "admin@example.com", "Admin", UserStatus.ACTIVE, Role.USER);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateUser(userId, "Admin", Role.SYSTEM_ADMIN, UserStatus.ACTIVE, "admin@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot change your own role");
    }

    @Test
    void shouldRejectSelfStatusChange() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "admin@example.com", "Admin", UserStatus.ACTIVE, Role.USER);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateUser(userId, "Admin", Role.USER, UserStatus.DISABLED, "admin@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot change your own status");
    }

    // --- findAll / findById tests ---

    @Test
    void shouldFindAllUsers() {
        var users = List.of(
                new User(UUID.randomUUID(), "a@example.com", "A", UserStatus.ACTIVE, Role.USER),
                new User(UUID.randomUUID(), "b@example.com", "B", UserStatus.PENDING, Role.SYSTEM_ADMIN)
        );
        given(userRepository.findAll()).willReturn(users);

        List<User> result = userService.findAll();

        assertThat(result).hasSize(2);
        then(userRepository).should().findAll();
    }

    @Test
    void shouldFindUserById() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "user@example.com", "User", UserStatus.ACTIVE, Role.USER);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        User result = userService.findById(userId);

        assertThat(result).isEqualTo(user);
    }

    @Test
    void shouldThrowWhenUserNotFoundById() {
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    // --- isEditingSelf tests ---

    @Test
    void shouldReturnTrueWhenEditingSelf() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "admin@example.com", "Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        boolean result = userService.isEditingSelf(userId, "admin@example.com");

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenNotEditingSelf() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "user@example.com", "User", UserStatus.ACTIVE, Role.USER);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        boolean result = userService.isEditingSelf(userId, "other@example.com");

        assertThat(result).isFalse();
    }

    // --- setPassword tests ---

    @Test
    void shouldSetPasswordHashOnUser() {
        // Arrange
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "admin@example.com", "Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(passwordEncoder.encode("rawPassword")).willReturn("$2a$10$encodedHash");
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        // Act
        userService.setPassword(userId, "rawPassword");

        // Assert
        assertThat(user.getPasswordHash()).isEqualTo("$2a$10$encodedHash");
        then(userRepository).should().save(user);
    }

    @Test
    void shouldThrowWhenSettingPasswordForNonExistentUser() {
        // Arrange
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.setPassword(userId, "rawPassword"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }
}
