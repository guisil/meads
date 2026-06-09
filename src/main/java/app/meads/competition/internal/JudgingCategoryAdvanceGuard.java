package app.meads.competition.internal;

import app.meads.BusinessRuleException;
import app.meads.competition.CategoryScope;
import app.meads.competition.DivisionAdvanceGuard;
import app.meads.competition.DivisionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
class JudgingCategoryAdvanceGuard implements DivisionAdvanceGuard {

    private final DivisionCategoryRepository divisionCategoryRepository;

    JudgingCategoryAdvanceGuard(DivisionCategoryRepository divisionCategoryRepository) {
        this.divisionCategoryRepository = divisionCategoryRepository;
    }

    @Override
    public void checkAdvanceAllowed(UUID divisionId, DivisionStatus fromStatus, DivisionStatus toStatus) {
        if (fromStatus != DivisionStatus.REGISTRATION_CLOSED || toStatus != DivisionStatus.JUDGING) {
            return;
        }
        if (!divisionCategoryRepository.existsByDivisionIdAndScope(divisionId, CategoryScope.JUDGING)) {
            log.warn("Blocked REGISTRATION_CLOSED → JUDGING advance for division {}: no judging categories", divisionId);
            throw new BusinessRuleException("error.division.cannot-start-judging-without-categories");
        }
    }
}
