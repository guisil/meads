package app.meads.entry;

import app.meads.competition.DivisionStatus;
import app.meads.competition.DivisionStatusAdvancedEvent;
import app.meads.entry.internal.RegistrationClosedListener;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

class RegistrationClosedListenerTest {

    @Test
    void shouldHandleRegistrationClosedEventWithoutError() {
        var listener = new RegistrationClosedListener();

        var event = new DivisionStatusAdvancedEvent(
                UUID.randomUUID(),
                DivisionStatus.REGISTRATION_OPEN,
                DivisionStatus.REGISTRATION_CLOSED);

        assertThatCode(() -> listener.on(event)).doesNotThrowAnyException();
    }

    @Test
    void shouldIgnoreNonRegistrationClosedEvents() {
        var listener = new RegistrationClosedListener();

        var event = new DivisionStatusAdvancedEvent(
                UUID.randomUUID(),
                DivisionStatus.DRAFT,
                DivisionStatus.REGISTRATION_OPEN);

        assertThatCode(() -> listener.on(event)).doesNotThrowAnyException();
    }
}
