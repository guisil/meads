package app.meads.identity;

import app.meads.TestcontainersConfiguration;
import app.meads.identity.internal.UserRepository;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.server.VaadinServletRequest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static com.github.mvysny.kaributesting.v10.LocatorJ.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MfaVerifyViewTest {

    @Autowired ApplicationContext ctx;
    @Autowired UserRepository userRepository;
    @Autowired UserService userService;

    @MockitoBean
    JavaMailSender mailSender;

    MimeMessage mimeMessage;

    @BeforeEach
    void setup() {
        mimeMessage = mock(MimeMessage.class);
        given(mailSender.createMimeMessage()).willReturn(mimeMessage);

        var routes = new Routes().autoDiscoverViews("app.meads");
        var servlet = new MockSpringServlet(routes, ctx, UI::new);
        MockVaadin.setup(UI::new, servlet);

        // Simulate the MFA-pending session state created by MfaAuthenticationSuccessHandler
        var admin = userRepository.findByEmail("mfaverify-admin@example.com")
                .orElseGet(() -> userService.createUser("mfaverify-admin@example.com", "Admin",
                        UserStatus.ACTIVE, Role.SYSTEM_ADMIN));
        userService.setupMfa(admin.getId());
        var enabled = userRepository.findById(admin.getId()).orElseThrow();
        enabled.enableMfa(enabled.getTotpSecret());
        userRepository.save(enabled);

        var session = VaadinServletRequest.getCurrent().getHttpServletRequest().getSession();
        session.setAttribute("MFA_PENDING_EMAIL", admin.getEmail());
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldShowLostDeviceLinkOnMfaVerifyView() {
        UI.getCurrent().navigate("mfa");

        var lostDeviceButton = _get(Button.class, spec -> spec.withText("Lost your device?"));
        assertThat(lostDeviceButton).isNotNull();
    }

    @Test
    void shouldSendMfaResetEmailWhenLostDeviceClicked() {
        UI.getCurrent().navigate("mfa");

        _click(_get(Button.class, spec -> spec.withText("Lost your device?")));

        // Email was triggered via SmtpEmailService → JavaMailSender.send
        verify(mailSender, times(1)).send(any(MimeMessage.class));

        var notification = _get(Notification.class);
        assertThat(notification.getElement().getProperty("text")).contains("reset link has been emailed");
    }
}
