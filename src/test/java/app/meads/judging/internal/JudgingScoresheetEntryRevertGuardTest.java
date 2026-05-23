package app.meads.judging.internal;

import app.meads.BusinessRuleException;
import app.meads.judging.Scoresheet;
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
class JudgingScoresheetEntryRevertGuardTest {

    @Mock
    private ScoresheetRepository scoresheetRepository;

    @InjectMocks
    private JudgingScoresheetEntryRevertGuard guard;

    @Test
    void shouldBlockRevertWhenScoresheetExists() {
        var entryId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        given(scoresheetRepository.findByEntryId(entryId))
                .willReturn(Optional.of(new Scoresheet(UUID.randomUUID(), entryId)));

        assertThatThrownBy(() -> guard.checkRevertAllowed(entryId, divisionId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.entry.revert-blocked-scoresheet-exists");
    }

    @Test
    void shouldAllowRevertWhenNoScoresheetExists() {
        var entryId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        given(scoresheetRepository.findByEntryId(entryId)).willReturn(Optional.empty());

        assertThatNoException().isThrownBy(() -> guard.checkRevertAllowed(entryId, divisionId));
    }
}
