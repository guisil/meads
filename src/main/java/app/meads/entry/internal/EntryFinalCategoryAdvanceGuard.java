package app.meads.entry.internal;

import app.meads.BusinessRuleException;
import app.meads.competition.DivisionAdvanceGuard;
import app.meads.competition.DivisionStatus;
import app.meads.entry.EntryStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
class EntryFinalCategoryAdvanceGuard implements DivisionAdvanceGuard {

    private static final Set<EntryStatus> IN_FLIGHT_STATUSES =
            EnumSet.of(EntryStatus.SUBMITTED, EntryStatus.RECEIVED);

    private final EntryRepository entryRepository;

    EntryFinalCategoryAdvanceGuard(EntryRepository entryRepository) {
        this.entryRepository = entryRepository;
    }

    @Override
    public void checkAdvanceAllowed(UUID divisionId, DivisionStatus fromStatus, DivisionStatus toStatus) {
        if (fromStatus != DivisionStatus.REGISTRATION_CLOSED || toStatus != DivisionStatus.JUDGING) {
            return;
        }
        long missing = entryRepository.findByDivisionId(divisionId).stream()
                .filter(e -> e.getFinalCategoryId() == null)
                .filter(e -> IN_FLIGHT_STATUSES.contains(e.getStatus()))
                .count();
        if (missing > 0) {
            log.warn("Blocked REGISTRATION_CLOSED → JUDGING advance for division {}: {} entry/entries missing final category",
                    divisionId, missing);
            throw new BusinessRuleException(
                    "error.division.cannot-start-judging-entries-without-final-category",
                    String.valueOf(missing));
        }
    }
}
