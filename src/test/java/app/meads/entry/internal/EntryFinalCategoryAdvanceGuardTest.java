package app.meads.entry.internal;

import app.meads.BusinessRuleException;
import app.meads.competition.DivisionStatus;
import app.meads.entry.Carbonation;
import app.meads.entry.Entry;
import app.meads.entry.EntryStatus;
import app.meads.entry.Sweetness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class EntryFinalCategoryAdvanceGuardTest {

    @Mock
    private EntryRepository entryRepository;

    @InjectMocks
    private EntryFinalCategoryAdvanceGuard guard;

    @Test
    void shouldBlockAdvanceToJudgingWhenSubmittedOrReceivedEntriesLackFinalCategory() {
        var divisionId = UUID.randomUUID();
        given(entryRepository.findByDivisionId(divisionId)).willReturn(List.of(
                entryWithStatus(divisionId, EntryStatus.SUBMITTED, null),
                entryWithStatus(divisionId, EntryStatus.RECEIVED, null),
                entryWithStatus(divisionId, EntryStatus.RECEIVED, UUID.randomUUID()),
                entryWithStatus(divisionId, EntryStatus.DRAFT, null),
                entryWithStatus(divisionId, EntryStatus.WITHDRAWN, null)));

        assertThatThrownBy(() -> guard.checkAdvanceAllowed(
                divisionId, DivisionStatus.REGISTRATION_CLOSED, DivisionStatus.JUDGING))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.division.cannot-start-judging-entries-without-final-category");
    }

    @Test
    void shouldAllowAdvanceToJudgingWhenAllInFlightEntriesHaveFinalCategory() {
        var divisionId = UUID.randomUUID();
        given(entryRepository.findByDivisionId(divisionId)).willReturn(List.of(
                entryWithStatus(divisionId, EntryStatus.SUBMITTED, UUID.randomUUID()),
                entryWithStatus(divisionId, EntryStatus.RECEIVED, UUID.randomUUID()),
                entryWithStatus(divisionId, EntryStatus.DRAFT, null),
                entryWithStatus(divisionId, EntryStatus.WITHDRAWN, null)));

        assertThatNoException().isThrownBy(() -> guard.checkAdvanceAllowed(
                divisionId, DivisionStatus.REGISTRATION_CLOSED, DivisionStatus.JUDGING));
    }

    @Test
    void shouldIgnoreOtherTransitions() {
        var divisionId = UUID.randomUUID();

        assertThatNoException().isThrownBy(() -> guard.checkAdvanceAllowed(
                divisionId, DivisionStatus.DRAFT, DivisionStatus.REGISTRATION_OPEN));
        assertThatNoException().isThrownBy(() -> guard.checkAdvanceAllowed(
                divisionId, DivisionStatus.JUDGING, DivisionStatus.DELIBERATION));
    }

    private Entry entryWithStatus(UUID divisionId, EntryStatus status, UUID finalCategoryId) {
        var entry = new Entry(divisionId, UUID.randomUUID(), 1, "AMA-1",
                "Mead", UUID.randomUUID(), Sweetness.DRY, new BigDecimal("12.0"),
                Carbonation.STILL, "Wildflower", null, false, null, null);
        switch (status) {
            case DRAFT -> { /* already DRAFT */ }
            case SUBMITTED -> entry.advanceStatus();
            case RECEIVED -> { entry.advanceStatus(); entry.advanceStatus(); }
            case WITHDRAWN -> entry.withdraw();
        }
        if (finalCategoryId != null) {
            entry.assignFinalCategory(finalCategoryId);
        }
        return entry;
    }
}
