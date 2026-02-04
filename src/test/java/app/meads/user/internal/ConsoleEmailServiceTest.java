package app.meads.user.internal;

import app.meads.user.api.EmailService.EmailRecipient;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConsoleEmailService Tests")
class ConsoleEmailServiceTest {

  private ConsoleEmailService emailService;
  private Logger logger;
  private ListAppender<ILoggingEvent> listAppender;

  @BeforeEach
  void setUp() {
    emailService = new ConsoleEmailService();

    // Setup log capture
    logger = (Logger) LoggerFactory.getLogger(ConsoleEmailService.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(listAppender);
  }

  @Test
  @DisplayName("should log magic link email to console")
  void shouldLogMagicLinkEmailToConsole() {
    // Given
    var email = "test@example.com";
    var magicLink = "http://localhost:8080/auth/verify?token=abc123";

    // When
    emailService.sendMagicLinkEmail(email, magicLink);

    // Then
    var logsList = listAppender.list;
    assertThat(logsList).isNotEmpty();

    var logMessages = logsList.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .toList();

    assertThat(logMessages).anyMatch(msg -> msg.contains("MAGIC LINK EMAIL"));
    assertThat(logMessages).anyMatch(msg -> msg.contains("To: " + email));
    assertThat(logMessages).anyMatch(msg -> msg.contains("Magic Link: " + magicLink));
  }

  @Test
  @DisplayName("should log bulk magic link emails to console")
  void shouldLogBulkMagicLinkEmailsToConsole() {
    // Given
    var recipients = List.of(
        new EmailRecipient("user1@example.com", "http://localhost:8080/auth/verify?token=token1"),
        new EmailRecipient("user2@example.com", "http://localhost:8080/auth/verify?token=token2"),
        new EmailRecipient("user3@example.com", "http://localhost:8080/auth/verify?token=token3")
    );

    // When
    emailService.sendBulkMagicLinkEmails(recipients);

    // Then
    var logsList = listAppender.list;
    assertThat(logsList).hasSizeGreaterThanOrEqualTo(3);

    // Check that each recipient was logged
    var logMessages = logsList.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .toList();

    assertThat(logMessages).anyMatch(msg -> msg.contains("user1@example.com"));
    assertThat(logMessages).anyMatch(msg -> msg.contains("user2@example.com"));
    assertThat(logMessages).anyMatch(msg -> msg.contains("user3@example.com"));
    assertThat(logMessages).anyMatch(msg -> msg.contains("token1"));
    assertThat(logMessages).anyMatch(msg -> msg.contains("token2"));
    assertThat(logMessages).anyMatch(msg -> msg.contains("token3"));
  }

  @Test
  @DisplayName("should handle empty bulk email list")
  void shouldHandleEmptyBulkEmailList() {
    // Given
    var recipients = List.<EmailRecipient>of();

    // When
    emailService.sendBulkMagicLinkEmails(recipients);

    // Then
    var logsList = listAppender.list;
    // Should not throw exception, may or may not log anything
    assertThat(logsList).isNotNull();
  }

  @Test
  @DisplayName("should handle single recipient in bulk email")
  void shouldHandleSingleRecipientInBulkEmail() {
    // Given
    var recipients = List.of(
        new EmailRecipient("single@example.com", "http://localhost:8080/auth/verify?token=single")
    );

    // When
    emailService.sendBulkMagicLinkEmails(recipients);

    // Then
    var logsList = listAppender.list;
    assertThat(logsList).isNotEmpty();

    var logMessages = logsList.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .toList();

    assertThat(logMessages).anyMatch(msg -> msg.contains("single@example.com"));
    assertThat(logMessages).anyMatch(msg -> msg.contains("single"));
  }

  @Test
  @DisplayName("should handle special characters in email")
  void shouldHandleSpecialCharactersInEmail() {
    // Given
    var email = "test+tag@example.com";
    var magicLink = "http://localhost:8080/auth/verify?token=abc123";

    // When
    emailService.sendMagicLinkEmail(email, magicLink);

    // Then
    var logsList = listAppender.list;
    assertThat(logsList).isNotEmpty();

    var logMessages = logsList.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .toList();

    assertThat(logMessages).anyMatch(msg -> msg.contains("test+tag@example.com"));
  }

  @Test
  @DisplayName("should handle long magic links")
  void shouldHandleLongMagicLinks() {
    // Given
    var email = "test@example.com";
    var magicLink = "http://localhost:8080/auth/verify?token=" + "a".repeat(200);

    // When
    emailService.sendMagicLinkEmail(email, magicLink);

    // Then
    var logsList = listAppender.list;
    assertThat(logsList).isNotEmpty();

    var logMessages = logsList.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .toList();

    assertThat(logMessages).anyMatch(msg -> msg.contains(email));
    assertThat(logMessages).anyMatch(msg -> msg.contains(magicLink));
  }
}
