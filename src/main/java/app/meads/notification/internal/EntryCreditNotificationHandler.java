package app.meads.notification.internal;

import app.meads.entrant.api.EntryCreditAddedEvent;
import app.meads.shared.api.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class EntryCreditNotificationHandler {

    private final EmailService emailService;

    @ApplicationModuleListener
    void onEntryCreditAdded(EntryCreditAddedEvent event) {
        log.info("Handling EntryCreditAddedEvent for entrant: {}", event.entrantEmail());

        var subject = "Entry Credits Added - MEADS Competition";
        var body = String.format("""
            Hello %s,

            You have received %d entry credit(s) for the MEADS competition.

            Credit ID: %s
            Competition ID: %s

            You can use these credits to submit your mead entries.

            Thank you for participating!

            Best regards,
            The MEADS Team
            """,
            event.entrantName(),
            event.quantity(),
            event.creditId(),
            event.competitionId()
        );

        emailService.sendEmail(event.entrantEmail(), subject, body);
    }
}
