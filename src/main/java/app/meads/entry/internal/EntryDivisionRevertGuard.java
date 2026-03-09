package app.meads.entry.internal;

import app.meads.competition.DivisionRevertGuard;
import app.meads.competition.DivisionStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class EntryDivisionRevertGuard implements DivisionRevertGuard {

    private final EntryRepository entryRepository;

    EntryDivisionRevertGuard(EntryRepository entryRepository) {
        this.entryRepository = entryRepository;
    }

    @Override
    public void checkRevertAllowed(UUID divisionId, DivisionStatus fromStatus, DivisionStatus toStatus) {
        if (toStatus == DivisionStatus.DRAFT && entryRepository.existsByDivisionId(divisionId)) {
            throw new IllegalStateException(
                    "Cannot revert to DRAFT: division has entries");
        }
    }
}
