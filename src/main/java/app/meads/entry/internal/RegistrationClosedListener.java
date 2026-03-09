package app.meads.entry.internal;

import app.meads.competition.DivisionStatus;
import app.meads.competition.DivisionStatusAdvancedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RegistrationClosedListener {


    @ApplicationModuleListener
    public void on(DivisionStatusAdvancedEvent event) {
        if (event.newStatus() == DivisionStatus.REGISTRATION_CLOSED) {
            log.info("Registration closed for division {}. TODO: discard unsubmitted drafts.",
                    event.divisionId());
        }
    }
}
