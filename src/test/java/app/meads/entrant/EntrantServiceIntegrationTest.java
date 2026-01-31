package app.meads.entrant;

import app.meads.TestcontainersConfiguration;
import app.meads.entrant.api.AddEntryCreditCommand;
import app.meads.entrant.api.Entrant;
import app.meads.entrant.api.EntryCreditAddedEvent;
import app.meads.entrant.api.EntrantService;
import app.meads.event.api.Competition;
import app.meads.event.api.CompetitionType;
import app.meads.event.api.MeadEvent;
import app.meads.event.api.MeadEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@RecordApplicationEvents
class EntrantServiceIntegrationTest {

    @Autowired
    private EntrantService entrantService;

    @Autowired
    private MeadEventService meadEventService;

    @Autowired
    private ApplicationEvents applicationEvents;

    private UUID competitionId;

    @BeforeEach
    void setUp() {
        var event = meadEventService.createEvent(new MeadEvent(
            null, "test-event-" + UUID.randomUUID(), "Test Event", null,
            LocalDate.of(2024, 1, 1), null, true
        ));
        var competition = meadEventService.createCompetition(new Competition(
            null, event.id(), CompetitionType.HOME, "Test Competition", null, 3, true, null
        ));
        competitionId = competition.id();
    }

    @Test
    void shouldCreateAndRetrieveEntrant() {
        var entrant = new Entrant(
            null,
            "meadmaker@example.com",
            "John Brewer",
            "+1-555-1234",
            "123 Honey Lane",
            "Apt 4B",
            "Beeville",
            "CA",
            "90210",
            "USA"
        );

        var created = entrantService.createEntrant(entrant);

        assertThat(created.id()).isNotNull();
        assertThat(created.email()).isEqualTo("meadmaker@example.com");
        assertThat(created.name()).isEqualTo("John Brewer");

        var found = entrantService.findEntrantById(created.id());
        assertThat(found).isPresent();
        assertThat(found.get().city()).isEqualTo("Beeville");
    }

    @Test
    void shouldFindEntrantByEmail() {
        var entrant = entrantService.createEntrant(new Entrant(
            null, "unique@example.com", "Unique Brewer", null, null, null, null, null, null, null
        ));

        var found = entrantService.findEntrantByEmail("unique@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(entrant.id());
    }

    @Test
    void shouldUpdateEntrant() {
        var entrant = entrantService.createEntrant(new Entrant(
            null, "update@example.com", "Original Name", null, null, null, null, null, null, null
        ));

        var updated = entrantService.updateEntrant(new Entrant(
            entrant.id(),
            "update@example.com",
            "Updated Name",
            "+1-555-9999",
            "456 New Street",
            null,
            "New City",
            "NY",
            "10001",
            "USA"
        ));

        assertThat(updated.name()).isEqualTo("Updated Name");
        assertThat(updated.phone()).isEqualTo("+1-555-9999");
        assertThat(updated.city()).isEqualTo("New City");
    }

    @Test
    void shouldAddCreditAndPublishEvent() {
        var entrant = entrantService.createEntrant(new Entrant(
            null, "credit@example.com", "Credit Tester", null, null, null, null, null, null, null
        ));

        var command = new AddEntryCreditCommand(
            entrant.id(),
            competitionId,
            2,
            "ORDER-123",
            "jumpseller",
            Instant.now()
        );

        var credit = entrantService.addCredit(command);

        assertThat(credit.id()).isNotNull();
        assertThat(credit.entrantId()).isEqualTo(entrant.id());
        assertThat(credit.competitionId()).isEqualTo(competitionId);
        assertThat(credit.quantity()).isEqualTo(2);
        assertThat(credit.usedCount()).isEqualTo(0);
        assertThat(credit.availableCredits()).isEqualTo(2);
        assertThat(credit.status()).isEqualTo("ACTIVE");

        // Verify event was published
        var events = applicationEvents.stream(EntryCreditAddedEvent.class).toList();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().entrantId()).isEqualTo(entrant.id());
        assertThat(events.getFirst().quantity()).isEqualTo(2);
    }

    @Test
    void shouldFindCreditsByEntrantId() {
        var entrant = entrantService.createEntrant(new Entrant(
            null, "multicredit@example.com", "Multi Credit", null, null, null, null, null, null, null
        ));

        entrantService.addCredit(new AddEntryCreditCommand(
            entrant.id(), competitionId, 1, "ORDER-A", "jumpseller", Instant.now()
        ));
        entrantService.addCredit(new AddEntryCreditCommand(
            entrant.id(), competitionId, 3, "ORDER-B", "manual", Instant.now()
        ));

        var credits = entrantService.findCreditsByEntrantId(entrant.id());

        assertThat(credits).hasSize(2);
        assertThat(credits).extracting("quantity").containsExactlyInAnyOrder(1, 3);
    }

    @Test
    void shouldFindCreditByExternalOrder() {
        var entrant = entrantService.createEntrant(new Entrant(
            null, "external@example.com", "External Order", null, null, null, null, null, null, null
        ));

        entrantService.addCredit(new AddEntryCreditCommand(
            entrant.id(), competitionId, 2, "EXT-ORDER-999", "jumpseller", Instant.now()
        ));

        var found = entrantService.findCreditByExternalOrder("EXT-ORDER-999", "jumpseller");
        assertThat(found).isPresent();
        assertThat(found.get().quantity()).isEqualTo(2);

        var notFound = entrantService.findCreditByExternalOrder("EXT-ORDER-999", "other-source");
        assertThat(notFound).isEmpty();
    }

    @Test
    void shouldTrackCompetitionIdsWithCredits() {
        var entrant = entrantService.createEntrant(new Entrant(
            null, "tracking@example.com", "Tracker", null, null, null, null, null, null, null
        ));

        // Initially empty
        assertThat(entrantService.getCompetitionIdsWithCredits(entrant.id())).isEmpty();

        // Add credit
        entrantService.addCredit(new AddEntryCreditCommand(
            entrant.id(), competitionId, 1, "TRACK-ORDER", "test", Instant.now()
        ));

        var competitionIds = entrantService.getCompetitionIdsWithCredits(entrant.id());
        assertThat(competitionIds).containsExactly(competitionId);
    }

    @Test
    void shouldFindAllEntrants() {
        int initialCount = entrantService.findAllEntrants().size();

        entrantService.createEntrant(new Entrant(
            null, "all1@example.com", "All 1", null, null, null, null, null, null, null
        ));
        entrantService.createEntrant(new Entrant(
            null, "all2@example.com", "All 2", null, null, null, null, null, null, null
        ));

        var allEntrants = entrantService.findAllEntrants();
        assertThat(allEntrants).hasSize(initialCount + 2);
    }
}
