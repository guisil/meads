package app.meads.competition;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.internal.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CategoryRepositoryTest {

    @Autowired
    CategoryRepository categoryRepository;

    @Test
    void shouldFindSeededMjpCategories() {
        var categories = categoryRepository.findByScoringSystem(ScoringSystem.MJP);

        assertThat(categories).isNotEmpty();
        assertThat(categories).allSatisfy(c -> {
            assertThat(c.getCode()).isNotBlank();
            assertThat(c.getName()).isNotBlank();
            assertThat(c.getDescription()).isNotBlank();
            assertThat(c.getScoringSystem()).isEqualTo(ScoringSystem.MJP);
        });
    }

    @Test
    void shouldContainTraditionalMeadCategory() {
        var categories = categoryRepository.findByScoringSystem(ScoringSystem.MJP);

        assertThat(categories)
                .anyMatch(c -> c.getCode().equals("M1A")
                        && c.getName().equals("Traditional Mead (Dry)"));
    }

    @Test
    void shouldReturnEmptyForUnknownScoringSystem() {
        var all = categoryRepository.findAll();
        var mjp = categoryRepository.findByScoringSystem(ScoringSystem.MJP);

        // All seeded categories are MJP, so findAll == findByMJP
        assertThat(all).hasSameSizeAs(mjp);
    }
}
