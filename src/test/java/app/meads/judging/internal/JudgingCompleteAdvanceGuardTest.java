package app.meads.judging.internal;

import app.meads.BusinessRuleException;
import app.meads.competition.DivisionStatus;
import app.meads.judging.Judging;
import app.meads.judging.JudgingPhase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class JudgingCompleteAdvanceGuardTest {

    @Mock
    private JudgingRepository judgingRepository;

    @InjectMocks
    private JudgingCompleteAdvanceGuard guard;

    @Test
    void shouldBlockAdvanceToDeliberationWhenJudgingNotStarted() {
        var divisionId = UUID.randomUUID();
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> guard.checkAdvanceAllowed(
                divisionId, DivisionStatus.JUDGING, DivisionStatus.DELIBERATION))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.division.cannot-deliberate-judging-incomplete");
    }

    @Test
    void shouldBlockAdvanceToDeliberationWhenJudgingActive() {
        var divisionId = UUID.randomUUID();
        var judging = new Judging(divisionId);
        judging.markActive();
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));

        assertThatThrownBy(() -> guard.checkAdvanceAllowed(
                divisionId, DivisionStatus.JUDGING, DivisionStatus.DELIBERATION))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.division.cannot-deliberate-judging-incomplete");
    }

    @Test
    void shouldAllowAdvanceToDeliberationWhenJudgingComplete() {
        var divisionId = UUID.randomUUID();
        var judging = new Judging(divisionId);
        judging.markActive();
        judging.startBos();
        judging.completeBos();
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));

        assertThatNoException().isThrownBy(() -> guard.checkAdvanceAllowed(
                divisionId, DivisionStatus.JUDGING, DivisionStatus.DELIBERATION));
    }

    @Test
    void shouldIgnoreOtherTransitions() {
        var divisionId = UUID.randomUUID();

        assertThatNoException().isThrownBy(() -> guard.checkAdvanceAllowed(
                divisionId, DivisionStatus.REGISTRATION_CLOSED, DivisionStatus.JUDGING));
        assertThatNoException().isThrownBy(() -> guard.checkAdvanceAllowed(
                divisionId, DivisionStatus.DRAFT, DivisionStatus.REGISTRATION_OPEN));
    }
}
