package app.meads.competition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CompetitionStatusTest {

    @Test
    void shouldReturnDisplayNameForEachStatus() {
        assertThat(CompetitionStatus.DRAFT.getDisplayName()).isEqualTo("Draft");
        assertThat(CompetitionStatus.REGISTRATION_OPEN.getDisplayName()).isEqualTo("Registration Open");
        assertThat(CompetitionStatus.REGISTRATION_CLOSED.getDisplayName()).isEqualTo("Registration Closed");
        assertThat(CompetitionStatus.JUDGING.getDisplayName()).isEqualTo("Judging");
        assertThat(CompetitionStatus.DELIBERATION.getDisplayName()).isEqualTo("Deliberation");
        assertThat(CompetitionStatus.RESULTS_PUBLISHED.getDisplayName()).isEqualTo("Results Published");
    }

    @Test
    void shouldReturnNextStatusForNonTerminalStatuses() {
        assertThat(CompetitionStatus.DRAFT.next()).isEqualTo(Optional.of(CompetitionStatus.REGISTRATION_OPEN));
        assertThat(CompetitionStatus.REGISTRATION_OPEN.next()).isEqualTo(Optional.of(CompetitionStatus.REGISTRATION_CLOSED));
        assertThat(CompetitionStatus.REGISTRATION_CLOSED.next()).isEqualTo(Optional.of(CompetitionStatus.JUDGING));
        assertThat(CompetitionStatus.JUDGING.next()).isEqualTo(Optional.of(CompetitionStatus.DELIBERATION));
        assertThat(CompetitionStatus.DELIBERATION.next()).isEqualTo(Optional.of(CompetitionStatus.RESULTS_PUBLISHED));
    }

    @Test
    void shouldReturnEmptyForTerminalStatus() {
        assertThat(CompetitionStatus.RESULTS_PUBLISHED.next()).isEmpty();
    }

    @Test
    void shouldReturnBadgeCssClassForEachStatus() {
        assertThat(CompetitionStatus.DRAFT.getBadgeCssClass()).isEqualTo("badge-draft");
        assertThat(CompetitionStatus.REGISTRATION_OPEN.getBadgeCssClass()).isEqualTo("badge-registration-open");
        assertThat(CompetitionStatus.REGISTRATION_CLOSED.getBadgeCssClass()).isEqualTo("badge-registration-closed");
        assertThat(CompetitionStatus.JUDGING.getBadgeCssClass()).isEqualTo("badge-judging");
        assertThat(CompetitionStatus.DELIBERATION.getBadgeCssClass()).isEqualTo("badge-deliberation");
        assertThat(CompetitionStatus.RESULTS_PUBLISHED.getBadgeCssClass()).isEqualTo("badge-results-published");
    }

    @ParameterizedTest
    @EnumSource(CompetitionStatus.class)
    void shouldHaveNonBlankDisplayNameForAllStatuses(CompetitionStatus status) {
        assertThat(status.getDisplayName()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(CompetitionStatus.class)
    void shouldHaveNonBlankBadgeCssClassForAllStatuses(CompetitionStatus status) {
        assertThat(status.getBadgeCssClass()).startsWith("badge-");
    }
}
