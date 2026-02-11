package app.meads.identity.internal;

import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class UserActivationListenerTest {

    @InjectMocks UserActivationListener listener;
    @Mock UserRepository userRepository;

    @Test
    void shouldActivatePendingUserWhenAuthenticationSucceeds() {
        // Arrange
        var user = new User(UUID.randomUUID(), "pending@example.com", "Pending User", UserStatus.PENDING, Role.USER);
        given(userRepository.findByEmail("pending@example.com")).willReturn(Optional.of(user));

        var authentication = mock(Authentication.class);
        given(authentication.getName()).willReturn("pending@example.com");
        var event = new AuthenticationSuccessEvent(authentication);

        // Act
        listener.onAuthenticationSuccess(event);

        // Assert
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        then(userRepository).should().save(user);
    }

    @Test
    void shouldNotSaveWhenUserIsAlreadyActive() {
        // Arrange
        var user = new User(UUID.randomUUID(), "active@example.com", "Active User", UserStatus.ACTIVE, Role.USER);
        given(userRepository.findByEmail("active@example.com")).willReturn(Optional.of(user));

        var authentication = mock(Authentication.class);
        given(authentication.getName()).willReturn("active@example.com");
        var event = new AuthenticationSuccessEvent(authentication);

        // Act
        listener.onAuthenticationSuccess(event);

        // Assert
        then(userRepository).should(never()).save(any());
    }

    @Test
    void shouldDoNothingWhenUserNotFoundInDatabase() {
        // Arrange
        given(userRepository.findByEmail("unknown@example.com")).willReturn(Optional.empty());

        var authentication = mock(Authentication.class);
        given(authentication.getName()).willReturn("unknown@example.com");
        var event = new AuthenticationSuccessEvent(authentication);

        // Act
        listener.onAuthenticationSuccess(event);

        // Assert
        then(userRepository).should(never()).save(any());
    }
}
