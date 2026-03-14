package app.meads.competition;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParticipantTest {

    private Participant createParticipant() {
        return new Participant(UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void shouldCreateParticipant() {
        var competitionId = UUID.randomUUID();
        var userId = UUID.randomUUID();

        var participant = new Participant(competitionId, userId);

        assertThat(participant.getId()).isNotNull();
        assertThat(participant.getCompetitionId()).isEqualTo(competitionId);
        assertThat(participant.getUserId()).isEqualTo(userId);
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
}
