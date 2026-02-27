package app.meads.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@Controller
class MagicLinkLandingController {

    private final String template;

    MagicLinkLandingController() {
        try (var is = getClass().getResourceAsStream("/templates/magic-link-landing.html")) {
            this.template = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @GetMapping("/login/magic")
    void landingPage(@RequestParam String token, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String escaped = HtmlUtils.htmlEscape(token);
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        String html = template
                .replace("{{csrfParameterName}}", csrfToken.getParameterName())
                .replace("{{csrfToken}}", csrfToken.getToken())
                .replace("{{token}}", escaped);
        response.setContentType("text/html");
        response.getWriter().write(html);
    }
}
