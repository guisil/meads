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
class RegistrationCategoryAdvanceGuard implements DivisionAdvanceGuard {

    private final DivisionCategoryRepository divisionCategoryRepository;

    RegistrationCategoryAdvanceGuard(DivisionCategoryRepository divisionCategoryRepository) {
        this.divisionCategoryRepository = divisionCategoryRepository;
    }

    @Override
    public void checkAdvanceAllowed(UUID divisionId, DivisionStatus fromStatus, DivisionStatus toStatus) {
        if (fromStatus != DivisionStatus.DRAFT || toStatus != DivisionStatus.REGISTRATION_OPEN) {
            return;
        }
        if (!divisionCategoryRepository.existsByDivisionIdAndScope(divisionId, CategoryScope.REGISTRATION)) {
            log.warn("Blocked DRAFT → REGISTRATION_OPEN advance for division {}: no registration categories", divisionId);
            throw new BusinessRuleException("error.division.cannot-open-registration-without-categories");
        }
    }
}
