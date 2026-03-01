package app.meads.competition;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventParticipantTest {

    private EventParticipant createParticipant() {
        return new EventParticipant(UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void shouldCreateWithActiveStatus() {
        var eventId = UUID.randomUUID();
        var userId = UUID.randomUUID();

        var participant = new EventParticipant(eventId, userId);

        assertThat(participant.getId()).isNotNull();
        assertThat(participant.getEventId()).isEqualTo(eventId);
        assertThat(participant.getUserId()).isEqualTo(userId);
        assertThat(participant.getStatus()).isEqualTo(CompetitionParticipantStatus.ACTIVE);
        assertThat(participant.getAccessCode()).isNull();
    }

    @Test
    void shouldAssignAccessCode() {
        var participant = createParticipant();

        participant.assignAccessCode("AB3K9XYZ");

        assertThat(participant.getAccessCode()).isEqualTo("AB3K9XYZ");
    }

    @Test
    void shouldThrowWhenAccessCodeNotEightChars() {
        var participant = createParticipant();

        assertThatThrownBy(() -> participant.assignAccessCode("SHORT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8");
    }

    @Test
    void shouldNormalizeAccessCodeToUppercase() {
        var participant = createParticipant();

        participant.assignAccessCode("ab3k9xyz");

        assertThat(participant.getAccessCode()).isEqualTo("AB3K9XYZ");
    }

    @Test
    void shouldWithdrawEventParticipant() {
        var participant = createParticipant();

        participant.withdraw();

        assertThat(participant.getStatus()).isEqualTo(CompetitionParticipantStatus.WITHDRAWN);
    }

    @Test
    void shouldThrowWhenWithdrawingAlreadyWithdrawn() {
        var participant = createParticipant();
        participant.withdraw();

        assertThatThrownBy(() -> participant.withdraw())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already withdrawn");
    }
}
