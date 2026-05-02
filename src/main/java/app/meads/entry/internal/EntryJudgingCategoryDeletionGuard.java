package app.meads.entry.internal;

import app.meads.BusinessRuleException;
import app.meads.competition.JudgingCategoryDeletionGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
class EntryJudgingCategoryDeletionGuard implements JudgingCategoryDeletionGuard {

    private final EntryRepository entryRepository;

    EntryJudgingCategoryDeletionGuard(EntryRepository entryRepository) {
        this.entryRepository = entryRepository;
    }

    @Override
    public void checkDeletionAllowed(UUID categoryId) {
        if (entryRepository.existsByFinalCategoryId(categoryId)) {
            log.warn("Blocked judging category deletion: category {} referenced by entries", categoryId);
            throw new BusinessRuleException("error.category.judging-has-entries");
        }
    }
}
