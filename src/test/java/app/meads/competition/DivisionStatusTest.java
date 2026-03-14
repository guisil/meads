package app.meads.competition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DivisionStatusTest {

    @Test
    void shouldReturnDisplayNameForEachStatus() {
        assertThat(DivisionStatus.DRAFT.getDisplayName()).isEqualTo("Draft");
        assertThat(DivisionStatus.REGISTRATION_OPEN.getDisplayName()).isEqualTo("Registration Open");
        assertThat(DivisionStatus.REGISTRATION_CLOSED.getDisplayName()).isEqualTo("Registration Closed");
        assertThat(DivisionStatus.JUDGING.getDisplayName()).isEqualTo("Judging");
        assertThat(DivisionStatus.DELIBERATION.getDisplayName()).isEqualTo("Deliberation");
        assertThat(DivisionStatus.RESULTS_PUBLISHED.getDisplayName()).isEqualTo("Results Published");
    }

    @Test
    void shouldReturnNextStatusForNonTerminalStatuses() {
        assertThat(DivisionStatus.DRAFT.next()).isEqualTo(Optional.of(DivisionStatus.REGISTRATION_OPEN));
        assertThat(DivisionStatus.REGISTRATION_OPEN.next()).isEqualTo(Optional.of(DivisionStatus.REGISTRATION_CLOSED));
        assertThat(DivisionStatus.REGISTRATION_CLOSED.next()).isEqualTo(Optional.of(DivisionStatus.JUDGING));
        assertThat(DivisionStatus.JUDGING.next()).isEqualTo(Optional.of(DivisionStatus.DELIBERATION));
        assertThat(DivisionStatus.DELIBERATION.next()).isEqualTo(Optional.of(DivisionStatus.RESULTS_PUBLISHED));
    }

    @Test
    void shouldReturnEmptyForTerminalStatus() {
        assertThat(DivisionStatus.RESULTS_PUBLISHED.next()).isEmpty();
    }

    @Test
    void shouldReturnBadgeCssClassForEachStatus() {
        assertThat(DivisionStatus.DRAFT.getBadgeCssClass()).isEqualTo("badge-draft");
        assertThat(DivisionStatus.REGISTRATION_OPEN.getBadgeCssClass()).isEqualTo("badge-registration-open");
        assertThat(DivisionStatus.REGISTRATION_CLOSED.getBadgeCssClass()).isEqualTo("badge-registration-closed");
        assertThat(DivisionStatus.JUDGING.getBadgeCssClass()).isEqualTo("badge-judging");
        assertThat(DivisionStatus.DELIBERATION.getBadgeCssClass()).isEqualTo("badge-deliberation");
        assertThat(DivisionStatus.RESULTS_PUBLISHED.getBadgeCssClass()).isEqualTo("badge-results-published");
    }

    @ParameterizedTest
    @EnumSource(DivisionStatus.class)
    void shouldHaveNonBlankDisplayNameForAllStatuses(DivisionStatus status) {
        assertThat(status.getDisplayName()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(DivisionStatus.class)
    void shouldHaveNonBlankBadgeCssClassForAllStatuses(DivisionStatus status) {
        assertThat(status.getBadgeCssClass()).startsWith("badge-");
    }
}
