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
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
class SmtpEmailService implements EmailService {

    private static final Duration TOKEN_VALIDITY = Duration.ofDays(7);
    private static final String TEMPLATE_NAME = "email/email-base";

    private final JavaMailSender mailSender;
    private final JwtMagicLinkService jwtMagicLinkService;
    private final ITemplateEngine templateEngine;
    private final String fromAddress;
    private final int rateLimitMinutes;
    private final int dailyWarningThreshold;

    private final ConcurrentHashMap<String, Instant> rateLimitMap = new ConcurrentHashMap<>();
    private final AtomicInteger dailyCount = new AtomicInteger(0);
    private final AtomicReference<LocalDate> dailyCountDate = new AtomicReference<>(LocalDate.now());

    SmtpEmailService(JavaMailSender mailSender,
                     JwtMagicLinkService jwtMagicLinkService,
                     ITemplateEngine templateEngine,
                     @Value("${app.email.from}") String fromAddress,
                     @Value("${app.email.rate-limit-minutes:5}") int rateLimitMinutes,
                     @Value("${app.email.daily-warning-threshold:50}") int dailyWarningThreshold) {
        this.mailSender = mailSender;
        this.jwtMagicLinkService = jwtMagicLinkService;
        this.templateEngine = templateEngine;
        this.fromAddress = fromAddress;
        this.rateLimitMinutes = rateLimitMinutes;
        this.dailyWarningThreshold = dailyWarningThreshold;
    }

    @Override
    public void sendMagicLink(String recipientEmail) {
        if (isRateLimited(recipientEmail, "magic-link")) {
            return;
        }
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
    public void sendCredentialsReminder(String recipientEmail) {
        if (isRateLimited(recipientEmail, "credentials-reminder")) {
            return;
        }
        var ctx = new Context();
        ctx.setVariable("subject", "MEADS login reminder");
        ctx.setVariable("heading", "Login Reminder");
        ctx.setVariable("bodyText",
                "You have a password set on your MEADS account. Please use your credentials to log in "
                        + "instead of requesting a login link.");
        ctx.setVariable("ctaLabel", null);
        ctx.setVariable("ctaUrl", null);
        ctx.setVariable("contactEmail", null);
        sendEmail(recipientEmail, "MEADS login reminder", ctx, "");
    }

    @Override
    public void sendPasswordReset(String recipientEmail) {
        if (isRateLimited(recipientEmail, "password-reset")) {
            return;
        }
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

    @Override
    public void sendOrderReviewAlert(String recipientEmail, String competitionName,
                                      String jumpsellerOrderId, String customerName) {
        var ctx = new Context();
        var subject = "[MEADS] Order requires review — " + competitionName;
        ctx.setVariable("subject", subject);
        ctx.setVariable("heading", "Order Requires Review");
        ctx.setVariable("bodyText",
                "Order #" + jumpsellerOrderId + " from " + customerName
                        + " could not be fully processed and requires manual review.");
        ctx.setVariable("ctaLabel", null);
        ctx.setVariable("ctaUrl", null);
        ctx.setVariable("contactEmail", null);
        sendEmail(recipientEmail, subject, ctx, "");
    }

    @Override
    public void sendSubmissionConfirmation(String recipientEmail, String competitionName,
                                            String divisionName, String entrySummary,
                                            String entriesUrl) {
        var ctx = new Context();
        var subject = "[MEADS] Entries submitted — " + divisionName;
        ctx.setVariable("subject", subject);
        ctx.setVariable("heading", "Entries Submitted");
        ctx.setVariable("bodyText",
                "All your entries for " + divisionName + " (" + competitionName
                        + ") have been submitted. Click the button below to view your entries and download your labels.");
        ctx.setVariable("detailHtml", entrySummary.replace("\n", "<br>"));
        ctx.setVariable("ctaLabel", "View My Entries");
        ctx.setVariable("ctaUrl", entriesUrl);
        ctx.setVariable("contactEmail", null);
        sendEmail(recipientEmail, subject, ctx, entriesUrl);
    }

    @Override
    public void sendCreditNotification(String recipientEmail,
                                        int credits, String divisionName,
                                        String competitionName, String myEntriesUrl,
                                        String contactEmail) {
        var ctx = new Context();
        var subject = "[MEADS] Entry credits received — " + divisionName;
        ctx.setVariable("subject", subject);
        ctx.setVariable("heading", "Entry Credits Received");
        ctx.setVariable("bodyText",
                "You've received " + credits + " entry "
                        + (credits == 1 ? "credit" : "credits")
                        + " for " + divisionName + " (" + competitionName
                        + "). Click the button below to view your entries and start registering your meads.");
        ctx.setVariable("ctaLabel", "View My Entries");
        ctx.setVariable("ctaUrl", myEntriesUrl);
        ctx.setVariable("contactEmail", contactEmail);
        sendEmail(recipientEmail, subject, ctx, myEntriesUrl);
    }

    private boolean isRateLimited(String email, String type) {
        var key = email + ":" + type;
        var now = Instant.now();
        var lastSent = rateLimitMap.get(key);
        if (lastSent != null && now.isBefore(lastSent.plus(Duration.ofMinutes(rateLimitMinutes)))) {
            log.info("Rate limited: email type '{}' for {} (cooldown {} min)", type, email, rateLimitMinutes);
            return true;
        }
        rateLimitMap.put(key, now);
        return false;
    }

    private void trackDailyCount() {
        var today = LocalDate.now();
        if (!today.equals(dailyCountDate.get())) {
            dailyCount.set(0);
            dailyCountDate.set(today);
        }
        var count = dailyCount.incrementAndGet();
        if (count == dailyWarningThreshold) {
            log.warn("Daily email count has reached {} — approaching quota limits", count);
        }
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
            trackDailyCount();
            log.info("Email sent: subject='{}', to={}", subject, to);
        } catch (MailException | MessagingException e) {
            log.warn("Failed to send email to {} (subject='{}'): {}. Link: {}",
                    to, subject, e.getMessage(), fallbackLink);
        }
    }
}
