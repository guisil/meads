package app.meads.competition.internal;

import app.meads.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CompetitionUserDeletionGuardTest {

    @Mock
    private ParticipantRepository participantRepository;

    @InjectMocks
    private CompetitionUserDeletionGuard guard;

    @Test
    void shouldBlockDeletionWhenUserHasParticipantRecords() {
        var userId = UUID.randomUUID();
        given(participantRepository.existsByUserId(userId)).willReturn(true);

        assertThatThrownBy(() -> guard.checkDeletionAllowed(userId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.user.cannot-delete-has-data");
    }

    @Test
    void shouldAllowDeletionWhenUserHasNoParticipantRecords() {
        var userId = UUID.randomUUID();
        given(participantRepository.existsByUserId(userId)).willReturn(false);

        assertThatNoException().isThrownBy(() -> guard.checkDeletionAllowed(userId));
    }
}
