package app.meads.awards.internal;

import app.meads.BusinessRuleException;
import app.meads.competition.DivisionStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlockManualPublishAdvanceGuardTest {

    private final BlockManualPublishAdvanceGuard guard = new BlockManualPublishAdvanceGuard();

    @Test
    void shouldBlockManualAdvanceFromDeliberationToResultsPublished() {
        var divisionId = UUID.randomUUID();

        assertThatThrownBy(() -> guard.checkAdvanceAllowed(
                divisionId, DivisionStatus.DELIBERATION, DivisionStatus.RESULTS_PUBLISHED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.division.use-publish-results-instead");
    }

    @Test
    void shouldIgnoreOtherTransitions() {
        var divisionId = UUID.randomUUID();

        assertThatNoException().isThrownBy(() -> guard.checkAdvanceAllowed(
                divisionId, DivisionStatus.DRAFT, DivisionStatus.REGISTRATION_OPEN));
        assertThatNoException().isThrownBy(() -> guard.checkAdvanceAllowed(
                divisionId, DivisionStatus.JUDGING, DivisionStatus.DELIBERATION));
        assertThatNoException().isThrownBy(() -> guard.checkAdvanceAllowed(
                divisionId, DivisionStatus.REGISTRATION_CLOSED, DivisionStatus.JUDGING));
    }
}
