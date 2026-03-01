package app.meads.competition;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.internal.CompetitionParticipantRepository;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.MeadEventRepository;
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
class CompetitionParticipantRepositoryTest {

    @Autowired
    CompetitionParticipantRepository participantRepository;

    @Autowired
    CompetitionRepository competitionRepository;

    @Autowired
    MeadEventRepository meadEventRepository;

    @Autowired
    UserRepository userRepository;

    private MeadEvent createAndSaveEvent() {
        var event = new MeadEvent("Test Event",
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto");
        return meadEventRepository.save(event);
    }

    private Competition createAndSaveCompetition(java.util.UUID eventId) {
        var competition = new Competition(eventId,
                "Home", ScoringSystem.MJP);
        return competitionRepository.save(competition);
    }

    private User createAndSaveUser(String email) {
        var user = new User(email, "Test User",
                UserStatus.ACTIVE, Role.USER);
        return userRepository.save(user);
    }

    @Test
    void shouldSaveAndRetrieveParticipant() {
        var event = createAndSaveEvent();
        var competition = createAndSaveCompetition(event.getId());
        var user = createAndSaveUser("judge@test.com");

        var participant = new CompetitionParticipant(
                competition.getId(), user.getId(), CompetitionRole.JUDGE);
        participant.assignAccessCode("AB3K9XYZ");

        participantRepository.save(participant);
        var found = participantRepository.findById(participant.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getCompetitionId()).isEqualTo(competition.getId());
        assertThat(found.get().getUserId()).isEqualTo(user.getId());
        assertThat(found.get().getRole()).isEqualTo(CompetitionRole.JUDGE);
        assertThat(found.get().getAccessCode()).isEqualTo("AB3K9XYZ");
        assertThat(found.get().getStatus()).isEqualTo(CompetitionParticipantStatus.ACTIVE);
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void shouldPersistWithdrawnStatus() {
        var event = createAndSaveEvent();
        var competition = createAndSaveCompetition(event.getId());
        var user = createAndSaveUser("withdrawn@test.com");

        var participant = new CompetitionParticipant(
                competition.getId(), user.getId(), CompetitionRole.JUDGE);
        participantRepository.save(participant);

        participant.withdraw();
        participantRepository.save(participant);

        var found = participantRepository.findById(participant.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(CompetitionParticipantStatus.WITHDRAWN);
    }

    @Test
    void shouldFindParticipantsByCompetitionId() {
        var event = createAndSaveEvent();
        var competition = createAndSaveCompetition(event.getId());
        var user1 = createAndSaveUser("judge1@test.com");
        var user2 = createAndSaveUser("steward1@test.com");

        participantRepository.save(new CompetitionParticipant(
                competition.getId(), user1.getId(), CompetitionRole.JUDGE));
        participantRepository.save(new CompetitionParticipant(
                competition.getId(), user2.getId(), CompetitionRole.STEWARD));

        var results = participantRepository.findByCompetitionId(competition.getId());

        assertThat(results).hasSize(2);
    }

    @Test
    void shouldFindByCompetitionIdAndUserId() {
        var event = createAndSaveEvent();
        var competition = createAndSaveCompetition(event.getId());
        var user = createAndSaveUser("find@test.com");

        participantRepository.save(new CompetitionParticipant(
                competition.getId(), user.getId(), CompetitionRole.ENTRANT));

        var found = participantRepository.findByCompetitionIdAndUserId(
                competition.getId(), user.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getRole()).isEqualTo(CompetitionRole.ENTRANT);
    }

    @Test
    void shouldFindByAccessCode() {
        var event = createAndSaveEvent();
        var competition = createAndSaveCompetition(event.getId());
        var user = createAndSaveUser("code@test.com");

        var participant = new CompetitionParticipant(
                competition.getId(), user.getId(), CompetitionRole.JUDGE);
        participant.assignAccessCode("TESTCODE");
        participantRepository.save(participant);

        var results = participantRepository.findByAccessCode("TESTCODE");

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getUserId()).isEqualTo(user.getId());
    }

    @Test
    void shouldCheckExistsByCompetitionIdAndUserId() {
        var event = createAndSaveEvent();
        var competition = createAndSaveCompetition(event.getId());
        var user = createAndSaveUser("exists@test.com");

        participantRepository.save(new CompetitionParticipant(
                competition.getId(), user.getId(), CompetitionRole.JUDGE));

        assertThat(participantRepository.existsByCompetitionIdAndUserId(
                competition.getId(), user.getId())).isTrue();
        assertThat(participantRepository.existsByCompetitionIdAndUserId(
                competition.getId(), java.util.UUID.randomUUID())).isFalse();
    }
}
