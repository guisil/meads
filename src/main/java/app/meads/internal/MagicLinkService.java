package app.meads.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
class MagicLinkService {

    private static final Logger log = LoggerFactory.getLogger(MagicLinkService.class);
    private static final String BASE_URL = "http://localhost:8080";

    void requestMagicLink(String username) {
        // Generate a temporary token for development
        String token = UUID.randomUUID().toString();
        String link = BASE_URL + "/login/ott?token=" + token;
        log.info("\n\n\tMagic link for {}: {}\n", username, link);
    }
}
