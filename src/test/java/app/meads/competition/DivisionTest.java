package app.meads.competition;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DivisionTest {

    private Division createDraftDivision() {
        return new Division(UUID.randomUUID(),
                "Home Division", "home-division", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
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
    void shouldDefaultBosPlacesToOne() {
        var division = createDraftDivision();

        assertThat(division.getBosPlaces()).isEqualTo(1);
    }

    @Test
    void shouldDefaultMinJudgesPerTableToTwo() {
        var division = createDraftDivision();

        assertThat(division.getMinJudgesPerTable()).isEqualTo(2);
    }

    @Test
    void shouldUpdateBosPlacesInDraft() {
        var division = createDraftDivision();

        division.updateBosPlaces(3);

        assertThat(division.getBosPlaces()).isEqualTo(3);
    }

    @Test
    void shouldUpdateBosPlacesInRegistrationOpen() {
        var division = createDraftDivision();
        division.advanceStatus(); // REGISTRATION_OPEN

        division.updateBosPlaces(2);

        assertThat(division.getBosPlaces()).isEqualTo(2);
    }

    @Test
    void shouldRejectBosPlacesChangeAfterRegistrationOpen() {
        var division = createDraftDivision();
        division.advanceStatus();
        division.advanceStatus(); // REGISTRATION_CLOSED

        assertThatThrownBy(() -> division.updateBosPlaces(2))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectNonPositiveBosPlaces() {
        var division = createDraftDivision();

        assertThatThrownBy(() -> division.updateBosPlaces(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldUpdateMinJudgesPerTableThroughRegistrationClosed() {
        var division = createDraftDivision();
        division.advanceStatus();
        division.advanceStatus(); // REGISTRATION_CLOSED

        division.updateMinJudgesPerTable(3);

        assertThat(division.getMinJudgesPerTable()).isEqualTo(3);
    }

    @Test
    void shouldRejectMinJudgesPerTableChangeOnceJudging() {
        var division = createDraftDivision();
        division.advanceStatus();
        division.advanceStatus();
        division.advanceStatus(); // JUDGING

        assertThatThrownBy(() -> division.updateMinJudgesPerTable(3))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectNonPositiveMinJudgesPerTable() {
        var division = createDraftDivision();

        assertThatThrownBy(() -> division.updateMinJudgesPerTable(0))
                .isInstanceOf(IllegalArgumentException.class);
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
