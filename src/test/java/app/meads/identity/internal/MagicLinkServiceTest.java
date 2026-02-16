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
import static org.assertj.core.api.Assertions.assertThatCode;

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
    }

    @Test
    void shouldNotThrowExceptionWhenUserDoesNotExist() {
        // Requesting a magic link for a non-existent user should not reveal
        // whether the user exists (OWASP user enumeration prevention)
        assertThatCode(() -> magicLinkService.requestMagicLink("nonexistent@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldNotThrowExceptionWhenUserExists() {
        // Given - a user exists in the database
        var user = new User(UUID.randomUUID(), "existing@example.com", "Existing User", UserStatus.ACTIVE, Role.USER);
        userRepository.save(user);

        // When/Then - request magic link for existing user should succeed
        assertThatCode(() -> magicLinkService.requestMagicLink("existing@example.com"))
                .doesNotThrowAnyException();
    }
}
