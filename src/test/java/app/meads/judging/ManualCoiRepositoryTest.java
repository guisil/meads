package app.meads.judging;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.Competition;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import app.meads.identity.internal.UserRepository;
import app.meads.judging.internal.ManualCoi;
import app.meads.judging.internal.ManualCoiRepository;
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
class ManualCoiRepositoryTest {

    @Autowired
    ManualCoiRepository manualCoiRepository;

    @Autowired
    CompetitionRepository competitionRepository;

    @Autowired
    UserRepository userRepository;

    private static class Fixtures {
        Competition competition;
        User judge;
        User entrant;
    }

    private Fixtures createFixtures(String suffix) {
        var fx = new Fixtures();
        fx.competition = competitionRepository.save(new Competition("Test Competition", "test-" + suffix,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto"));
        fx.judge = userRepository.save(new User("judge-" + suffix + "@test.com",
                "Judge", UserStatus.ACTIVE, Role.USER));
        fx.entrant = userRepository.save(new User("entrant-" + suffix + "@test.com",
                "Entrant", UserStatus.ACTIVE, Role.USER));
        return fx;
    }

    @Test
    void shouldSaveAndFindByCompetitionId() {
        var fx = createFixtures("m1");

        var coi = new ManualCoi(fx.competition.getId(), fx.judge.getId(),
                fx.entrant.getId(), fx.judge.getId());
        manualCoiRepository.save(coi);

        var found = manualCoiRepository.findByCompetitionId(fx.competition.getId());
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getCompetitionId()).isEqualTo(fx.competition.getId());
        assertThat(found.getFirst().getJudgeUserId()).isEqualTo(fx.judge.getId());
        assertThat(found.getFirst().getEntrantUserId()).isEqualTo(fx.entrant.getId());
        assertThat(found.getFirst().getCreatedBy()).isEqualTo(fx.judge.getId());
        assertThat(found.getFirst().getCreatedAt()).isNotNull();
    }

    @Test
    void shouldReportExistenceForJudgeEntrantPairInCompetition() {
        var fx = createFixtures("m2");
        manualCoiRepository.save(new ManualCoi(fx.competition.getId(), fx.judge.getId(),
                fx.entrant.getId(), fx.judge.getId()));

        assertThat(manualCoiRepository.existsByCompetitionIdAndJudgeUserIdAndEntrantUserId(
                fx.competition.getId(), fx.judge.getId(), fx.entrant.getId())).isTrue();
        assertThat(manualCoiRepository.existsByCompetitionIdAndJudgeUserIdAndEntrantUserId(
                fx.competition.getId(), fx.entrant.getId(), fx.judge.getId())).isFalse();
    }
}
