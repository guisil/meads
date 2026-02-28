package app.meads.identity;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtMagicLinkServiceTest {

    private static final String JWT_SECRET = "dev-secret-key-minimum-32-characters-long-for-hs256";
    private static final String BASE_URL = "http://localhost:8080";

    JwtMagicLinkService jwtMagicLinkService = new JwtMagicLinkService(JWT_SECRET, BASE_URL);

    @Test
    void shouldGenerateLinkWithValidTokenForEmail() {
        // Act
        String link = jwtMagicLinkService.generateLink("user@example.com", Duration.ofDays(7));

        // Assert
        assertThat(link).startsWith(BASE_URL + "/login/magic?token=");
        String token = link.substring(link.indexOf("token=") + "token=".length());
        assertThat(token).isNotBlank();
    }
}
