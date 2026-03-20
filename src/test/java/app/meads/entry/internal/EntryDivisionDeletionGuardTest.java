package app.meads.entry.internal;

import app.meads.BusinessRuleException;
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
class EntryDivisionDeletionGuardTest {

    @Mock
    private EntryRepository entryRepository;

    @Mock
    private EntryCreditRepository entryCreditRepository;

    @Mock
    private ProductMappingRepository productMappingRepository;

    @InjectMocks
    private EntryDivisionDeletionGuard guard;

    @Test
    void shouldBlockDeletionWhenEntriesExist() {
        var divisionId = UUID.randomUUID();
        given(entryRepository.existsByDivisionId(divisionId)).willReturn(true);

        assertThatThrownBy(() -> guard.checkDeletionAllowed(divisionId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.division.cannot-delete-has-data");
    }

    @Test
    void shouldBlockDeletionWhenCreditsExist() {
        var divisionId = UUID.randomUUID();
        given(entryRepository.existsByDivisionId(divisionId)).willReturn(false);
        given(entryCreditRepository.existsByDivisionId(divisionId)).willReturn(true);

        assertThatThrownBy(() -> guard.checkDeletionAllowed(divisionId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.division.cannot-delete-has-data");
    }

    @Test
    void shouldBlockDeletionWhenProductMappingsExist() {
        var divisionId = UUID.randomUUID();
        given(entryRepository.existsByDivisionId(divisionId)).willReturn(false);
        given(entryCreditRepository.existsByDivisionId(divisionId)).willReturn(false);
        given(productMappingRepository.existsByDivisionId(divisionId)).willReturn(true);

        assertThatThrownBy(() -> guard.checkDeletionAllowed(divisionId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.division.cannot-delete-has-data");
    }

    @Test
    void shouldAllowDeletionWhenNoDataExists() {
        var divisionId = UUID.randomUUID();
        given(entryRepository.existsByDivisionId(divisionId)).willReturn(false);
        given(entryCreditRepository.existsByDivisionId(divisionId)).willReturn(false);
        given(productMappingRepository.existsByDivisionId(divisionId)).willReturn(false);

        assertThatNoException().isThrownBy(() -> guard.checkDeletionAllowed(divisionId));
    }
}
