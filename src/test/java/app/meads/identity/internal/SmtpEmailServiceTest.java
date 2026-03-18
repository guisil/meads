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
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.mail.MailSendException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
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
    MessageSource messageSource;

    @Mock
    MimeMessage mimeMessage;

    SmtpEmailService emailService;

    @BeforeEach
    void setup() {
        given(mailSender.createMimeMessage()).willReturn(mimeMessage);
        given(templateEngine.process(eq("email/email-base"), any(IContext.class)))
                .willReturn("<html>rendered</html>");
        // Return the default message (3rd arg) from MessageSource — this is the key itself
        // Lenient because admin-only methods don't call MessageSource
        org.mockito.Mockito.lenient().when(messageSource.getMessage(any(String.class), any(), any(String.class), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(2));
        emailService = new SmtpEmailService(mailSender, jwtMagicLinkService,
                templateEngine, messageSource, "MEADS <noreply@meads.app>", 5, 50);
    }

    @Test
    void shouldSendMagicLinkEmail() {
        given(jwtMagicLinkService.generateLink(eq("user@example.com"), any()))
                .willReturn("http://localhost:8080/login/magic?token=abc123");

        emailService.sendMagicLink("user@example.com", Locale.ENGLISH);

        verify(mailSender).send(any(MimeMessage.class));
        var contextCaptor = ArgumentCaptor.forClass(IContext.class);
        verify(templateEngine).process(eq("email/email-base"), contextCaptor.capture());
        var ctx = contextCaptor.getValue();
        assertThat(ctx.getVariable("heading")).isEqualTo("email.magic-link.heading");
        assertThat(ctx.getVariable("ctaLabel")).isEqualTo("email.magic-link.cta");
        assertThat(ctx.getVariable("ctaUrl")).isEqualTo("http://localhost:8080/login/magic?token=abc123");
        assertThat(ctx.getVariable("contactEmail")).isNull();
    }

    @Test
    void shouldSendPasswordResetEmail() {
        given(jwtMagicLinkService.generatePasswordSetupLink(eq("user@example.com"), any()))
                .willReturn("http://localhost:8080/set-password?token=xyz789");

        emailService.sendPasswordReset("user@example.com", Locale.ENGLISH);

        verify(mailSender).send(any(MimeMessage.class));
        var contextCaptor = ArgumentCaptor.forClass(IContext.class);
        verify(templateEngine).process(eq("email/email-base"), contextCaptor.capture());
        var ctx = contextCaptor.getValue();
        assertThat(ctx.getVariable("heading")).isEqualTo("email.password-reset.heading");
        assertThat(ctx.getVariable("ctaLabel")).isEqualTo("email.password-reset.cta");
        assertThat(ctx.getVariable("ctaUrl")).isEqualTo("http://localhost:8080/set-password?token=xyz789");
        assertThat(ctx.getVariable("contactEmail")).isNull();
    }

    @Test
    void shouldSendPasswordSetupEmailWithCompetitionContext() {
        given(jwtMagicLinkService.generatePasswordSetupLink(eq("admin@example.com"), any()))
                .willReturn("http://localhost:8080/set-password?token=setup456");

        emailService.sendPasswordSetup("admin@example.com", "CHIP 2026", "organizer@chip.com", Locale.ENGLISH);

        verify(mailSender).send(any(MimeMessage.class));
        var contextCaptor = ArgumentCaptor.forClass(IContext.class);
        verify(templateEngine).process(eq("email/email-base"), contextCaptor.capture());
        var ctx = contextCaptor.getValue();
        assertThat(ctx.getVariable("heading")).isEqualTo("email.password-setup.heading");
        assertThat((String) ctx.getVariable("bodyText")).isEqualTo("email.password-setup.body");
        assertThat(ctx.getVariable("contactEmail")).isEqualTo("organizer@chip.com");
    }

    @Test
    void shouldSendPasswordSetupEmailWithoutContactEmail() {
        given(jwtMagicLinkService.generatePasswordSetupLink(eq("admin@example.com"), any()))
                .willReturn("http://localhost:8080/set-password?token=setup456");

        emailService.sendPasswordSetup("admin@example.com", "CHIP 2026", null, Locale.ENGLISH);

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

        assertThatCode(() -> emailService.sendMagicLink("user@example.com", Locale.ENGLISH))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldUseSevenDayTokenValidityForMagicLink() {
        given(jwtMagicLinkService.generateLink(eq("user@example.com"), any()))
                .willReturn("http://localhost:8080/login/magic?token=abc");

        emailService.sendMagicLink("user@example.com", Locale.ENGLISH);

        var durationCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(jwtMagicLinkService).generateLink(eq("user@example.com"), durationCaptor.capture());
        assertThat(durationCaptor.getValue()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void shouldSendSubmissionConfirmationWithEntryLines() {
        var entryLines = java.util.List.of(
                "#1 — My Mead — M1A Traditional Mead (Dry)",
                "#2 — Berry Mead — M2C Berry Melomel");

        emailService.sendSubmissionConfirmation(
                "entrant@test.com", "CHIP 2026", "Amadora",
                entryLines,
                "/competitions/chip-2026/divisions/amadora/my-entries", Locale.ENGLISH);

        verify(mailSender).send(any(MimeMessage.class));
        var contextCaptor = ArgumentCaptor.forClass(IContext.class);
        verify(templateEngine).process(eq("email/email-base"), contextCaptor.capture());
        var ctx = contextCaptor.getValue();
        assertThat(ctx.getVariable("heading")).isEqualTo("email.submission.heading");
        assertThat(ctx.getVariable("bodyText")).isEqualTo("email.submission.body");
        @SuppressWarnings("unchecked")
        var lines = (java.util.List<String>) ctx.getVariable("entryLines");
        assertThat(lines).containsExactly(
                "#1 — My Mead — M1A Traditional Mead (Dry)",
                "#2 — Berry Mead — M2C Berry Melomel");
        assertThat(ctx.getVariable("ctaLabel")).isEqualTo("email.submission.cta");
    }

    @Test
    void shouldNotSendMagicLinkWhenRateLimited() {
        given(jwtMagicLinkService.generateLink(eq("user@example.com"), any()))
                .willReturn("http://localhost:8080/login/magic?token=abc123");

        emailService.sendMagicLink("user@example.com", Locale.ENGLISH);
        emailService.sendMagicLink("user@example.com", Locale.ENGLISH);

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void shouldNotSendPasswordResetWhenRateLimited() {
        given(jwtMagicLinkService.generatePasswordSetupLink(eq("user@example.com"), any()))
                .willReturn("http://localhost:8080/set-password?token=xyz789");

        emailService.sendPasswordReset("user@example.com", Locale.ENGLISH);
        emailService.sendPasswordReset("user@example.com", Locale.ENGLISH);

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void shouldSendCredentialsReminderEmail() {
        emailService.sendCredentialsReminder("user@example.com", Locale.ENGLISH);

        verify(mailSender).send(any(MimeMessage.class));
        var contextCaptor = ArgumentCaptor.forClass(IContext.class);
        verify(templateEngine).process(eq("email/email-base"), contextCaptor.capture());
        var ctx = contextCaptor.getValue();
        assertThat(ctx.getVariable("heading")).isEqualTo("email.credentials-reminder.heading");
        assertThat((String) ctx.getVariable("bodyText")).isEqualTo("email.credentials-reminder.body");
        assertThat(ctx.getVariable("ctaLabel")).isNull();
        assertThat(ctx.getVariable("ctaUrl")).isNull();
    }

    @Test
    void shouldRateLimitCredentialsReminderEmail() {
        emailService.sendCredentialsReminder("user@example.com", Locale.ENGLISH);
        emailService.sendCredentialsReminder("user@example.com", Locale.ENGLISH);

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void shouldNotRateLimitSystemTriggeredEmails() {
        emailService.sendSubmissionConfirmation("user@example.com", "CHIP 2026", "Amadora",
                java.util.List.of("#1 — My Mead"), "/my-entries", Locale.ENGLISH);
        emailService.sendSubmissionConfirmation("user@example.com", "CHIP 2026", "Amadora",
                java.util.List.of("#1 — My Mead"), "/my-entries", Locale.ENGLISH);

        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void shouldUseSevenDayTokenValidityForPasswordReset() {
        given(jwtMagicLinkService.generatePasswordSetupLink(eq("user@example.com"), any()))
                .willReturn("http://localhost:8080/set-password?token=abc");

        emailService.sendPasswordReset("user@example.com", Locale.ENGLISH);

        var durationCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(jwtMagicLinkService).generatePasswordSetupLink(eq("user@example.com"), durationCaptor.capture());
        assertThat(durationCaptor.getValue()).isEqualTo(Duration.ofDays(7));
    }
}
