package app.meads.order;

import app.meads.TestcontainersConfiguration;
import app.meads.entrant.api.EntrantService;
import app.meads.event.api.Competition;
import app.meads.event.api.CompetitionType;
import app.meads.event.api.MeadEvent;
import app.meads.event.api.MeadEventService;
import app.meads.order.api.OrderPendingReviewEvent;
import app.meads.order.api.PendingOrderService;
import app.meads.order.internal.OrderPayload;
import app.meads.order.internal.OrderProcessingService;
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

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@RecordApplicationEvents
class OrderProcessingServiceIntegrationTest {

    @Autowired
    private OrderProcessingService orderProcessingService;

    @Autowired
    private EntrantService entrantService;

    @Autowired
    private MeadEventService meadEventService;

    @Autowired
    private PendingOrderService pendingOrderService;

    @Autowired
    private ApplicationEvents applicationEvents;

    private UUID homeCompetitionId;
    private UUID commercialCompetitionId;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        var event = meadEventService.createEvent(new MeadEvent(
            null, "order-test-" + UUID.randomUUID(), "Order Test Event", null,
            LocalDate.of(2024, 1, 1), null, true
        ));
        eventId = event.id();

        var homeCompetition = meadEventService.createCompetition(new Competition(
            null, eventId, CompetitionType.HOME, "Home Competition", null, 3, true, null
        ));
        homeCompetitionId = homeCompetition.id();

        var commercialCompetition = meadEventService.createCompetition(new Competition(
            null, eventId, CompetitionType.COMMERCIAL, "Commercial Competition", null, 5, true, null
        ));
        commercialCompetitionId = commercialCompetition.id();
    }

    @Test
    void shouldProcessNewOrderAndCreateEntrant() {
        var payload = createPayload("NEW-ORDER-1", "newuser@example.com", homeCompetitionId, 2);

        var response = orderProcessingService.processOrder(payload);

        assertThat(response.status()).isEqualTo("PROCESSED");
        assertThat(response.creditsAdded()).isEqualTo(2);
        assertThat(response.entrantId()).isNotNull();

        // Verify entrant was created
        var entrant = entrantService.findEntrantByEmail("newuser@example.com");
        assertThat(entrant).isPresent();
        assertThat(entrant.get().name()).isEqualTo("Test User");

        // Verify credits were added
        var credits = entrantService.findCreditsByEntrantId(response.entrantId());
        assertThat(credits).hasSize(1);
        assertThat(credits.getFirst().quantity()).isEqualTo(2);
    }

    @Test
    void shouldReuseExistingEntrant() {
        // First order creates entrant
        var payload1 = createPayload("REUSE-ORDER-1", "existing@example.com", homeCompetitionId, 1);
        var response1 = orderProcessingService.processOrder(payload1);

        // Second order reuses entrant
        var payload2 = createPayload("REUSE-ORDER-2", "existing@example.com", homeCompetitionId, 2);
        var response2 = orderProcessingService.processOrder(payload2);

        assertThat(response1.entrantId()).isEqualTo(response2.entrantId());

        var credits = entrantService.findCreditsByEntrantId(response1.entrantId());
        assertThat(credits).hasSize(2);
    }

    @Test
    void shouldDetectDuplicateOrder() {
        var payload = createPayload("DUPLICATE-ORDER", "dup@example.com", homeCompetitionId, 2);

        var response1 = orderProcessingService.processOrder(payload);
        assertThat(response1.status()).isEqualTo("PROCESSED");

        var response2 = orderProcessingService.processOrder(payload);
        assertThat(response2.status()).isEqualTo("ALREADY_PROCESSED");
        assertThat(response2.creditsAdded()).isEqualTo(0);

        // Only one credit should exist
        var credits = entrantService.findCreditsByEntrantId(response1.entrantId());
        assertThat(credits).hasSize(1);
    }

    @Test
    void shouldCreatePendingOrderForCompetitionExclusivityViolation() {
        // First order for HOME competition
        var homePayload = createPayload("HOME-ORDER", "exclusive@example.com", homeCompetitionId, 1);
        var homeResponse = orderProcessingService.processOrder(homePayload);
        assertThat(homeResponse.status()).isEqualTo("PROCESSED");

        // Second order for COMMERCIAL competition - should be pending
        var commercialPayload = createPayload("COMMERCIAL-ORDER", "exclusive@example.com", commercialCompetitionId, 1);
        var commercialResponse = orderProcessingService.processOrder(commercialPayload);

        assertThat(commercialResponse.status()).isEqualTo("PENDING_REVIEW");
        assertThat(commercialResponse.creditsAdded()).isEqualTo(0);

        // Verify pending order was created
        var pendingOrders = pendingOrderService.findOrdersNeedingReview();
        assertThat(pendingOrders).anyMatch(po ->
            po.externalOrderId().equals("COMMERCIAL-ORDER") &&
            po.reason().equals("COMPETITION_EXCLUSIVITY")
        );

        // Verify event was published
        var events = applicationEvents.stream(OrderPendingReviewEvent.class).toList();
        assertThat(events).anyMatch(e -> e.reason().equals("COMPETITION_EXCLUSIVITY"));
    }

    @Test
    void shouldAllowSameCompetitionTypeMultipleOrders() {
        // Multiple orders for the same competition type are allowed
        var payload1 = createPayload("SAME-TYPE-1", "sametype@example.com", homeCompetitionId, 1);
        var payload2 = createPayload("SAME-TYPE-2", "sametype@example.com", homeCompetitionId, 2);

        var response1 = orderProcessingService.processOrder(payload1);
        var response2 = orderProcessingService.processOrder(payload2);

        assertThat(response1.status()).isEqualTo("PROCESSED");
        assertThat(response2.status()).isEqualTo("PROCESSED");

        var credits = entrantService.findCreditsByEntrantId(response1.entrantId());
        assertThat(credits).hasSize(2);
    }

    @Test
    void shouldHandleOrderWithAddressInfo() {
        var address = new OrderPayload.AddressInfo(
            "123 Main St", "Suite 100", "Portland", "OR", "97201", "USA"
        );
        var customer = new OrderPayload.CustomerInfo(
            "withaddress@example.com", "Address User", "+1-555-1234", address
        );
        var payload = new OrderPayload(
            "ADDRESS-ORDER", "jumpseller", homeCompetitionId, customer, 1, Instant.now()
        );

        var response = orderProcessingService.processOrder(payload);

        var entrant = entrantService.findEntrantById(response.entrantId());
        assertThat(entrant).isPresent();
        assertThat(entrant.get().addressLine1()).isEqualTo("123 Main St");
        assertThat(entrant.get().city()).isEqualTo("Portland");
    }

    @Test
    void shouldHandleOrderWithNullAddress() {
        var customer = new OrderPayload.CustomerInfo(
            "noaddress@example.com", "No Address User", null, null
        );
        var payload = new OrderPayload(
            "NO-ADDRESS-ORDER", "jumpseller", homeCompetitionId, customer, 1, Instant.now()
        );

        var response = orderProcessingService.processOrder(payload);

        assertThat(response.status()).isEqualTo("PROCESSED");
        var entrant = entrantService.findEntrantById(response.entrantId());
        assertThat(entrant).isPresent();
        assertThat(entrant.get().addressLine1()).isNull();
    }

    private OrderPayload createPayload(String orderId, String email, UUID competitionId, int quantity) {
        var customer = new OrderPayload.CustomerInfo(
            email, "Test User", "+1-555-0000", null
        );
        return new OrderPayload(
            orderId, "jumpseller", competitionId, customer, quantity, Instant.now()
        );
    }
}
