package app.meads.competition;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CompetitionParticipantTest {

    @Test
    void shouldCreateWithEventParticipantId() {
        var competitionId = UUID.randomUUID();
        var eventParticipantId = UUID.randomUUID();

        var participant = new CompetitionParticipant(
                competitionId, eventParticipantId, CompetitionRole.JUDGE);

        assertThat(participant.getId()).isNotNull();
        assertThat(participant.getCompetitionId()).isEqualTo(competitionId);
        assertThat(participant.getEventParticipantId()).isEqualTo(eventParticipantId);
        assertThat(participant.getRole()).isEqualTo(CompetitionRole.JUDGE);
    }

    @Test
    void shouldReportRequiresAccessCodeCorrectly() {
        assertThat(CompetitionRole.JUDGE.requiresAccessCode()).isTrue();
        assertThat(CompetitionRole.STEWARD.requiresAccessCode()).isTrue();
        assertThat(CompetitionRole.ENTRANT.requiresAccessCode()).isFalse();
        assertThat(CompetitionRole.COMPETITION_ADMIN.requiresAccessCode()).isFalse();
    }
}
