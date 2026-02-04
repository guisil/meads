package app.meads.user.internal;

import app.meads.user.api.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Production email service for sending actual emails via SMTP.
 * Active in all profiles except "dev".
 * TODO: Implement actual email sending logic with JavaMailSender
 */
@Slf4j
@Service
@Profile("!dev")
public class ProductionEmailService implements EmailService {

  @Override
  public void sendMagicLinkEmail(String email, String magicLink) {
    // TODO: Implement actual email sending
    log.warn("ProductionEmailService.sendMagicLinkEmail() not yet implemented");
    log.info("Would send magic link to: {}", email);
  }

  @Override
  public void sendBulkMagicLinkEmails(List<EmailRecipient> recipients) {
    // TODO: Implement actual bulk email sending
    log.warn("ProductionEmailService.sendBulkMagicLinkEmails() not yet implemented");
    log.info("Would send {} magic link emails", recipients.size());
  }
}
