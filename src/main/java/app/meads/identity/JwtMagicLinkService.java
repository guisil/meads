package app.meads.identity;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
public class JwtMagicLinkService {

    private final SecretKey signingKey;
    private final String baseUrl;

    JwtMagicLinkService(@Value("${app.auth.jwt-secret}") String jwtSecret,
                        @Value("${app.base-url}") String baseUrl) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.baseUrl = baseUrl;
    }

    public String extractEmail(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public String generateLink(String email, Duration validity) {
        String token = buildToken(email, validity);
        log.debug("Generated magic link for: {} (validity={})", email, validity);
        return baseUrl + "/login/magic?token=" + token;
    }

    /**
     * Generates a magic link that, after successful login, redirects to {@code redirectPath}
     * (a same-origin absolute path, e.g. {@code /competitions/x/divisions/y/my-results}).
     * The path is URL-encoded and validated against open-redirect by
     * {@code MagicLinkAuthenticationFilter} on click. A blank path yields a plain magic link.
     */
    public String generateLink(String email, Duration validity, String redirectPath) {
        String link = generateLink(email, validity);
        if (redirectPath == null || redirectPath.isBlank()) {
            return link;
        }
        return link + "&redirect=" + URLEncoder.encode(redirectPath.strip(), StandardCharsets.UTF_8);
    }

    public String generatePasswordSetupLink(String email, Duration validity) {
        String token = buildToken(email, validity);
        log.debug("Generated password setup link for: {} (validity={})", email, validity);
        return baseUrl + "/set-password?token=" + token;
    }

    public String generateMfaResetLink(String email, Duration validity) {
        String token = buildToken(email, validity);
        log.debug("Generated MFA reset link for: {} (validity={})", email, validity);
        return baseUrl + "/mfa-reset?token=" + token;
    }

    private String buildToken(String email, Duration validity) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(signingKey)
                .compact();
    }
}
