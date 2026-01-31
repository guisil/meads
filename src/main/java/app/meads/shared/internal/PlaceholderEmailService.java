package app.meads.shared.internal;

import app.meads.shared.api.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
class PlaceholderEmailService implements EmailService {

    @Override
    public void sendEmail(String to, String subject, String body) {
        log.info("=== PLACEHOLDER EMAIL ===");
        log.info("To: {}", to);
        log.info("Subject: {}", subject);
        log.info("Body: {}", body);
        log.info("=========================");
    }

    @Override
    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        log.info("=== PLACEHOLDER HTML EMAIL ===");
        log.info("To: {}", to);
        log.info("Subject: {}", subject);
        log.info("HTML Body: {}", htmlBody);
        log.info("==============================");
    }
}
