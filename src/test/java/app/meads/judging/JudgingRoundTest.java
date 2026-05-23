package app.meads.judging;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JudgingRoundTest {

    @Test
    void shouldMarkReadyFromPending() {
        var round = new JudgingRound(UUID.randomUUID(), "T", UUID.randomUUID(), null);
        assertThat(round.getStatus()).isEqualTo(JudgingRoundStatus.PENDING);

        round.markReady();

        assertThat(round.getStatus()).isEqualTo(JudgingRoundStatus.READY);
    }

    @Test
    void shouldRevertReadyBackToPending() {
        var round = new JudgingRound(UUID.randomUUID(), "T", UUID.randomUUID(), null);
        round.markReady();

        round.markPending();

        assertThat(round.getStatus()).isEqualTo(JudgingRoundStatus.PENDING);
    }

    @Test
    void shouldStartFromReady() {
        var round = new JudgingRound(UUID.randomUUID(), "T", UUID.randomUUID(), null);
        round.markReady();

        round.start();

        assertThat(round.getStatus()).isEqualTo(JudgingRoundStatus.ACTIVE);
    }

    @Test
    void shouldRejectMarkReadyWhenAlreadyActive() {
        var round = new JudgingRound(UUID.randomUUID(), "T", UUID.randomUUID(), null);
        round.start();
        assertThat(round.getStatus()).isEqualTo(JudgingRoundStatus.ACTIVE);

        assertThatThrownBy(round::markReady)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACTIVE");
    }

    @Test
    void shouldRejectMarkPendingWhenNotReady() {
        var round = new JudgingRound(UUID.randomUUID(), "T", UUID.randomUUID(), null);
        assertThat(round.getStatus()).isEqualTo(JudgingRoundStatus.PENDING);

        assertThatThrownBy(round::markPending)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING");
    }
}
