package app.meads.internal;

import app.meads.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.ott.InMemoryOneTimeTokenService;
import org.springframework.security.authentication.ott.OneTimeTokenService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MagicLinkServiceTest {

    @Autowired
    MagicLinkService magicLinkService;

    @Autowired(required = false)
    OneTimeTokenService tokenService;

    @Test
    void shouldUseSpringSecurityTokenServiceWhenGeneratingMagicLink() {
        // Given - Spring Security OTT service should be configured
        assertThat(tokenService)
                .as("OneTimeTokenService should be available as a bean")
                .isNotNull()
                .isInstanceOf(InMemoryOneTimeTokenService.class);

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
}
