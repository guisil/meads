package app.meads.identity.internal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.stereotype.Service;

@Service
public class MagicLinkService {

    private static final Logger log = LoggerFactory.getLogger(MagicLinkService.class);

    private final String baseUrl;
    private final OneTimeTokenService tokenService;
    private final UserRepository userRepository;

    MagicLinkService(@Value("${app.base-url}") String baseUrl, OneTimeTokenService tokenService, UserRepository userRepository) {
        this.baseUrl = baseUrl;
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    public void requestMagicLink(String username) {
        var user = userRepository.findByEmail(username);
        if (user.isEmpty()) {
            log.debug("Magic link requested for unknown user: {}", username);
            return;
        }

        var request = new GenerateOneTimeTokenRequest(username);
        var oneTimeToken = tokenService.generate(request);
        String tokenValue = oneTimeToken.getTokenValue();
        String link = baseUrl + "/login/magic?token=" + tokenValue;
        log.info("\n\n\tMagic link for {}: {}\n", username, link);
    }
}
