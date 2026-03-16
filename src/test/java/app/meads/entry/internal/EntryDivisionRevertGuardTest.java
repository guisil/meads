package app.meads.entry.internal;

import app.meads.BusinessRuleException;
import app.meads.competition.DivisionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class EntryDivisionRevertGuardTest {

    @Mock
    private EntryRepository entryRepository;

    @InjectMocks
    private EntryDivisionRevertGuard guard;

    @Test
    void shouldBlockRevertToDraftWhenEntriesExist() {
        var divisionId = UUID.randomUUID();
        given(entryRepository.existsByDivisionId(divisionId)).willReturn(true);

        assertThatThrownBy(() -> guard.checkRevertAllowed(
                divisionId, DivisionStatus.REGISTRATION_OPEN, DivisionStatus.DRAFT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.division.cannot-revert-has-entries");
    }

    @Test
    void shouldAllowRevertToDraftWhenNoEntriesExist() {
        var divisionId = UUID.randomUUID();
        given(entryRepository.existsByDivisionId(divisionId)).willReturn(false);

        assertThatNoException().isThrownBy(() -> guard.checkRevertAllowed(
                divisionId, DivisionStatus.REGISTRATION_OPEN, DivisionStatus.DRAFT));
    }

    @Test
    void shouldAllowRevertWhenTargetIsNotDraft() {
        var divisionId = UUID.randomUUID();

        assertThatNoException().isThrownBy(() -> guard.checkRevertAllowed(
                divisionId, DivisionStatus.JUDGING, DivisionStatus.REGISTRATION_CLOSED));
    }
}
