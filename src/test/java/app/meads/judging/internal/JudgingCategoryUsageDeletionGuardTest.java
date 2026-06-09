package app.meads.judging.internal;

import app.meads.BusinessRuleException;
import app.meads.judging.CategoryJudgingConfig;
import app.meads.judging.JudgingRound;
import app.meads.judging.MedalRoundMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class JudgingCategoryUsageDeletionGuardTest {

    @Mock
    private CategoryJudgingConfigRepository configRepository;
    @Mock
    private JudgingRoundRepository judgingRoundRepository;
    @Mock
    private MedalAwardRepository medalAwardRepository;
    @InjectMocks
    private JudgingCategoryUsageDeletionGuard guard;

    @Test
    void shouldBlockDeletionWhenCategoryHasAJudgingRound() {
        var categoryId = UUID.randomUUID();
        given(judgingRoundRepository.findByDivisionCategoryId(categoryId))
                .willReturn(List.of(new JudgingRound(UUID.randomUUID(), "R1", categoryId, null)));
        given(medalAwardRepository.findByFinalCategoryId(categoryId)).willReturn(List.of());
        given(configRepository.findByDivisionCategoryId(categoryId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> guard.checkDeletionAllowed(categoryId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.category.judging-in-use");
    }

    @Test
    void shouldBlockDeletionWhenCategoryHasOnlyAJudgingConfig() {
        // The FK that actually fired in the field — a config with no rounds yet.
        var categoryId = UUID.randomUUID();
        given(judgingRoundRepository.findByDivisionCategoryId(categoryId)).willReturn(List.of());
        given(medalAwardRepository.findByFinalCategoryId(categoryId)).willReturn(List.of());
        given(configRepository.findByDivisionCategoryId(categoryId))
                .willReturn(Optional.of(new CategoryJudgingConfig(categoryId, MedalRoundMode.COMPARATIVE)));

        assertThatThrownBy(() -> guard.checkDeletionAllowed(categoryId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.category.judging-in-use");
    }

    @Test
    void shouldAllowDeletionWhenCategoryHasNoJudgingReferences() {
        var categoryId = UUID.randomUUID();
        given(judgingRoundRepository.findByDivisionCategoryId(categoryId)).willReturn(List.of());
        given(medalAwardRepository.findByFinalCategoryId(categoryId)).willReturn(List.of());
        given(configRepository.findByDivisionCategoryId(categoryId)).willReturn(Optional.empty());

        assertThatNoException().isThrownBy(() -> guard.checkDeletionAllowed(categoryId));
    }
}
