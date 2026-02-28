package app.meads.identity;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtMagicLinkService {

    private final SecretKey signingKey;
    private final String baseUrl;

    JwtMagicLinkService(@Value("${app.auth.jwt-secret}") String jwtSecret,
                        @Value("${app.base-url}") String baseUrl) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.baseUrl = baseUrl;
    }

    public String generateLink(String email, Duration validity) {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject(email)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(signingKey)
                .compact();
        return baseUrl + "/login/magic?token=" + token;
    }
}
