package app.meads.user.internal;

import app.meads.user.api.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Development email service that logs magic links to console instead of sending emails.
 * Active only in the "dev" profile for local testing without email infrastructure.
 */
@Slf4j
@Service
@Profile("dev")
public class ConsoleEmailService implements EmailService {

  @Override
  public void sendMagicLinkEmail(String email, String magicLink) {
    log.info("=".repeat(80));
    log.info("MAGIC LINK EMAIL (DEV MODE - NOT SENT)");
    log.info("To: {}", email);
    log.info("Magic Link: {}", magicLink);
    log.info("=".repeat(80));
  }

  @Override
  public void sendBulkMagicLinkEmails(List<EmailRecipient> recipients) {
    log.info("=".repeat(80));
    log.info("BULK MAGIC LINK EMAILS (DEV MODE - NOT SENT)");
    log.info("Sending {} magic link emails:", recipients.size());
    recipients.forEach(recipient -> {
      log.info("  - To: {} | Magic Link: {}", recipient.email(), recipient.magicLink());
    });
    log.info("=".repeat(80));
  }
}
