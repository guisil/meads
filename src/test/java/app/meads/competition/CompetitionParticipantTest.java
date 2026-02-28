package app.meads.competition;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompetitionParticipantTest {

    private CompetitionParticipant createParticipant(CompetitionRole role) {
        return new CompetitionParticipant(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), role);
    }

    @Test
    void shouldAssignAccessCodeForJudge() {
        var participant = createParticipant(CompetitionRole.JUDGE);

        participant.assignAccessCode("AB3K9XYZ");

        assertThat(participant.getAccessCode()).isEqualTo("AB3K9XYZ");
    }

    @Test
    void shouldAssignAccessCodeForSteward() {
        var participant = createParticipant(CompetitionRole.STEWARD);

        participant.assignAccessCode("HJ7NW2QR");

        assertThat(participant.getAccessCode()).isEqualTo("HJ7NW2QR");
    }

    @Test
    void shouldNormalizeAccessCodeToUppercase() {
        var participant = createParticipant(CompetitionRole.JUDGE);

        participant.assignAccessCode("ab3k9xyz");

        assertThat(participant.getAccessCode()).isEqualTo("AB3K9XYZ");
    }

    @Test
    void shouldThrowWhenAssigningAccessCodeToEntrant() {
        var participant = createParticipant(CompetitionRole.ENTRANT);

        assertThatThrownBy(() -> participant.assignAccessCode("AB3K9XYZ"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("role");
    }

    @Test
    void shouldThrowWhenAssigningAccessCodeToCompetitionAdmin() {
        var participant = createParticipant(CompetitionRole.COMPETITION_ADMIN);

        assertThatThrownBy(() -> participant.assignAccessCode("AB3K9XYZ"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("role");
    }

    @Test
    void shouldThrowWhenAccessCodeNotExactlyEightChars() {
        var participant = createParticipant(CompetitionRole.JUDGE);

        assertThatThrownBy(() -> participant.assignAccessCode("SHORT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8");
    }

    @Test
    void shouldStartWithActiveStatus() {
        var participant = createParticipant(CompetitionRole.JUDGE);

        assertThat(participant.getStatus()).isEqualTo(CompetitionParticipantStatus.ACTIVE);
    }

    @Test
    void shouldWithdrawParticipant() {
        var participant = createParticipant(CompetitionRole.JUDGE);

        participant.withdraw();

        assertThat(participant.getStatus()).isEqualTo(CompetitionParticipantStatus.WITHDRAWN);
    }

    @Test
    void shouldThrowWhenWithdrawingAlreadyWithdrawnParticipant() {
        var participant = createParticipant(CompetitionRole.JUDGE);
        participant.withdraw();

        assertThatThrownBy(() -> participant.withdraw())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already withdrawn");
    }

    @Test
    void shouldReportRequiresAccessCodeCorrectly() {
        assertThat(CompetitionRole.JUDGE.requiresAccessCode()).isTrue();
        assertThat(CompetitionRole.STEWARD.requiresAccessCode()).isTrue();
        assertThat(CompetitionRole.ENTRANT.requiresAccessCode()).isFalse();
        assertThat(CompetitionRole.COMPETITION_ADMIN.requiresAccessCode()).isFalse();
    }
}
