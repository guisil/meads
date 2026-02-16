package app.meads.identity.internal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.stereotype.Service;

@Service
class MagicLinkService {

    private static final Logger log = LoggerFactory.getLogger(MagicLinkService.class);
    private static final String BASE_URL = "http://localhost:8080";

    private final OneTimeTokenService tokenService;
    private final UserRepository userRepository;

    MagicLinkService(OneTimeTokenService tokenService, UserRepository userRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    void requestMagicLink(String username) {
        var user = userRepository.findByEmail(username);
        if (user.isEmpty()) {
            log.debug("Magic link requested for unknown user: {}", username);
            return;
        }

        var request = new GenerateOneTimeTokenRequest(username);
        var oneTimeToken = tokenService.generate(request);
        String tokenValue = oneTimeToken.getTokenValue();
        String link = BASE_URL + "/login/magic?token=" + tokenValue;
        log.info("\n\n\tMagic link for {}: {}\n", username, link);
    }
}
