package app.meads.competition;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompetitionTest {

    private Competition createDraftCompetition() {
        return new Competition(UUID.randomUUID(), UUID.randomUUID(),
                "Home Competition", ScoringSystem.MJP);
    }

    @Test
    void shouldStartInDraftStatus() {
        var competition = createDraftCompetition();

        assertThat(competition.getStatus()).isEqualTo(CompetitionStatus.DRAFT);
    }

    @Test
    void shouldAdvanceThroughAllStatusesSequentially() {
        var competition = createDraftCompetition();

        competition.advanceStatus();
        assertThat(competition.getStatus()).isEqualTo(CompetitionStatus.REGISTRATION_OPEN);

        competition.advanceStatus();
        assertThat(competition.getStatus()).isEqualTo(CompetitionStatus.REGISTRATION_CLOSED);

        competition.advanceStatus();
        assertThat(competition.getStatus()).isEqualTo(CompetitionStatus.JUDGING);

        competition.advanceStatus();
        assertThat(competition.getStatus()).isEqualTo(CompetitionStatus.DELIBERATION);

        competition.advanceStatus();
        assertThat(competition.getStatus()).isEqualTo(CompetitionStatus.RESULTS_PUBLISHED);
    }

    @Test
    void shouldUpdateDetailsWhenInDraft() {
        var competition = createDraftCompetition();

        competition.updateDetails("Updated Name", ScoringSystem.MJP);

        assertThat(competition.getName()).isEqualTo("Updated Name");
    }

    @Test
    void shouldThrowWhenUpdatingDetailsAfterDraft() {
        var competition = createDraftCompetition();
        competition.advanceStatus(); // REGISTRATION_OPEN

        assertThatThrownBy(() -> competition.updateDetails("New Name", ScoringSystem.MJP))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    void shouldThrowWhenAdvancingPastTerminalStatus() {
        var competition = createDraftCompetition();
        competition.advanceStatus(); // REGISTRATION_OPEN
        competition.advanceStatus(); // REGISTRATION_CLOSED
        competition.advanceStatus(); // JUDGING
        competition.advanceStatus(); // DELIBERATION
        competition.advanceStatus(); // RESULTS_PUBLISHED

        assertThatThrownBy(competition::advanceStatus)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESULTS_PUBLISHED");
    }
}
