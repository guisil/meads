package app.meads.judging;

import app.meads.BusinessRuleException;
import app.meads.competition.DivisionStatus;
import app.meads.judging.internal.JudgingDivisionStatusRevertGuard;
import app.meads.judging.internal.JudgingRepository;
import app.meads.judging.internal.JudgingRoundRepository;
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
    JudgingRoundRepository judgingRoundRepository;

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
    void shouldNotBlockWhenJudgingExistsButPhaseNotStartedAndNoRounds() {
        var judging = new Judging(divisionId);
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(judgingRoundRepository.findByJudgingId(judging.getId()))
                .willReturn(java.util.List.of());

        assertThatCode(() -> guard.checkRevertAllowed(divisionId,
                DivisionStatus.JUDGING, DivisionStatus.REGISTRATION_CLOSED))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowRevertWhenRoundsExistButAreAllPendingOrReady() {
        // Admin set up rounds (assigned judges + entries + tables) at
        // REGISTRATION_CLOSED, advanced to JUDGING, then realised they want to
        // adjust something only changeable at REG_CLOSED (e.g. BOS places,
        // sharedTables flag). No round has actually started — judging.phase is
        // still NOT_STARTED — so the revert is harmless and should be allowed.
        var judging = new Judging(divisionId);
        var pendingRound = new JudgingRound(judging.getId(), "M1A Panel",
                UUID.randomUUID(), null); // default status PENDING
        var readyRound = new JudgingRound(judging.getId(), "M1B Panel",
                UUID.randomUUID(), null);
        readyRound.markReady();
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(judgingRoundRepository.findByJudgingId(judging.getId()))
                .willReturn(java.util.List.of(pendingRound, readyRound));

        assertThatCode(() -> guard.checkRevertAllowed(divisionId,
                DivisionStatus.JUDGING, DivisionStatus.REGISTRATION_CLOSED))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldBlockWhenAnyRoundIsActive() {
        var judging = new Judging(divisionId);
        var activeRound = new JudgingRound(judging.getId(), "M1A Panel",
                UUID.randomUUID(), null);
        activeRound.assignJudge(UUID.randomUUID());
        activeRound.start();
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(judgingRoundRepository.findByJudgingId(judging.getId()))
                .willReturn(java.util.List.of(activeRound));

        assertThatThrownBy(() -> guard.checkRevertAllowed(divisionId,
                DivisionStatus.JUDGING, DivisionStatus.REGISTRATION_CLOSED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.division.cannot-revert-has-judging");
    }
}
