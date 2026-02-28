package app.meads.identity;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void shouldValidateAndExtractEmailFromToken() {
        // Arrange
        String link = jwtMagicLinkService.generateLink("user@example.com", Duration.ofDays(7));
        String token = link.substring(link.indexOf("token=") + "token=".length());

        // Act
        String email = jwtMagicLinkService.validateToken(token);

        // Assert
        assertThat(email).isEqualTo("user@example.com");
    }

    @Test
    void shouldRejectExpiredToken() {
        // Arrange — negative duration creates an already-expired token
        String link = jwtMagicLinkService.generateLink("user@example.com", Duration.ofSeconds(-1));
        String token = link.substring(link.indexOf("token=") + "token=".length());

        // Act & Assert
        assertThatThrownBy(() -> jwtMagicLinkService.validateToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shouldRejectTamperedToken() {
        // Arrange
        String link = jwtMagicLinkService.generateLink("user@example.com", Duration.ofDays(7));
        String token = link.substring(link.indexOf("token=") + "token=".length());
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        // Act & Assert
        assertThatThrownBy(() -> jwtMagicLinkService.validateToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shouldRejectTokenSignedWithDifferentKey() {
        // Arrange — sign with a completely different key
        var differentKey = Keys.hmacShaKeyFor(
                "a-completely-different-secret-key-for-testing-purposes".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("user@example.com")
                .signWith(differentKey)
                .compact();

        // Act & Assert
        assertThatThrownBy(() -> jwtMagicLinkService.validateToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shouldAllowReusingValidToken() {
        // Arrange
        String link = jwtMagicLinkService.generateLink("user@example.com", Duration.ofDays(7));
        String token = link.substring(link.indexOf("token=") + "token=".length());

        // Act — validate the same token twice
        String firstResult = jwtMagicLinkService.validateToken(token);
        String secondResult = jwtMagicLinkService.validateToken(token);

        // Assert — stateless = reusable
        assertThat(firstResult).isEqualTo("user@example.com");
        assertThat(secondResult).isEqualTo("user@example.com");
    }
}
