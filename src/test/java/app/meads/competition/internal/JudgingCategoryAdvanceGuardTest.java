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
class JudgingCategoryAdvanceGuardTest {

    @Mock
    private DivisionCategoryRepository divisionCategoryRepository;

    @InjectMocks
    private JudgingCategoryAdvanceGuard guard;

    @Test
    void shouldBlockAdvanceToJudgingWhenNoJudgingCategoriesExist() {
        var divisionId = UUID.randomUUID();
        given(divisionCategoryRepository.existsByDivisionIdAndScope(
                divisionId, CategoryScope.JUDGING)).willReturn(false);

        assertThatThrownBy(() -> guard.checkAdvanceAllowed(
                divisionId, DivisionStatus.REGISTRATION_CLOSED, DivisionStatus.JUDGING))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.division.cannot-start-judging-without-categories");
    }

    @Test
    void shouldAllowAdvanceToJudgingWhenJudgingCategoriesExist() {
        var divisionId = UUID.randomUUID();
        given(divisionCategoryRepository.existsByDivisionIdAndScope(
                divisionId, CategoryScope.JUDGING)).willReturn(true);

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
}
