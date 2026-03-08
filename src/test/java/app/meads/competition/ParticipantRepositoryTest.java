package app.meads.competition;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.ParticipantRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ParticipantRepositoryTest {

    @Autowired
    ParticipantRepository participantRepository;

    @Autowired
    CompetitionRepository competitionRepository;

    @Autowired
    UserRepository userRepository;

    private Competition createAndSaveCompetition() {
        return competitionRepository.save(new Competition("Test Competition", "test-competition",
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto"));
    }

    private User createAndSaveUser(String email) {
        return userRepository.save(new User(email, "Test User",
                UserStatus.ACTIVE, Role.USER));
    }

    @Test
    void shouldSaveAndRetrieve() {
        var competition = createAndSaveCompetition();
        var user = createAndSaveUser("p-save@test.com");

        var participant = new Participant(competition.getId(), user.getId());
        participantRepository.save(participant);

        var found = participantRepository.findById(participant.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getCompetitionId()).isEqualTo(competition.getId());
        assertThat(found.get().getUserId()).isEqualTo(user.getId());
        assertThat(found.get().getAccessCode()).isNull();
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void shouldFindByCompetitionId() {
        var competition = createAndSaveCompetition();
        var user1 = createAndSaveUser("p-find1@test.com");
        var user2 = createAndSaveUser("p-find2@test.com");

        participantRepository.save(new Participant(competition.getId(), user1.getId()));
        participantRepository.save(new Participant(competition.getId(), user2.getId()));

        var results = participantRepository.findByCompetitionId(competition.getId());

        assertThat(results).hasSize(2);
    }

    @Test
    void shouldFindByAccessCode() {
        var competition = createAndSaveCompetition();
        var user = createAndSaveUser("p-code@test.com");

        var participant = new Participant(competition.getId(), user.getId());
        participant.assignAccessCode("TESTCODE");
        participantRepository.save(participant);

        var found = participantRepository.findByAccessCode("TESTCODE");

        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(user.getId());
    }

    @Test
    void shouldFindByCompetitionIdAndUserId() {
        var competition = createAndSaveCompetition();
        var user = createAndSaveUser("p-compuser@test.com");

        participantRepository.save(new Participant(competition.getId(), user.getId()));

        var found = participantRepository.findByCompetitionIdAndUserId(
                competition.getId(), user.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getCompetitionId()).isEqualTo(competition.getId());
        assertThat(found.get().getUserId()).isEqualTo(user.getId());
    }
}
