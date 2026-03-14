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

import static org.mockito.ArgumentMatchers.eq;

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
    JwtMagicLinkService jwtMagicLinkService;

    @Mock
    PasswordEncoder passwordEncoder;

    @Test
    void shouldDeactivateUserWhenStatusIsNotInactive() {
        // Arrange
        User user = new User("user@example.com", "Test User", UserStatus.ACTIVE, Role.USER);
        UUID userId = user.getId();
        String currentUserEmail = "admin@example.com";

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        // Act
        userService.removeUser(userId, currentUserEmail);

        // Assert
        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
        then(userRepository).should().save(user);
        then(userRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    void shouldHardDeleteUserWhenStatusIsInactive() {
        // Arrange
        User user = new User("user@example.com", "Test User", UserStatus.INACTIVE, Role.USER);
        UUID userId = user.getId();
        String currentUserEmail = "admin@example.com";

        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // Act
        userService.removeUser(userId, currentUserEmail);

        // Assert
        then(userRepository).should().delete(user);
        then(userRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    void shouldRejectRemovingOwnAccount() {
        // Arrange
        User user = new User("user@example.com", "Test User", UserStatus.ACTIVE, Role.USER);
        UUID userId = user.getId();
        String currentUserEmail = "user@example.com";

        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // Act & Assert
        assertThatThrownBy(() -> userService.removeUser(userId, currentUserEmail))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot deactivate or delete your own account");
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
        User user = new User("user@example.com", "Old Name", UserStatus.ACTIVE, Role.USER);
        UUID userId = user.getId();
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
        User user = new User("admin@example.com", "Admin", UserStatus.ACTIVE, Role.USER);
        UUID userId = user.getId();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateUser(userId, "Admin", Role.SYSTEM_ADMIN, UserStatus.ACTIVE, "admin@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot change your own role");
    }

    @Test
    void shouldRejectSelfStatusChange() {
        User user = new User("admin@example.com", "Admin", UserStatus.ACTIVE, Role.USER);
        UUID userId = user.getId();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateUser(userId, "Admin", Role.USER, UserStatus.INACTIVE, "admin@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot change your own status");
    }

    // --- findAll / findById tests ---

    @Test
    void shouldFindAllUsers() {
        var users = List.of(
                new User("a@example.com", "A", UserStatus.ACTIVE, Role.USER),
                new User("b@example.com", "B", UserStatus.PENDING, Role.SYSTEM_ADMIN)
        );
        given(userRepository.findAll(any(org.springframework.data.domain.Sort.class))).willReturn(users);

        List<User> result = userService.findAll();

        assertThat(result).hasSize(2);
        then(userRepository).should().findAll(any(org.springframework.data.domain.Sort.class));
    }

    @Test
    void shouldFindUserById() {
        User user = new User("user@example.com", "User", UserStatus.ACTIVE, Role.USER);
        UUID userId = user.getId();
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
        User user = new User("admin@example.com", "Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
        UUID userId = user.getId();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        boolean result = userService.isEditingSelf(userId, "admin@example.com");

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenNotEditingSelf() {
        User user = new User("user@example.com", "User", UserStatus.ACTIVE, Role.USER);
        UUID userId = user.getId();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        boolean result = userService.isEditingSelf(userId, "other@example.com");

        assertThat(result).isFalse();
    }

    // --- findAllByIds ---

    @Test
    void shouldFindAllUsersByIds() {
        var user1 = new User("a@example.com", "A", UserStatus.ACTIVE, Role.USER);
        var user2 = new User("b@example.com", "B", UserStatus.ACTIVE, Role.USER);
        var ids = List.of(user1.getId(), user2.getId());
        given(userRepository.findAllById(ids)).willReturn(List.of(user1, user2));

        var result = userService.findAllByIds(ids);

        assertThat(result).hasSize(2);
        then(userRepository).should().findAllById(ids);
    }

    // --- findOrCreateByEmail tests ---

    @Test
    void shouldReturnExistingUserWhenFindOrCreate() {
        var existing = new User("existing@example.com", "Existing User", UserStatus.ACTIVE, Role.USER);
        given(userRepository.findByEmail("existing@example.com")).willReturn(Optional.of(existing));

        var result = userService.findOrCreateByEmail("existing@example.com");

        assertThat(result).isSameAs(existing);
        then(userRepository).should(never()).save(any());
    }

    @Test
    void shouldCreatePendingUserWhenFindOrCreate() {
        given(userRepository.findByEmail("new@example.com")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        var result = userService.findOrCreateByEmail("new@example.com");

        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getName()).isEqualTo("new@example.com");
        assertThat(result.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(result.getRole()).isEqualTo(Role.USER);
        then(userRepository).should().save(any(User.class));
    }

    // --- setPassword tests ---

    @Test
    void shouldSetPasswordHashOnUser() {
        // Arrange
        User user = new User("admin@example.com", "Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
        UUID userId = user.getId();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(passwordEncoder.encode("rawPassword")).willReturn("$2a$10$encodedHash");
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        // Act
        userService.setPassword(userId, "rawPassword");

        // Assert
        assertThat(user.getPasswordHash()).isEqualTo("$2a$10$encodedHash");
        then(userRepository).should().save(user);
    }

    // --- updateProfile tests ---

    @Test
    void shouldUpdateProfile() {
        var user = new User("test@example.com", "Old Name", UserStatus.ACTIVE, Role.USER);
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
        given(userRepository.save(any(User.class))).willAnswer(i -> i.getArgument(0));

        var result = userService.updateProfile(user.getId(), "New Name", "My Meadery", "PT");

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getMeaderyName()).isEqualTo("My Meadery");
        assertThat(result.getCountry()).isEqualTo("PT");
    }

    @Test
    void shouldRejectInvalidCountryCode() {
        var userId = UUID.randomUUID();

        assertThatThrownBy(() -> userService.updateProfile(userId, "Name", null, "XX"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("country code");
    }

    @Test
    void shouldAllowNullCountryInProfile() {
        var user = new User("test@example.com", "Name", UserStatus.ACTIVE, Role.USER);
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
        given(userRepository.save(any(User.class))).willAnswer(i -> i.getArgument(0));

        var result = userService.updateProfile(user.getId(), "Name", null, null);

        assertThat(result.getCountry()).isNull();
        assertThat(result.getMeaderyName()).isNull();
    }

    // --- updateMeaderyName tests ---

    @Test
    void shouldUpdateMeaderyName() {
        var user = new User("user@example.com", "User", UserStatus.ACTIVE, Role.USER);
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        var result = userService.updateMeaderyName(user.getId(), "Golden Meadery");

        assertThat(result.getMeaderyName()).isEqualTo("Golden Meadery");
        then(userRepository).should().save(user);
    }

    @Test
    void shouldRejectPasswordShorterThanEightCharacters() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> userService.setPassword(userId, "short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 8 characters");

        then(passwordEncoder).shouldHaveNoInteractions();
        then(userRepository).shouldHaveNoInteractions();
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

    // --- setPasswordByToken tests ---

    @Test
    void shouldSetPasswordByToken() {
        User user = new User("admin@example.com", "Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
        given(jwtMagicLinkService.extractEmail("valid-token")).willReturn("admin@example.com");
        given(userRepository.findByEmail("admin@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.encode("newPassword123")).willReturn("$2a$10$encoded");
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        userService.setPasswordByToken("valid-token", "newPassword123");

        assertThat(user.getPasswordHash()).isEqualTo("$2a$10$encoded");
        then(userRepository).should().save(user);
    }

    @Test
    void shouldRejectShortPasswordByToken() {
        given(jwtMagicLinkService.extractEmail("valid-token")).willReturn("admin@example.com");

        assertThatThrownBy(() -> userService.setPasswordByToken("valid-token", "short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 8 characters");

        then(userRepository).should(never()).save(any());
    }

    // --- hasPassword tests ---

    @Test
    void shouldReturnTrueWhenUserHasPassword() {
        User user = new User("admin@example.com", "Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
        user.assignPasswordHash("$2a$10$someHash");
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));

        assertThat(userService.hasPassword(user.getId())).isTrue();
    }

    @Test
    void shouldReturnFalseWhenUserHasNoPassword() {
        User user = new User("user@example.com", "User", UserStatus.ACTIVE, Role.USER);
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));

        assertThat(userService.hasPassword(user.getId())).isFalse();
    }
}
