package app.meads.event;

import app.meads.TestcontainersConfiguration;
import app.meads.event.api.Competition;
import app.meads.event.api.CompetitionType;
import app.meads.event.api.MeadEvent;
import app.meads.event.api.MeadEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MeadEventServiceIntegrationTest {

    @Autowired
    private MeadEventService meadEventService;

    @Test
    void shouldCreateAndRetrieveEvent() {
        var event = new MeadEvent(
            null,
            "meads-2024",
            "MEADS 2024",
            "Annual mead competition",
            LocalDate.of(2024, 6, 1),
            LocalDate.of(2024, 6, 30),
            true
        );

        var created = meadEventService.createEvent(event);

        assertThat(created.id()).isNotNull();
        assertThat(created.slug()).isEqualTo("meads-2024");
        assertThat(created.name()).isEqualTo("MEADS 2024");
        assertThat(created.active()).isTrue();

        var found = meadEventService.findEventById(created.id());
        assertThat(found).isPresent();
        assertThat(found.get().slug()).isEqualTo("meads-2024");
    }

    @Test
    void shouldFindEventBySlug() {
        var event = new MeadEvent(
            null,
            "spring-mead-fest",
            "Spring Mead Fest",
            "Spring festival",
            LocalDate.of(2024, 4, 1),
            LocalDate.of(2024, 4, 15),
            true
        );

        meadEventService.createEvent(event);

        var found = meadEventService.findEventBySlug("spring-mead-fest");
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Spring Mead Fest");
    }

    @Test
    void shouldUpdateEvent() {
        var event = meadEventService.createEvent(new MeadEvent(
            null,
            "update-test",
            "Original Name",
            "Original description",
            LocalDate.of(2024, 1, 1),
            null,
            true
        ));

        var updated = meadEventService.updateEvent(new MeadEvent(
            event.id(),
            "update-test",
            "Updated Name",
            "Updated description",
            LocalDate.of(2024, 2, 1),
            LocalDate.of(2024, 3, 1),
            false
        ));

        assertThat(updated.name()).isEqualTo("Updated Name");
        assertThat(updated.description()).isEqualTo("Updated description");
        assertThat(updated.active()).isFalse();
    }

    @Test
    void shouldCreateAndRetrieveCompetition() {
        var event = meadEventService.createEvent(new MeadEvent(
            null,
            "comp-test-event",
            "Competition Test Event",
            null,
            LocalDate.of(2024, 5, 1),
            null,
            true
        ));

        var competition = new Competition(
            null,
            event.id(),
            CompetitionType.HOME,
            "Home Mead Competition",
            "For amateur mead makers",
            3,
            true,
            Instant.now().plus(30, ChronoUnit.DAYS)
        );

        var created = meadEventService.createCompetition(competition);

        assertThat(created.id()).isNotNull();
        assertThat(created.meadEventId()).isEqualTo(event.id());
        assertThat(created.type()).isEqualTo(CompetitionType.HOME);
        assertThat(created.name()).isEqualTo("Home Mead Competition");
        assertThat(created.maxEntriesPerEntrant()).isEqualTo(3);
    }

    @Test
    void shouldFindCompetitionsByEventId() {
        var event = meadEventService.createEvent(new MeadEvent(
            null,
            "multi-comp-event",
            "Multi Competition Event",
            null,
            LocalDate.of(2024, 5, 1),
            null,
            true
        ));

        meadEventService.createCompetition(new Competition(
            null, event.id(), CompetitionType.HOME, "Home", null, 3, true, null
        ));
        meadEventService.createCompetition(new Competition(
            null, event.id(), CompetitionType.COMMERCIAL, "Commercial", null, 5, false, null
        ));

        var competitions = meadEventService.findCompetitionsByEventId(event.id());

        assertThat(competitions).hasSize(2);
        assertThat(competitions).extracting(Competition::type)
            .containsExactlyInAnyOrder(CompetitionType.HOME, CompetitionType.COMMERCIAL);
    }

    @Test
    void shouldUpdateCompetition() {
        var event = meadEventService.createEvent(new MeadEvent(
            null,
            "update-comp-event",
            "Update Competition Event",
            null,
            LocalDate.of(2024, 5, 1),
            null,
            true
        ));

        var competition = meadEventService.createCompetition(new Competition(
            null, event.id(), CompetitionType.HOME, "Original Name", null, 3, false, null
        ));

        var updated = meadEventService.updateCompetition(new Competition(
            competition.id(),
            event.id(),
            CompetitionType.HOME,
            "Updated Name",
            "New description",
            5,
            true,
            Instant.now().plus(60, ChronoUnit.DAYS)
        ));

        assertThat(updated.name()).isEqualTo("Updated Name");
        assertThat(updated.maxEntriesPerEntrant()).isEqualTo(5);
        assertThat(updated.registrationOpen()).isTrue();
    }

    @Test
    void shouldFindAllEvents() {
        int initialCount = meadEventService.findAllEvents().size();

        meadEventService.createEvent(new MeadEvent(
            null, "event-1", "Event 1", null, LocalDate.of(2024, 1, 1), null, true
        ));
        meadEventService.createEvent(new MeadEvent(
            null, "event-2", "Event 2", null, LocalDate.of(2024, 2, 1), null, true
        ));

        var allEvents = meadEventService.findAllEvents();
        assertThat(allEvents).hasSize(initialCount + 2);
    }
}
