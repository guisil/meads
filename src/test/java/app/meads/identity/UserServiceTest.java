package app.meads.identity;

import app.meads.identity.internal.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    UserService userService;

    @Mock
    UserRepository userRepository;

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
                .hasMessageContaining("Cannot delete your own account");
    }
}
