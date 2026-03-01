package app.meads.competition;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.MeadEventRepository;
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
class CompetitionRepositoryTest {

    @Autowired
    CompetitionRepository competitionRepository;

    @Autowired
    MeadEventRepository meadEventRepository;

    private MeadEvent createAndSaveEvent() {
        var event = new MeadEvent("Test Event",
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto");
        return meadEventRepository.save(event);
    }

    @Test
    void shouldSaveAndRetrieveCompetition() {
        var event = createAndSaveEvent();
        var competition = new Competition(event.getId(),
                "Home Competition", ScoringSystem.MJP);

        competitionRepository.save(competition);
        var found = competitionRepository.findById(competition.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Home Competition");
        assertThat(found.get().getEventId()).isEqualTo(event.getId());
        assertThat(found.get().getStatus()).isEqualTo(CompetitionStatus.DRAFT);
        assertThat(found.get().getScoringSystem()).isEqualTo(ScoringSystem.MJP);
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void shouldFindCompetitionsByEventId() {
        var event = createAndSaveEvent();
        competitionRepository.save(new Competition(event.getId(),
                "Home", ScoringSystem.MJP));
        competitionRepository.save(new Competition(event.getId(),
                "Professional", ScoringSystem.MJP));

        var otherEvent = meadEventRepository.save(new MeadEvent("Other",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), null));
        competitionRepository.save(new Competition(otherEvent.getId(),
                "Unrelated", ScoringSystem.MJP));

        var results = competitionRepository.findByEventId(event.getId());

        assertThat(results).hasSize(2);
        assertThat(results).extracting(Competition::getName)
                .containsExactlyInAnyOrder("Home", "Professional");
    }
}
