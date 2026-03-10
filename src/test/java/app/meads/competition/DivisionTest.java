package app.meads.competition;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DivisionTest {

    private Division createDraftDivision() {
        return new Division(UUID.randomUUID(),
                "Home Division", "home-division", ScoringSystem.MJP);
    }

    @Test
    void shouldStartInDraftStatus() {
        var division = createDraftDivision();

        assertThat(division.getStatus()).isEqualTo(DivisionStatus.DRAFT);
    }

    @Test
    void shouldAdvanceThroughAllStatusesSequentially() {
        var division = createDraftDivision();

        division.advanceStatus();
        assertThat(division.getStatus()).isEqualTo(DivisionStatus.REGISTRATION_OPEN);

        division.advanceStatus();
        assertThat(division.getStatus()).isEqualTo(DivisionStatus.REGISTRATION_CLOSED);

        division.advanceStatus();
        assertThat(division.getStatus()).isEqualTo(DivisionStatus.JUDGING);

        division.advanceStatus();
        assertThat(division.getStatus()).isEqualTo(DivisionStatus.DELIBERATION);

        division.advanceStatus();
        assertThat(division.getStatus()).isEqualTo(DivisionStatus.RESULTS_PUBLISHED);
    }

    @Test
    void shouldUpdateDetailsWhenInDraft() {
        var division = createDraftDivision();

        division.updateDetails("Updated Name", "updated-name", ScoringSystem.MJP, null);

        assertThat(division.getName()).isEqualTo("Updated Name");
    }

    @Test
    void shouldAllowUpdatingNameAndShortNameAfterDraft() {
        var division = createDraftDivision();
        division.advanceStatus(); // REGISTRATION_OPEN

        division.updateDetails("New Name", "new-name", ScoringSystem.MJP, null);

        assertThat(division.getName()).isEqualTo("New Name");
        assertThat(division.getShortName()).isEqualTo("new-name");
    }

    @Test
    void shouldRevertStatusOneStepBack() {
        var division = createDraftDivision();
        division.advanceStatus(); // REGISTRATION_OPEN
        division.advanceStatus(); // REGISTRATION_CLOSED

        division.revertStatus();

        assertThat(division.getStatus()).isEqualTo(DivisionStatus.REGISTRATION_OPEN);
    }

    @Test
    void shouldThrowWhenRevertingFromDraft() {
        var division = createDraftDivision();

        assertThatThrownBy(division::revertStatus)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    void shouldDefaultMeaderyNameRequiredToFalse() {
        var division = createDraftDivision();
        assertThat(division.isMeaderyNameRequired()).isFalse();
    }

    @Test
    void shouldUpdateMeaderyNameRequired() {
        var division = createDraftDivision();
        division.updateMeaderyNameRequired(true);
        assertThat(division.isMeaderyNameRequired()).isTrue();
    }

    @Test
    void shouldRejectMeaderyNameRequiredChangeOutsideDraft() {
        var division = createDraftDivision();
        division.advanceStatus(); // DRAFT → REGISTRATION_OPEN
        assertThatThrownBy(() -> division.updateMeaderyNameRequired(true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldThrowWhenAdvancingPastTerminalStatus() {
        var division = createDraftDivision();
        division.advanceStatus(); // REGISTRATION_OPEN
        division.advanceStatus(); // REGISTRATION_CLOSED
        division.advanceStatus(); // JUDGING
        division.advanceStatus(); // DELIBERATION
        division.advanceStatus(); // RESULTS_PUBLISHED

        assertThatThrownBy(division::advanceStatus)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESULTS_PUBLISHED");
    }
}
