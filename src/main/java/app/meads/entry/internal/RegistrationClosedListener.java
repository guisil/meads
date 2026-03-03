package app.meads.entry.internal;

import app.meads.competition.DivisionStatus;
import app.meads.competition.DivisionStatusAdvancedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class RegistrationClosedListener {

    private static final Logger log = LoggerFactory.getLogger(RegistrationClosedListener.class);

    @ApplicationModuleListener
    public void on(DivisionStatusAdvancedEvent event) {
        if (event.newStatus() == DivisionStatus.REGISTRATION_CLOSED) {
            log.info("Registration closed for division {}. TODO: discard unsubmitted drafts.",
                    event.divisionId());
        }
    }
}
