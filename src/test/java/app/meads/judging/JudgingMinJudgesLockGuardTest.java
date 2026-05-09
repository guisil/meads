package app.meads.judging;

import app.meads.judging.internal.JudgingMinJudgesLockGuard;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class JudgingMinJudgesLockGuardTest {

    @InjectMocks
    JudgingMinJudgesLockGuard guard;

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
    void shouldNotBeLockedWhenNoJudgingExists() {
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.empty());

        assertThat(guard.isLocked(divisionId)).isFalse();
    }

    @Test
    void shouldNotBeLockedWhenAllTablesNotStarted() {
        var judging = new Judging(divisionId);
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(judgingTableRepository.existsStartedByJudgingId(judging.getId())).willReturn(false);

        assertThat(guard.isLocked(divisionId)).isFalse();
    }

    @Test
    void shouldBeLockedWhenAnyTableHasStarted() {
        var judging = new Judging(divisionId);
        given(judgingRepository.findByDivisionId(divisionId)).willReturn(Optional.of(judging));
        given(judgingTableRepository.existsStartedByJudgingId(judging.getId())).willReturn(true);

        assertThat(guard.isLocked(divisionId)).isTrue();
    }
}
