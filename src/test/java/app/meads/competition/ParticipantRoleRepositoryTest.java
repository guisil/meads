package app.meads.competition;

import app.meads.TestcontainersConfiguration;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.ParticipantRepository;
import app.meads.competition.internal.ParticipantRoleRepository;
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
class ParticipantRoleRepositoryTest {

    @Autowired
    ParticipantRoleRepository participantRoleRepository;

    @Autowired
    ParticipantRepository participantRepository;

    @Autowired
    CompetitionRepository competitionRepository;

    @Autowired
    UserRepository userRepository;

    private Competition createAndSaveCompetition() {
        return competitionRepository.save(new Competition("Test Competition",
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto"));
    }

    private Participant createAndSaveParticipant(UUID competitionId, String email) {
        var user = userRepository.save(new User(email, "Test User",
                UserStatus.ACTIVE, Role.USER));
        return participantRepository.save(new Participant(competitionId, user.getId()));
    }

    @Test
    void shouldSaveAndRetrieve() {
        var competition = createAndSaveCompetition();
        var participant = createAndSaveParticipant(competition.getId(), "pr-save@test.com");

        var pr = new ParticipantRole(participant.getId(), CompetitionRole.JUDGE);
        participantRoleRepository.save(pr);

        var found = participantRoleRepository.findById(pr.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getParticipantId()).isEqualTo(participant.getId());
        assertThat(found.get().getRole()).isEqualTo(CompetitionRole.JUDGE);
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void shouldFindByParticipantId() {
        var competition = createAndSaveCompetition();
        var participant = createAndSaveParticipant(competition.getId(), "pr-find@test.com");

        participantRoleRepository.save(new ParticipantRole(
                participant.getId(), CompetitionRole.JUDGE));
        participantRoleRepository.save(new ParticipantRole(
                participant.getId(), CompetitionRole.ENTRANT));

        var results = participantRoleRepository.findByParticipantId(participant.getId());

        assertThat(results).hasSize(2);
    }

    @Test
    void shouldCheckExistsByParticipantIdAndRole() {
        var competition = createAndSaveCompetition();
        var participant = createAndSaveParticipant(competition.getId(), "pr-exists@test.com");

        participantRoleRepository.save(new ParticipantRole(
                participant.getId(), CompetitionRole.ADMIN));

        assertThat(participantRoleRepository.existsByParticipantIdAndRole(
                participant.getId(), CompetitionRole.ADMIN)).isTrue();
        assertThat(participantRoleRepository.existsByParticipantIdAndRole(
                participant.getId(), CompetitionRole.JUDGE)).isFalse();
    }
}
