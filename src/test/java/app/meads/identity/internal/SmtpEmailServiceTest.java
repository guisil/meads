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

import org.springframework.mail.MailSendException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
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

    @Test
    void shouldSendPasswordResetEmail() {
        given(jwtMagicLinkService.generatePasswordSetupLink(eq("user@example.com"), any()))
                .willReturn("http://localhost:8080/set-password?token=xyz789");

        emailService.sendPasswordReset("user@example.com");

        verify(mailSender).send(any(MimeMessage.class));
        var contextCaptor = ArgumentCaptor.forClass(IContext.class);
        verify(templateEngine).process(eq("email/email-base"), contextCaptor.capture());
        var ctx = contextCaptor.getValue();
        assertThat(ctx.getVariable("heading")).isEqualTo("Set your password");
        assertThat(ctx.getVariable("ctaLabel")).isEqualTo("Set Password");
        assertThat(ctx.getVariable("ctaUrl")).isEqualTo("http://localhost:8080/set-password?token=xyz789");
        assertThat(ctx.getVariable("contactEmail")).isNull();
    }

    @Test
    void shouldSendPasswordSetupEmailWithCompetitionContext() {
        given(jwtMagicLinkService.generatePasswordSetupLink(eq("admin@example.com"), any()))
                .willReturn("http://localhost:8080/set-password?token=setup456");

        emailService.sendPasswordSetup("admin@example.com", "CHIP 2026", "organizer@chip.com");

        verify(mailSender).send(any(MimeMessage.class));
        var contextCaptor = ArgumentCaptor.forClass(IContext.class);
        verify(templateEngine).process(eq("email/email-base"), contextCaptor.capture());
        var ctx = contextCaptor.getValue();
        assertThat(ctx.getVariable("heading")).isEqualTo("Set your admin password");
        assertThat((String) ctx.getVariable("bodyText")).contains("CHIP 2026");
        assertThat(ctx.getVariable("contactEmail")).isEqualTo("organizer@chip.com");
    }

    @Test
    void shouldSendPasswordSetupEmailWithoutContactEmail() {
        given(jwtMagicLinkService.generatePasswordSetupLink(eq("admin@example.com"), any()))
                .willReturn("http://localhost:8080/set-password?token=setup456");

        emailService.sendPasswordSetup("admin@example.com", "CHIP 2026", null);

        verify(mailSender).send(any(MimeMessage.class));
        var contextCaptor = ArgumentCaptor.forClass(IContext.class);
        verify(templateEngine).process(eq("email/email-base"), contextCaptor.capture());
        var ctx = contextCaptor.getValue();
        assertThat(ctx.getVariable("contactEmail")).isNull();
    }

    @Test
    void shouldNotThrowWhenSmtpFails() {
        given(jwtMagicLinkService.generateLink(eq("user@example.com"), any()))
                .willReturn("http://localhost:8080/login/magic?token=abc123");
        doThrow(new MailSendException("SMTP connection refused"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> emailService.sendMagicLink("user@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldUseSevenDayTokenValidityForMagicLink() {
        given(jwtMagicLinkService.generateLink(eq("user@example.com"), any()))
                .willReturn("http://localhost:8080/login/magic?token=abc");

        emailService.sendMagicLink("user@example.com");

        var durationCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(jwtMagicLinkService).generateLink(eq("user@example.com"), durationCaptor.capture());
        assertThat(durationCaptor.getValue()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void shouldUseSevenDayTokenValidityForPasswordReset() {
        given(jwtMagicLinkService.generatePasswordSetupLink(eq("user@example.com"), any()))
                .willReturn("http://localhost:8080/set-password?token=abc");

        emailService.sendPasswordReset("user@example.com");

        var durationCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(jwtMagicLinkService).generatePasswordSetupLink(eq("user@example.com"), durationCaptor.capture());
        assertThat(durationCaptor.getValue()).isEqualTo(Duration.ofDays(7));
    }
}
