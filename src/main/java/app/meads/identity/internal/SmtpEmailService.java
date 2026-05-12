package app.meads.identity.internal;

import app.meads.PluralRules;
import app.meads.identity.EmailService;
import app.meads.identity.JwtMagicLinkService;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import org.springframework.core.io.ClassPathResource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
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
    private final MessageSource messageSource;
    private final String fromAddress;
    private final int rateLimitMinutes;
    private final int dailyWarningThreshold;

    private final ConcurrentHashMap<String, Instant> rateLimitMap = new ConcurrentHashMap<>();
    private final AtomicInteger dailyCount = new AtomicInteger(0);
    private final AtomicReference<LocalDate> dailyCountDate = new AtomicReference<>(LocalDate.now());

    SmtpEmailService(JavaMailSender mailSender,
                     JwtMagicLinkService jwtMagicLinkService,
                     ITemplateEngine templateEngine,
                     MessageSource messageSource,
                     @Value("${app.email.from}") String fromAddress,
                     @Value("${app.email.rate-limit-minutes:5}") int rateLimitMinutes,
                     @Value("${app.email.daily-warning-threshold:50}") int dailyWarningThreshold) {
        this.mailSender = mailSender;
        this.jwtMagicLinkService = jwtMagicLinkService;
        this.templateEngine = templateEngine;
        this.messageSource = messageSource;
        this.fromAddress = fromAddress;
        this.rateLimitMinutes = rateLimitMinutes;
        this.dailyWarningThreshold = dailyWarningThreshold;
    }

    @Override
    public void sendMagicLink(String recipientEmail, Locale locale) {
        if (isRateLimited(recipientEmail, "magic-link")) {
            return;
        }
        var link = jwtMagicLinkService.generateLink(recipientEmail, TOKEN_VALIDITY);
        var subject = msg("email.magic-link.subject", locale);
        var ctx = new Context();
        ctx.setVariable("subject", subject);
        ctx.setVariable("heading", msg("email.magic-link.heading", locale));
        ctx.setVariable("bodyText", msg("email.magic-link.body", locale));
        ctx.setVariable("ctaLabel", msg("email.magic-link.cta", locale));
        ctx.setVariable("ctaUrl", link);
        ctx.setVariable("fallbackText", msg("email.fallback", locale));
        ctx.setVariable("footerText", msg("email.footer", locale));
        ctx.setVariable("contactEmail", null);
        sendEmail(recipientEmail, subject, ctx, link);
    }

    @Override
    public void sendCredentialsReminder(String recipientEmail, Locale locale) {
        if (isRateLimited(recipientEmail, "credentials-reminder")) {
            return;
        }
        var subject = msg("email.credentials-reminder.subject", locale);
        var ctx = new Context();
        ctx.setVariable("subject", subject);
        ctx.setVariable("heading", msg("email.credentials-reminder.heading", locale));
        ctx.setVariable("bodyText", msg("email.credentials-reminder.body", locale));
        ctx.setVariable("ctaLabel", null);
        ctx.setVariable("ctaUrl", null);
        ctx.setVariable("fallbackText", msg("email.fallback", locale));
        ctx.setVariable("footerText", msg("email.footer", locale));
        ctx.setVariable("contactEmail", null);
        sendEmail(recipientEmail, subject, ctx, "");
    }

    @Override
    public void sendPasswordReset(String recipientEmail, Locale locale) {
        if (isRateLimited(recipientEmail, "password-reset")) {
            return;
        }
        var link = jwtMagicLinkService.generatePasswordSetupLink(recipientEmail, TOKEN_VALIDITY);
        var subject = msg("email.password-reset.subject", locale);
        var ctx = new Context();
        ctx.setVariable("subject", subject);
        ctx.setVariable("heading", msg("email.password-reset.heading", locale));
        ctx.setVariable("bodyText", msg("email.password-reset.body", locale));
        ctx.setVariable("ctaLabel", msg("email.password-reset.cta", locale));
        ctx.setVariable("ctaUrl", link);
        ctx.setVariable("fallbackText", msg("email.fallback", locale));
        ctx.setVariable("footerText", msg("email.footer", locale));
        ctx.setVariable("contactEmail", null);
        sendEmail(recipientEmail, subject, ctx, link);
    }

    @Override
    public void sendPasswordSetup(String recipientEmail, String competitionName, String contactEmail, Locale locale) {
        var link = jwtMagicLinkService.generatePasswordSetupLink(recipientEmail, TOKEN_VALIDITY);
        var subject = msg("email.password-setup.subject", locale);
        var ctx = new Context();
        ctx.setVariable("subject", subject);
        ctx.setVariable("heading", msg("email.password-setup.heading", locale));
        ctx.setVariable("bodyText", msg("email.password-setup.body", locale, competitionName));
        ctx.setVariable("ctaLabel", msg("email.password-setup.cta", locale));
        ctx.setVariable("ctaUrl", link);
        ctx.setVariable("fallbackText", msg("email.fallback", locale));
        ctx.setVariable("footerText", msg("email.footer", locale));
        ctx.setVariable("contactEmail", contactEmail);
        ctx.setVariable("competitionName", competitionName);
        sendEmail(recipientEmail, subject, ctx, link);
    }

    @Override
    public void sendOrderReviewAlert(String recipientEmail, String competitionName,
                                      String jumpsellerOrderId, String customerName,
                                      String divisionNames) {
        var ctx = new Context();
        var subject = "[MEADS] Order requires review — " + competitionName;
        ctx.setVariable("subject", subject);
        ctx.setVariable("heading", "Order Requires Review");
        ctx.setVariable("bodyText",
                "Order #" + jumpsellerOrderId + " from " + customerName
                        + " could not be fully processed and requires manual review.");
        ctx.setVariable("orderReviewCompetition", competitionName);
        ctx.setVariable("orderReviewDivisions", divisionNames);
        ctx.setVariable("ctaLabel", null);
        ctx.setVariable("ctaUrl", null);
        ctx.setVariable("contactEmail", null);
        sendEmail(recipientEmail, subject, ctx, "");
    }

    @Override
    public void sendSubmissionConfirmation(String recipientEmail, String competitionName,
                                            String divisionName, java.util.List<String> entryLines,
                                            String entriesUrl, Locale locale) {
        var subject = msg("email.submission.subject", locale, divisionName);
        var ctx = new Context();
        ctx.setVariable("subject", subject);
        ctx.setVariable("heading", msg("email.submission.heading", locale));
        ctx.setVariable("bodyText", msg("email.submission.body", locale, divisionName, competitionName));
        ctx.setVariable("entryLines", entryLines);
        ctx.setVariable("ctaLabel", msg("email.submission.cta", locale));
        ctx.setVariable("ctaUrl", entriesUrl);
        ctx.setVariable("fallbackText", msg("email.fallback", locale));
        ctx.setVariable("footerText", msg("email.footer", locale));
        ctx.setVariable("contactEmail", null);
        sendEmail(recipientEmail, subject, ctx, entriesUrl);
    }

    @Override
    public void sendCreditNotification(String recipientEmail,
                                        int credits, String divisionName,
                                        String competitionName, String myEntriesUrl,
                                        String contactEmail, Locale locale) {
        var creditWord = msgPlural("email.credit.unit", credits, locale);
        var subject = msg("email.credit.subject", locale, divisionName);
        var ctx = new Context();
        ctx.setVariable("subject", subject);
        ctx.setVariable("heading", msg("email.credit.heading", locale));
        ctx.setVariable("bodyText", msg("email.credit.body", locale, credits, creditWord, divisionName, competitionName));
        ctx.setVariable("bodyText2", msg("email.credit.body2", locale));
        ctx.setVariable("ctaLabel", msg("email.credit.cta", locale));
        ctx.setVariable("ctaUrl", myEntriesUrl);
        ctx.setVariable("fallbackText", msg("email.fallback", locale));
        ctx.setVariable("footerText", msg("email.footer", locale));
        ctx.setVariable("contactText", msg("email.contact", locale));
        ctx.setVariable("contactEmail", contactEmail);
        sendEmail(recipientEmail, subject, ctx, myEntriesUrl);
    }

    @Override
    public void sendResultsAnnouncement(String recipientEmail, Locale locale,
                                         EmailService.ResultsAnnouncementType type,
                                         String competitionName, String divisionName,
                                         String customOrJustificationBody,
                                         String resultsUrl, String contactEmail) {
        var subjectKey = switch (type) {
            case INITIAL_NO_CUSTOM -> "email.results-published.subject";
            case REPUBLISH_NO_CUSTOM -> "email.results-republished.subject";
            case CUSTOM_MESSAGE -> "email.custom-announcement.subject";
        };
        var headingKey = switch (type) {
            case INITIAL_NO_CUSTOM -> "email.results-published.heading";
            case REPUBLISH_NO_CUSTOM -> "email.results-republished.heading";
            case CUSTOM_MESSAGE -> "email.custom-announcement.heading";
        };
        var bodyKey = switch (type) {
            case INITIAL_NO_CUSTOM -> "email.results-published.body";
            case REPUBLISH_NO_CUSTOM -> "email.results-republished.intro";
            case CUSTOM_MESSAGE -> null;
        };
        var subject = msg(subjectKey, locale, competitionName, divisionName);
        var heading = msg(headingKey, locale, competitionName, divisionName);
        var bodyText = type == EmailService.ResultsAnnouncementType.CUSTOM_MESSAGE
                ? customOrJustificationBody
                : msg(bodyKey, locale, competitionName, divisionName);
        var bodyText2 = type == EmailService.ResultsAnnouncementType.REPUBLISH_NO_CUSTOM
                ? customOrJustificationBody
                : null;

        var link = jwtMagicLinkService.generateLink(recipientEmail, TOKEN_VALIDITY);
        var ctx = new Context();
        ctx.setVariable("subject", subject);
        ctx.setVariable("heading", heading);
        ctx.setVariable("bodyText", bodyText);
        ctx.setVariable("bodyText2", bodyText2);
        ctx.setVariable("ctaLabel", msg("email.results.cta-label", locale));
        ctx.setVariable("ctaUrl", link + resultsUrl);
        ctx.setVariable("fallbackText", msg("email.fallback", locale));
        ctx.setVariable("footerText", msg("email.footer", locale));
        ctx.setVariable("contactText", msg("email.contact", locale));
        ctx.setVariable("contactEmail", contactEmail);
        sendEmail(recipientEmail, subject, ctx, link + resultsUrl);
    }

    private String msg(String key, Locale locale, Object... args) {
        return messageSource.getMessage(key, args, key, locale);
    }

    private String msgPlural(String keyPrefix, int count, Locale locale) {
        var category = PluralRules.getCategory(count, locale);
        var specificKey = keyPrefix + "." + category;
        var result = messageSource.getMessage(specificKey, null, null, locale);
        if (result != null) {
            return result;
        }
        return messageSource.getMessage(keyPrefix + ".other", null, keyPrefix, locale);
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
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            helper.addInline("meads-logo",
                    new ClassPathResource("META-INF/resources/images/meads-logo-white.png"),
                    "image/png");
            mailSender.send(message);
            trackDailyCount();
            log.info("Email sent: subject='{}', to={}", subject, to);
        } catch (MailException | MessagingException e) {
            log.warn("Failed to send email to {} (subject='{}'): {}. Link: {}",
                    to, subject, e.getMessage(), fallbackLink);
        }
    }
}
