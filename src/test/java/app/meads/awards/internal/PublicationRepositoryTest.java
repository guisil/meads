package app.meads.awards.internal;

import app.meads.TestcontainersConfiguration;
import app.meads.awards.Publication;
import app.meads.competition.Competition;
import app.meads.competition.Division;
import app.meads.competition.ScoringSystem;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.DivisionRepository;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserStatus;
import app.meads.identity.internal.UserRepository;
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
class PublicationRepositoryTest {

    @Autowired PublicationRepository publicationRepository;
    @Autowired CompetitionRepository competitionRepository;
    @Autowired DivisionRepository divisionRepository;
    @Autowired UserRepository userRepository;

    private static class Fixtures {
        UUID divisionId;
        UUID userId;
    }

    private Fixtures createFixtures(String suffix) {
        var fx = new Fixtures();
        var competition = competitionRepository.save(new Competition("Test Competition", "test-" + suffix,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto"));
        var division = divisionRepository.save(new Division(competition.getId(),
                "Home", "home-" + suffix, ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC"));
        var user = userRepository.save(new User("admin-" + suffix + "@test.com",
                "Admin", UserStatus.ACTIVE, Role.USER));
        fx.divisionId = division.getId();
        fx.userId = user.getId();
        return fx;
    }

    @Test
    void shouldSaveAndFindLatestPublicationByDivisionId() {
        var fx = createFixtures("p1");

        publicationRepository.save(new Publication(fx.divisionId, fx.userId));
        publicationRepository.save(Publication.republish(fx.divisionId, 1, "Fix typo in entry name", fx.userId));

        var latest = publicationRepository.findTopByDivisionIdOrderByVersionDesc(fx.divisionId);
        assertThat(latest).isPresent();
        assertThat(latest.get().getVersion()).isEqualTo(2);
        assertThat(latest.get().getJustification()).isEqualTo("Fix typo in entry name");
        assertThat(latest.get().isInitial()).isFalse();
    }

    @Test
    void shouldReturnHistoryOrderedByVersionAsc() {
        var fx = createFixtures("p2");

        publicationRepository.save(new Publication(fx.divisionId, fx.userId));
        publicationRepository.save(Publication.republish(fx.divisionId, 1, "First correction", fx.userId));
        publicationRepository.save(Publication.republish(fx.divisionId, 2, "Second correction", fx.userId));

        var history = publicationRepository.findByDivisionIdOrderByVersionAsc(fx.divisionId);
        assertThat(history).hasSize(3);
        assertThat(history.get(0).getVersion()).isEqualTo(1);
        assertThat(history.get(0).isInitial()).isTrue();
        assertThat(history.get(1).getVersion()).isEqualTo(2);
        assertThat(history.get(2).getVersion()).isEqualTo(3);
    }

    @Test
    void shouldReturnTrueWhenAnyPublicationExistsForDivision() {
        var fx = createFixtures("p3");

        assertThat(publicationRepository.existsByDivisionId(fx.divisionId)).isFalse();
        publicationRepository.save(new Publication(fx.divisionId, fx.userId));
        assertThat(publicationRepository.existsByDivisionId(fx.divisionId)).isTrue();
    }
}
