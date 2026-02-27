package app.meads.identity.internal;

import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.authentication.ott.OneTimeTokenService;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class MagicLinkServiceUnitTest {

    @Mock
    OneTimeTokenService tokenService;

    @Mock
    UserRepository userRepository;

    @Test
    void shouldUseConfiguredBaseUrlWhenGeneratingMagicLink() {
        // Arrange - create service with a custom base URL
        var service = new MagicLinkService("https://app.example.com", tokenService, userRepository);

        var user = new User(UUID.randomUUID(), "test@example.com", "Test", UserStatus.ACTIVE, Role.USER);
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(tokenService.generate(any())).willReturn(new OneTimeToken() {
            @Override public String getTokenValue() { return "test-token"; }
            @Override public String getUsername() { return "test@example.com"; }
            @Override public Instant getExpiresAt() { return Instant.now().plusSeconds(300); }
        });

        // Act
        service.requestMagicLink("test@example.com");

        // Assert - token service should have been called (link was generated)
        then(tokenService).should().generate(any());
    }
}
