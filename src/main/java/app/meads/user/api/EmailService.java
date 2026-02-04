package app.meads.user.api;

import java.util.List;

/**
 * Service for sending emails, particularly magic link authentication emails.
 */
public interface EmailService {

  /**
   * Sends a magic link email to a single user.
   *
   * @param email the recipient's email address
   * @param magicLink the magic link URL for authentication
   */
  void sendMagicLinkEmail(String email, String magicLink);

  /**
   * Sends magic link emails to multiple users.
   *
   * @param recipients list of recipient email addresses and their corresponding magic links
   */
  void sendBulkMagicLinkEmails(List<EmailRecipient> recipients);

  /**
   * Record containing recipient email and magic link.
   */
  record EmailRecipient(String email, String magicLink) {}
}
