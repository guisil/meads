package app.meads.identity;

import app.meads.TestcontainersConfiguration;
import app.meads.identity.internal.UserRepository;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.QueryParameters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;

import static com.github.mvysny.kaributesting.v10.LocatorJ.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MfaResetViewTest {

    @Autowired ApplicationContext ctx;
    @Autowired UserRepository userRepository;
    @Autowired UserService userService;
    @Autowired JwtMagicLinkService jwtMagicLinkService;

    @BeforeEach
    void setup() {
        var routes = new Routes().autoDiscoverViews("app.meads");
        var servlet = new MockSpringServlet(routes, ctx, UI::new);
        MockVaadin.setup(UI::new, servlet);
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldDisableMfaAndShowSuccessParagraphWhenTokenValid() {
        var admin = userService.createUser("mfareset@example.com", "Admin",
                UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
        userService.setupMfa(admin.getId());
        var enabled = userRepository.findById(admin.getId()).orElseThrow();
        enabled.enableMfa(enabled.getTotpSecret());
        userRepository.save(enabled);
        assertThat(userRepository.findById(admin.getId()).orElseThrow().isMfaEnabled()).isTrue();

        String link = jwtMagicLinkService.generateMfaResetLink(admin.getEmail(), Duration.ofHours(1));
        String token = link.substring(link.indexOf("token=") + "token=".length());

        UI.getCurrent().navigate("mfa-reset", QueryParameters.of("token", token));

        var paragraphs = _find(Paragraph.class);
        assertThat(paragraphs).anyMatch(p -> p.getText().contains("Two-factor authentication disabled"));

        var refreshed = userRepository.findById(admin.getId()).orElseThrow();
        assertThat(refreshed.isMfaEnabled()).isFalse();
        assertThat(refreshed.getTotpSecret()).isNull();

        // Continue-to-login button is present
        var loginButton = _get(Button.class, spec -> spec.withText("Continue to Login"));
        assertThat(loginButton).isNotNull();
    }

    @Test
    void shouldShowErrorParagraphWhenTokenInvalid() {
        UI.getCurrent().navigate("mfa-reset", QueryParameters.of("token", "garbage.token.here"));

        var paragraphs = _find(Paragraph.class);
        assertThat(paragraphs).anyMatch(p -> p.getText().contains("invalid or has expired"));
    }

    @Test
    void shouldForwardToLoginWhenTokenMissing() {
        UI.getCurrent().navigate("mfa-reset");

        // Forwarded to /login — login page is rendered, not MfaResetView
        assertThat(_find(Button.class, spec -> spec.withText("Continue to Login"))).isEmpty();
        assertThat(_find(Button.class, spec -> spec.withText("Get Login Link"))).hasSize(1);
    }
}
