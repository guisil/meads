package app.meads.identity.internal;

import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class DatabaseUserDetailsServiceTest {

    @InjectMocks DatabaseUserDetailsService databaseUserDetailsService;
    @Mock UserRepository userRepository;

    @Test
    void shouldReturnUserDetailsWhenUserExists() {
        // Arrange
        var user = new User(
                UUID.randomUUID(),
                "user@example.com",
                "Test User",
                UserStatus.ACTIVE,
                Role.USER
        );
        given(userRepository.findByEmail("user@example.com")).willReturn(Optional.of(user));

        // Act
        var userDetails = databaseUserDetailsService.loadUserByUsername("user@example.com");

        // Assert
        assertThat(userDetails.getUsername()).isEqualTo("user@example.com");
        assertThat(userDetails.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
        assertThat(userDetails.isEnabled()).isTrue();
    }

    @Test
    void shouldThrowUsernameNotFoundExceptionWhenUserDoesNotExist() {
        // Arrange
        given(userRepository.findByEmail("unknown@example.com")).willReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> databaseUserDetailsService.loadUserByUsername("unknown@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("unknown@example.com");
    }
}
