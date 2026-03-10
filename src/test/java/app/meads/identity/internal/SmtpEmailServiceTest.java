package app.meads.identity.internal;

import app.meads.identity.JwtMagicLinkService;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.IContext;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SmtpEmailServiceTest {

    @Mock
    JavaMailSender mailSender;

    @Mock
    JwtMagicLinkService jwtMagicLinkService;

    @Mock
    ITemplateEngine templateEngine;

    @Mock
    MimeMessage mimeMessage;

    SmtpEmailService emailService;

    @BeforeEach
    void setup() {
        given(mailSender.createMimeMessage()).willReturn(mimeMessage);
        given(templateEngine.process(eq("email/email-base"), any(IContext.class)))
                .willReturn("<html>rendered</html>");
        emailService = new SmtpEmailService(mailSender, jwtMagicLinkService,
                templateEngine, "MEADS <noreply@meads.app>");
    }

    @Test
    void shouldSendMagicLinkEmail() {
        given(jwtMagicLinkService.generateLink(eq("user@example.com"), any()))
                .willReturn("http://localhost:8080/login/magic?token=abc123");

        emailService.sendMagicLink("user@example.com");

        verify(mailSender).send(any(MimeMessage.class));
        var contextCaptor = ArgumentCaptor.forClass(IContext.class);
        verify(templateEngine).process(eq("email/email-base"), contextCaptor.capture());
        var ctx = contextCaptor.getValue();
        assertThat(ctx.getVariable("heading")).isEqualTo("Log in to MEADS");
        assertThat(ctx.getVariable("ctaLabel")).isEqualTo("Log In");
        assertThat(ctx.getVariable("ctaUrl")).isEqualTo("http://localhost:8080/login/magic?token=abc123");
        assertThat(ctx.getVariable("contactEmail")).isNull();
    }
}
