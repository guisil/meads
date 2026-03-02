package app.meads.competition;

import app.meads.TestcontainersConfiguration;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class EventParticipantRepositoryTest {

    @Autowired
    EventParticipantRepository eventParticipantRepository;

    @Autowired
    MeadEventRepository meadEventRepository;

    @Autowired
    UserRepository userRepository;

    private MeadEvent createAndSaveEvent() {
        return meadEventRepository.save(new MeadEvent("Test Event",
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto"));
    }

    private User createAndSaveUser(String email) {
        return userRepository.save(new User(email, "Test User",
                UserStatus.ACTIVE, Role.USER));
    }

    @Test
    void shouldSaveAndRetrieve() {
        var event = createAndSaveEvent();
        var user = createAndSaveUser("ep-save@test.com");

        var ep = new EventParticipant(event.getId(), user.getId());
        eventParticipantRepository.save(ep);

        var found = eventParticipantRepository.findById(ep.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getEventId()).isEqualTo(event.getId());
        assertThat(found.get().getUserId()).isEqualTo(user.getId());
        assertThat(found.get().getAccessCode()).isNull();
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void shouldFindByEventId() {
        var event = createAndSaveEvent();
        var user1 = createAndSaveUser("ep-find1@test.com");
        var user2 = createAndSaveUser("ep-find2@test.com");

        eventParticipantRepository.save(new EventParticipant(event.getId(), user1.getId()));
        eventParticipantRepository.save(new EventParticipant(event.getId(), user2.getId()));

        var results = eventParticipantRepository.findByEventId(event.getId());

        assertThat(results).hasSize(2);
    }

    @Test
    void shouldFindByAccessCode() {
        var event = createAndSaveEvent();
        var user = createAndSaveUser("ep-code@test.com");

        var ep = new EventParticipant(event.getId(), user.getId());
        ep.assignAccessCode("TESTCODE");
        eventParticipantRepository.save(ep);

        var found = eventParticipantRepository.findByAccessCode("TESTCODE");

        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(user.getId());
    }

    @Test
    void shouldFindByEventIdAndUserId() {
        var event = createAndSaveEvent();
        var user = createAndSaveUser("ep-eventuser@test.com");

        eventParticipantRepository.save(new EventParticipant(event.getId(), user.getId()));

        var found = eventParticipantRepository.findByEventIdAndUserId(
                event.getId(), user.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getEventId()).isEqualTo(event.getId());
        assertThat(found.get().getUserId()).isEqualTo(user.getId());
    }
}
