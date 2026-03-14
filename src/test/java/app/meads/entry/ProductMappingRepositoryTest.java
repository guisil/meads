package app.meads.entry;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.Competition;
import app.meads.competition.Division;
import app.meads.competition.ScoringSystem;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.DivisionRepository;
import app.meads.entry.internal.ProductMappingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ProductMappingRepositoryTest {

    @Autowired
    ProductMappingRepository productMappingRepository;

    @Autowired
    CompetitionRepository competitionRepository;

    @Autowired
    DivisionRepository divisionRepository;

    private Division createAndSaveDivision() {
        var competition = competitionRepository.save(new Competition("Test Competition", "test-competition",
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto"));
        return divisionRepository.save(new Division(competition.getId(),
                "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC"));
    }

    @Test
    void shouldSaveAndFindByDivisionId() {
        var division = createAndSaveDivision();

        var mapping1 = new ProductMapping(division.getId(), "PROD-001", "SKU-001",
                "Entry Pack", 1);
        var mapping2 = new ProductMapping(division.getId(), "PROD-002", "SKU-002",
                "Entry Pack x3", 3);
        productMappingRepository.save(mapping1);
        productMappingRepository.save(mapping2);

        var found = productMappingRepository.findByDivisionId(division.getId());

        assertThat(found).hasSize(2);
        assertThat(found).extracting(ProductMapping::getJumpsellerProductId)
                .containsExactlyInAnyOrder("PROD-001", "PROD-002");
        assertThat(found.getFirst().getCreatedAt()).isNotNull();
    }

    @Test
    void shouldCheckExistsByDivisionIdAndJumpsellerProductId() {
        var division = createAndSaveDivision();

        productMappingRepository.save(new ProductMapping(division.getId(), "PROD-001",
                "SKU-001", "Entry Pack", 1));

        assertThat(productMappingRepository.existsByDivisionIdAndJumpsellerProductId(
                division.getId(), "PROD-001")).isTrue();
        assertThat(productMappingRepository.existsByDivisionIdAndJumpsellerProductId(
                division.getId(), "PROD-999")).isFalse();
    }

    @Test
    void shouldFindByJumpsellerProductId() {
        var division = createAndSaveDivision();

        productMappingRepository.save(new ProductMapping(division.getId(), "PROD-001",
                "SKU-001", "Entry Pack", 1));

        var found = productMappingRepository.findByJumpsellerProductId("PROD-001");

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getDivisionId()).isEqualTo(division.getId());
    }

    @Test
    void shouldNotFindByNonExistentDivisionId() {
        var found = productMappingRepository.findByDivisionId(UUID.randomUUID());
        assertThat(found).isEmpty();
    }
}
