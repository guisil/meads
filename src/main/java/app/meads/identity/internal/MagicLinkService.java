package app.meads.internal;

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

    String requestMagicLink(String username) {
        // Check if user exists
        userRepository.findByEmail(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        var request = new GenerateOneTimeTokenRequest(username);
        var oneTimeToken = tokenService.generate(request);
        String tokenValue = oneTimeToken.getTokenValue();
        String link = BASE_URL + "/login/ott?token=" + tokenValue;
        log.info("\n\n\tMagic link for {}: {}\n", username, link);
        return tokenValue;
    }
}
