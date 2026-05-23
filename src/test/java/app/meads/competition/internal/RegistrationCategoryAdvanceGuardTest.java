package app.meads.competition.internal;

import app.meads.BusinessRuleException;
import app.meads.competition.CategoryScope;
import app.meads.competition.DivisionStatus;
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
class RegistrationCategoryAdvanceGuardTest {

    @Mock
    private DivisionCategoryRepository divisionCategoryRepository;

    @InjectMocks
    private RegistrationCategoryAdvanceGuard guard;

    @Test
    void shouldBlockAdvanceToRegistrationOpenWhenNoRegistrationCategoriesExist() {
        var divisionId = UUID.randomUUID();
        given(divisionCategoryRepository.existsByDivisionIdAndScope(
                divisionId, CategoryScope.REGISTRATION)).willReturn(false);

        assertThatThrownBy(() -> guard.checkAdvanceAllowed(
                divisionId, DivisionStatus.DRAFT, DivisionStatus.REGISTRATION_OPEN))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.division.cannot-open-registration-without-categories");
    }

    @Test
    void shouldAllowAdvanceToRegistrationOpenWhenRegistrationCategoriesExist() {
        var divisionId = UUID.randomUUID();
        given(divisionCategoryRepository.existsByDivisionIdAndScope(
                divisionId, CategoryScope.REGISTRATION)).willReturn(true);

        assertThatNoException().isThrownBy(() -> guard.checkAdvanceAllowed(
                divisionId, DivisionStatus.DRAFT, DivisionStatus.REGISTRATION_OPEN));
    }

    @Test
    void shouldIgnoreOtherTransitions() {
        var divisionId = UUID.randomUUID();

        assertThatNoException().isThrownBy(() -> guard.checkAdvanceAllowed(
                divisionId, DivisionStatus.REGISTRATION_OPEN, DivisionStatus.REGISTRATION_CLOSED));
        assertThatNoException().isThrownBy(() -> guard.checkAdvanceAllowed(
                divisionId, DivisionStatus.JUDGING, DivisionStatus.DELIBERATION));
    }
}
