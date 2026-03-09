package app.meads.entry.internal;

import app.meads.competition.DivisionRevertGuard;
import app.meads.competition.DivisionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
class EntryDivisionRevertGuard implements DivisionRevertGuard {

    private final EntryRepository entryRepository;

    EntryDivisionRevertGuard(EntryRepository entryRepository) {
        this.entryRepository = entryRepository;
    }

    @Override
    public void checkRevertAllowed(UUID divisionId, DivisionStatus fromStatus, DivisionStatus toStatus) {
        if (toStatus == DivisionStatus.DRAFT && entryRepository.existsByDivisionId(divisionId)) {
            log.warn("Blocked division revert to DRAFT: division {} has entries", divisionId);
            throw new IllegalStateException(
                    "Cannot revert to DRAFT: division has entries");
        }
    }
}
