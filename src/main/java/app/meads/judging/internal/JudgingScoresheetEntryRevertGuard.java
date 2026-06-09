package app.meads.judging.internal;

import app.meads.BusinessRuleException;
import app.meads.entry.EntryStatusRevertGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Blocks an entry's status revert (RECEIVED → SUBMITTED) or withdrawal when a
 * scoresheet exists on a judging table — judging data would otherwise be left
 * orphaned. The admin must delete the scoresheet from the table first.
 */
@Slf4j
@Component
class JudgingScoresheetEntryRevertGuard implements EntryStatusRevertGuard {

    private final ScoresheetRepository scoresheetRepository;

    JudgingScoresheetEntryRevertGuard(ScoresheetRepository scoresheetRepository) {
        this.scoresheetRepository = scoresheetRepository;
    }

    @Override
    public void checkRevertAllowed(UUID entryId, UUID divisionId) {
        if (scoresheetRepository.findByEntryId(entryId).isPresent()) {
            log.warn("Blocked entry status revert/withdraw for entry {}: scoresheet exists", entryId);
            throw new BusinessRuleException("error.entry.revert-blocked-scoresheet-exists");
        }
    }
}
