package app.meads.identity.internal;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;

@Controller
class MagicLinkLandingController {

    @GetMapping("/login/magic")
    void landingPage(@RequestParam String token, HttpServletResponse response) throws IOException {
        String escaped = HtmlUtils.htmlEscape(token);
        response.setContentType("text/html");
        response.getWriter().write(
                "<html><body>"
                + "<form action=\"/login/ott\" method=\"post\">"
                + "<input type=\"hidden\" name=\"token\" value=\"" + escaped + "\"/>"
                + "</form>"
                + "<script>document.forms[0].submit()</script>"
                + "</body></html>"
        );
    }
}
