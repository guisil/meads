package app.meads.identity.internal;

import app.meads.TestcontainersConfiguration;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.ott.InMemoryOneTimeTokenService;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MagicLinkServiceTest {

    @Autowired
    MagicLinkService magicLinkService;

    @Autowired(required = false)
    OneTimeTokenService tokenService;

    @Autowired
    UserRepository userRepository;

    @Test
    void shouldUseSpringSecurityTokenServiceWhenGeneratingMagicLink() {
        // Given - Spring Security OTT service should be configured
        assertThat(tokenService)
                .as("OneTimeTokenService should be available as a bean")
                .isNotNull()
                .isInstanceOf(InMemoryOneTimeTokenService.class);

        // And - a user exists
        var user = new User(UUID.randomUUID(), "user@example.com", "Test User", UserStatus.ACTIVE, Role.USER);
        userRepository.save(user);

        // When - request a magic link
        String tokenValue = magicLinkService.requestMagicLink("user@example.com");

        // Then - should return a non-empty token value
        assertThat(tokenValue).isNotNull().isNotEmpty();

        // And - token should be consumable by Spring Security (verifies it's a real stored token)
        var authToken = new org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken(tokenValue);
        var consumedToken = tokenService.consume(authToken);

        assertThat(consumedToken).isNotNull();
        assertThat(consumedToken.getUsername()).isEqualTo("user@example.com");
    }

    @Test
    void shouldThrowExceptionWhenUserDoesNotExist() {
        // When/Then - requesting magic link for non-existent user should throw exception
        assertThatThrownBy(() -> magicLinkService.requestMagicLink("nonexistent@example.com"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("nonexistent@example.com");
    }

    @Test
    void shouldGenerateMagicLinkWhenUserExists() {
        // Given - a user exists in the database
        var user = new User(UUID.randomUUID(), "existing@example.com", "Existing User", UserStatus.ACTIVE, Role.USER);
        userRepository.save(user);

        // When - request magic link for existing user
        String tokenValue = magicLinkService.requestMagicLink("existing@example.com");

        // Then - should successfully generate token
        assertThat(tokenValue).isNotNull().isNotEmpty();
    }
}
