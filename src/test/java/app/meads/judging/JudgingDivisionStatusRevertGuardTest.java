package app.meads.judging;

import app.meads.BusinessRuleException;
import app.meads.competition.DivisionStatus;
import app.meads.judging.internal.JudgingDivisionStatusRevertGuard;
import app.meads.judging.internal.JudgingRepository;
import app.meads.judging.internal.JudgingTableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class JudgingDivisionStatusRevertGuardTest {

    @InjectMocks
    JudgingDivisionStatusRevertGuard guard;

    @Mock
    JudgingRepository judgingRepository;

    @Mock
    JudgingTableRepository judgingTableRepository;

    UUID divisionId;

    @BeforeEach
    void setUp() {
        divisionId = UUID.randomUUID();
    }

    @Test
    void shouldNotBlockUnrelatedReverts() {
        // Reverting from REGISTRATION_OPEN to DRAFT — not our concern
        assertThatCode(() -> guard.checkRevertAllowed(divisionId,
                DivisionStatus.REGISTRATION_OPEN, DivisionStatus.DRAFT))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldNotBlockWhenNoJudgingExists() {
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.empty());

        assertThatCode(() -> guard.checkRevertAllowed(divisionId,
                DivisionStatus.JUDGING, DivisionStatus.REGISTRATION_CLOSED))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldNotBlockWhenJudgingExistsButPhaseNotStartedAndNoTables() {
        var judging = new Judging(divisionId);
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(judgingTableRepository.existsByJudgingId(judging.getId())).willReturn(false);

        assertThatCode(() -> guard.checkRevertAllowed(divisionId,
                DivisionStatus.JUDGING, DivisionStatus.REGISTRATION_CLOSED))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldBlockWhenJudgingPhaseIsActive() {
        var judging = new Judging(divisionId);
        judging.markActive();
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));

        assertThatThrownBy(() -> guard.checkRevertAllowed(divisionId,
                DivisionStatus.JUDGING, DivisionStatus.REGISTRATION_CLOSED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.division.cannot-revert-has-judging");
    }

    @Test
    void shouldBlockWhenJudgingTablesExist() {
        var judging = new Judging(divisionId);
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(judgingTableRepository.existsByJudgingId(judging.getId())).willReturn(true);

        assertThatThrownBy(() -> guard.checkRevertAllowed(divisionId,
                DivisionStatus.JUDGING, DivisionStatus.REGISTRATION_CLOSED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.division.cannot-revert-has-judging");
    }
}
