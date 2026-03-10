package app.meads.identity.internal;

import app.meads.identity.EmailService;
import app.meads.identity.JwtMagicLinkService;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Duration;

@Slf4j
@Service
class SmtpEmailService implements EmailService {

    private static final Duration TOKEN_VALIDITY = Duration.ofDays(7);
    private static final String TEMPLATE_NAME = "email/email-base";

    private final JavaMailSender mailSender;
    private final JwtMagicLinkService jwtMagicLinkService;
    private final ITemplateEngine templateEngine;
    private final String fromAddress;

    SmtpEmailService(JavaMailSender mailSender,
                     JwtMagicLinkService jwtMagicLinkService,
                     ITemplateEngine templateEngine,
                     @Value("${app.email.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.jwtMagicLinkService = jwtMagicLinkService;
        this.templateEngine = templateEngine;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendMagicLink(String recipientEmail) {
        var link = jwtMagicLinkService.generateLink(recipientEmail, TOKEN_VALIDITY);
        var ctx = new Context();
        ctx.setVariable("subject", "Your MEADS login link");
        ctx.setVariable("heading", "Log in to MEADS");
        ctx.setVariable("bodyText", "Click the button below to log in.");
        ctx.setVariable("ctaLabel", "Log In");
        ctx.setVariable("ctaUrl", link);
        ctx.setVariable("contactEmail", null);
        sendEmail(recipientEmail, "Your MEADS login link", ctx, link);
    }

    @Override
    public void sendPasswordReset(String recipientEmail) {
        var link = jwtMagicLinkService.generatePasswordSetupLink(recipientEmail, TOKEN_VALIDITY);
        var ctx = new Context();
        ctx.setVariable("subject", "Reset your MEADS password");
        ctx.setVariable("heading", "Set your password");
        ctx.setVariable("bodyText", "Click the button below to set a new password.");
        ctx.setVariable("ctaLabel", "Set Password");
        ctx.setVariable("ctaUrl", link);
        ctx.setVariable("contactEmail", null);
        sendEmail(recipientEmail, "Reset your MEADS password", ctx, link);
    }

    @Override
    public void sendPasswordSetup(String recipientEmail, String competitionName, String contactEmail) {
        var link = jwtMagicLinkService.generatePasswordSetupLink(recipientEmail, TOKEN_VALIDITY);
        var ctx = new Context();
        ctx.setVariable("subject", "Set up your MEADS admin password");
        ctx.setVariable("heading", "Set your admin password");
        ctx.setVariable("bodyText",
                "You've been added as an admin for " + competitionName + ". Click below to set your password.");
        ctx.setVariable("ctaLabel", "Set Password");
        ctx.setVariable("ctaUrl", link);
        ctx.setVariable("contactEmail", contactEmail);
        sendEmail(recipientEmail, "Set up your MEADS admin password", ctx, link);
    }

    private void sendEmail(String to, String subject, Context thymeleafContext, String fallbackLink) {
        try {
            var htmlBody = templateEngine.process(TEMPLATE_NAME, thymeleafContext);
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent: subject='{}', to={}", subject, to);
        } catch (MailException | MessagingException e) {
            log.warn("Failed to send email to {} (subject='{}'): {}. Link: {}",
                    to, subject, e.getMessage(), fallbackLink);
        }
    }
}
