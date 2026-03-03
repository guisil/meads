package app.meads.competition;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ParticipantRoleTest {

    @Test
    void shouldCreateWithParticipantId() {
        var participantId = UUID.randomUUID();

        var role = new ParticipantRole(participantId, CompetitionRole.JUDGE);

        assertThat(role.getId()).isNotNull();
        assertThat(role.getParticipantId()).isEqualTo(participantId);
        assertThat(role.getRole()).isEqualTo(CompetitionRole.JUDGE);
    }

    @Test
    void shouldReportRequiresAccessCodeCorrectly() {
        assertThat(CompetitionRole.JUDGE.requiresAccessCode()).isTrue();
        assertThat(CompetitionRole.STEWARD.requiresAccessCode()).isTrue();
        assertThat(CompetitionRole.ENTRANT.requiresAccessCode()).isFalse();
        assertThat(CompetitionRole.ADMIN.requiresAccessCode()).isFalse();
    }
}
