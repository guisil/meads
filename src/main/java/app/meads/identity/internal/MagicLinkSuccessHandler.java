package app.meads.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.web.authentication.ott.OneTimeTokenGenerationSuccessHandler;
import org.springframework.security.web.authentication.ott.RedirectOneTimeTokenGenerationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import java.io.IOException;

@Component
class MagicLinkSuccessHandler implements OneTimeTokenGenerationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(MagicLinkSuccessHandler.class);

    private final OneTimeTokenGenerationSuccessHandler redirectHandler =
            new RedirectOneTimeTokenGenerationSuccessHandler("/login?tokenSent");

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, OneTimeToken oneTimeToken)
            throws IOException, ServletException {
        String link = "http://localhost:8080/login/ott?token=" + oneTimeToken.getTokenValue();
        log.info("\n\n\tMagic link: {}\n", link);
        redirectHandler.handle(request, response, oneTimeToken);
    }
}
