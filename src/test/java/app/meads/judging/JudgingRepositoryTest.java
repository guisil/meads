package app.meads.judging;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.Competition;
import app.meads.competition.Division;
import app.meads.competition.ScoringSystem;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.DivisionRepository;
import app.meads.judging.internal.JudgingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class JudgingRepositoryTest {

    @Autowired
    JudgingRepository judgingRepository;

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
    void shouldSaveAndFindJudgingByDivisionId() {
        var division = createAndSaveDivision();

        var judging = new Judging(division.getId());

        judgingRepository.save(judging);

        var found = judgingRepository.findByDivisionId(division.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getDivisionId()).isEqualTo(division.getId());
        assertThat(found.get().getPhase()).isEqualTo(JudgingPhase.NOT_STARTED);
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNull();
    }
}
