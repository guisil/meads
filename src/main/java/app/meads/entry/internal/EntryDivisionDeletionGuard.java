package app.meads.entry.internal;

import app.meads.BusinessRuleException;
import app.meads.competition.DivisionDeletionGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
class EntryDivisionDeletionGuard implements DivisionDeletionGuard {

    private final EntryRepository entryRepository;
    private final EntryCreditRepository entryCreditRepository;
    private final ProductMappingRepository productMappingRepository;

    EntryDivisionDeletionGuard(EntryRepository entryRepository,
                                EntryCreditRepository entryCreditRepository,
                                ProductMappingRepository productMappingRepository) {
        this.entryRepository = entryRepository;
        this.entryCreditRepository = entryCreditRepository;
        this.productMappingRepository = productMappingRepository;
    }

    @Override
    public void checkDeletionAllowed(UUID divisionId) {
        if (entryRepository.existsByDivisionId(divisionId)) {
            log.warn("Blocked division deletion: division {} has entries", divisionId);
            throw new BusinessRuleException("error.division.cannot-delete-has-data");
        }
        if (entryCreditRepository.existsByDivisionId(divisionId)) {
            log.warn("Blocked division deletion: division {} has credits", divisionId);
            throw new BusinessRuleException("error.division.cannot-delete-has-data");
        }
        if (productMappingRepository.existsByDivisionId(divisionId)) {
            log.warn("Blocked division deletion: division {} has product mappings", divisionId);
            throw new BusinessRuleException("error.division.cannot-delete-has-data");
        }
    }
}
