package app.meads.competition;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.internal.CompetitionParticipantRepository;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.EventParticipantRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CompetitionParticipantRepositoryTest {

    @Autowired
    CompetitionParticipantRepository participantRepository;

    @Autowired
    EventParticipantRepository eventParticipantRepository;

    @Autowired
    CompetitionRepository competitionRepository;

    @Autowired
    MeadEventRepository meadEventRepository;

    @Autowired
    UserRepository userRepository;

    private MeadEvent createAndSaveEvent() {
        return meadEventRepository.save(new MeadEvent("Test Event",
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto"));
    }

    private Competition createAndSaveCompetition(UUID eventId) {
        return competitionRepository.save(new Competition(eventId,
                "Home", ScoringSystem.MJP));
    }

    private EventParticipant createAndSaveEventParticipant(UUID eventId, String email) {
        var user = userRepository.save(new User(email, "Test User",
                UserStatus.ACTIVE, Role.USER));
        return eventParticipantRepository.save(new EventParticipant(eventId, user.getId()));
    }

    @Test
    void shouldSaveAndRetrieve() {
        var event = createAndSaveEvent();
        var competition = createAndSaveCompetition(event.getId());
        var ep = createAndSaveEventParticipant(event.getId(), "cp-save@test.com");

        var cp = new CompetitionParticipant(
                competition.getId(), ep.getId(), CompetitionRole.JUDGE);
        participantRepository.save(cp);

        var found = participantRepository.findById(cp.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getCompetitionId()).isEqualTo(competition.getId());
        assertThat(found.get().getEventParticipantId()).isEqualTo(ep.getId());
        assertThat(found.get().getRole()).isEqualTo(CompetitionRole.JUDGE);
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void shouldFindByCompetitionId() {
        var event = createAndSaveEvent();
        var competition = createAndSaveCompetition(event.getId());
        var ep1 = createAndSaveEventParticipant(event.getId(), "cp-find1@test.com");
        var ep2 = createAndSaveEventParticipant(event.getId(), "cp-find2@test.com");

        participantRepository.save(new CompetitionParticipant(
                competition.getId(), ep1.getId(), CompetitionRole.JUDGE));
        participantRepository.save(new CompetitionParticipant(
                competition.getId(), ep2.getId(), CompetitionRole.ENTRANT));

        var results = participantRepository.findByCompetitionId(competition.getId());

        assertThat(results).hasSize(2);
    }

    @Test
    void shouldCheckExistsByCompetitionIdAndEventParticipantIdAndRole() {
        var event = createAndSaveEvent();
        var competition = createAndSaveCompetition(event.getId());
        var ep = createAndSaveEventParticipant(event.getId(), "cp-exists@test.com");

        participantRepository.save(new CompetitionParticipant(
                competition.getId(), ep.getId(), CompetitionRole.JUDGE));

        assertThat(participantRepository.existsByCompetitionIdAndEventParticipantIdAndRole(
                competition.getId(), ep.getId(), CompetitionRole.JUDGE)).isTrue();
        assertThat(participantRepository.existsByCompetitionIdAndEventParticipantIdAndRole(
                competition.getId(), ep.getId(), CompetitionRole.ENTRANT)).isFalse();
    }

    @Test
    void shouldFindByCompetitionIdAndEventParticipantId() {
        var event = createAndSaveEvent();
        var competition = createAndSaveCompetition(event.getId());
        var ep = createAndSaveEventParticipant(event.getId(), "cp-multi@test.com");

        participantRepository.save(new CompetitionParticipant(
                competition.getId(), ep.getId(), CompetitionRole.JUDGE));
        participantRepository.save(new CompetitionParticipant(
                competition.getId(), ep.getId(), CompetitionRole.ENTRANT));

        var results = participantRepository.findByCompetitionIdAndEventParticipantId(
                competition.getId(), ep.getId());

        assertThat(results).hasSize(2);
        assertThat(results).extracting(CompetitionParticipant::getRole)
                .containsExactlyInAnyOrder(CompetitionRole.JUDGE, CompetitionRole.ENTRANT);
    }
}
