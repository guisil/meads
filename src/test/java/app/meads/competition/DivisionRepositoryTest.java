package app.meads.competition;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.DivisionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DivisionRepositoryTest {

    @Autowired
    DivisionRepository divisionRepository;

    @Autowired
    CompetitionRepository competitionRepository;

    private Competition createAndSaveCompetition() {
        var competition = new Competition("Test Competition",
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto");
        return competitionRepository.save(competition);
    }

    @Test
    void shouldSaveAndRetrieveDivision() {
        var competition = createAndSaveCompetition();
        var division = new Division(competition.getId(),
                "Home Division", ScoringSystem.MJP);

        divisionRepository.save(division);
        var found = divisionRepository.findById(division.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Home Division");
        assertThat(found.get().getCompetitionId()).isEqualTo(competition.getId());
        assertThat(found.get().getStatus()).isEqualTo(DivisionStatus.DRAFT);
        assertThat(found.get().getScoringSystem()).isEqualTo(ScoringSystem.MJP);
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void shouldFindDivisionsByCompetitionId() {
        var competition = createAndSaveCompetition();
        divisionRepository.save(new Division(competition.getId(),
                "Home", ScoringSystem.MJP));
        divisionRepository.save(new Division(competition.getId(),
                "Professional", ScoringSystem.MJP));

        var otherCompetition = competitionRepository.save(new Competition("Other",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), null));
        divisionRepository.save(new Division(otherCompetition.getId(),
                "Unrelated", ScoringSystem.MJP));

        var results = divisionRepository.findByCompetitionId(competition.getId());

        assertThat(results).hasSize(2);
        assertThat(results).extracting(Division::getName)
                .containsExactlyInAnyOrder("Home", "Professional");
    }
}
