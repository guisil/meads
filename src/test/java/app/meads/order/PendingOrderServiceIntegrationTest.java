package app.meads.order;

import app.meads.TestcontainersConfiguration;
import app.meads.event.api.Competition;
import app.meads.event.api.CompetitionType;
import app.meads.event.api.MeadEvent;
import app.meads.event.api.MeadEventService;
import app.meads.order.api.PendingOrderService;
import app.meads.order.internal.OrderPayload;
import app.meads.order.internal.OrderProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PendingOrderServiceIntegrationTest {

    @Autowired
    private PendingOrderService pendingOrderService;

    @Autowired
    private OrderProcessingService orderProcessingService;

    @Autowired
    private MeadEventService meadEventService;

    private UUID homeCompetitionId;
    private UUID commercialCompetitionId;

    @BeforeEach
    void setUp() {
        var event = meadEventService.createEvent(new MeadEvent(
            null, "pending-test-" + UUID.randomUUID(), "Pending Test Event", null,
            LocalDate.of(2024, 1, 1), null, true
        ));

        var homeCompetition = meadEventService.createCompetition(new Competition(
            null, event.id(), CompetitionType.HOME, "Home", null, 3, true, null
        ));
        homeCompetitionId = homeCompetition.id();

        var commercialCompetition = meadEventService.createCompetition(new Competition(
            null, event.id(), CompetitionType.COMMERCIAL, "Commercial", null, 5, true, null
        ));
        commercialCompetitionId = commercialCompetition.id();
    }

    @Test
    void shouldFindOrdersNeedingReview() {
        // Create a pending order by triggering exclusivity violation
        createPendingOrder("pending1@example.com", "PENDING-1");

        var pendingOrders = pendingOrderService.findOrdersNeedingReview();

        assertThat(pendingOrders).anyMatch(po -> po.externalOrderId().equals("PENDING-1"));
        assertThat(pendingOrders).allMatch(po -> po.status().equals("NEEDS_REVIEW"));
    }

    @Test
    void shouldResolveOrder() {
        createPendingOrder("resolve@example.com", "RESOLVE-ORDER");

        var pendingOrders = pendingOrderService.findOrdersNeedingReview();
        var orderId = pendingOrders.stream()
            .filter(po -> po.externalOrderId().equals("RESOLVE-ORDER"))
            .findFirst()
            .orElseThrow()
            .id();

        pendingOrderService.resolveOrder(orderId, "admin", "Approved manually");

        var resolved = pendingOrderService.findById(orderId);
        assertThat(resolved).isPresent();
        assertThat(resolved.get().status()).isEqualTo("RESOLVED");
        assertThat(resolved.get().resolvedBy()).isEqualTo("admin");
        assertThat(resolved.get().resolutionNotes()).isEqualTo("Approved manually");
        assertThat(resolved.get().resolvedAt()).isNotNull();
    }

    @Test
    void shouldCancelOrder() {
        createPendingOrder("cancel@example.com", "CANCEL-ORDER");

        var pendingOrders = pendingOrderService.findOrdersNeedingReview();
        var orderId = pendingOrders.stream()
            .filter(po -> po.externalOrderId().equals("CANCEL-ORDER"))
            .findFirst()
            .orElseThrow()
            .id();

        pendingOrderService.cancelOrder(orderId, "admin", "Customer refunded");

        var cancelled = pendingOrderService.findById(orderId);
        assertThat(cancelled).isPresent();
        assertThat(cancelled.get().status()).isEqualTo("CANCELLED");
    }

    @Test
    void shouldFindAllPendingOrders() {
        createPendingOrder("all1@example.com", "ALL-1");
        createPendingOrder("all2@example.com", "ALL-2");

        // Resolve one
        var pendingOrders = pendingOrderService.findOrdersNeedingReview();
        var orderId = pendingOrders.stream()
            .filter(po -> po.externalOrderId().equals("ALL-1"))
            .findFirst()
            .orElseThrow()
            .id();
        pendingOrderService.resolveOrder(orderId, "admin", "resolved");

        // findAll should include both
        var allOrders = pendingOrderService.findAllPendingOrders();
        assertThat(allOrders).anyMatch(po -> po.externalOrderId().equals("ALL-1") && po.status().equals("RESOLVED"));
        assertThat(allOrders).anyMatch(po -> po.externalOrderId().equals("ALL-2") && po.status().equals("NEEDS_REVIEW"));

        // findOrdersNeedingReview should only include unresolved
        var needingReview = pendingOrderService.findOrdersNeedingReview();
        assertThat(needingReview).noneMatch(po -> po.externalOrderId().equals("ALL-1"));
        assertThat(needingReview).anyMatch(po -> po.externalOrderId().equals("ALL-2"));
    }

    @Test
    void shouldThrowWhenResolvingNonExistentOrder() {
        assertThatThrownBy(() ->
            pendingOrderService.resolveOrder(UUID.randomUUID(), "admin", "test")
        ).isInstanceOf(IllegalArgumentException.class);
    }

    private void createPendingOrder(String email, String orderId) {
        // First create a home order
        var homeCustomer = new OrderPayload.CustomerInfo(email, "Test", null, null);
        var homePayload = new OrderPayload("HOME-" + orderId, "jumpseller", homeCompetitionId, homeCustomer, 1, Instant.now());
        orderProcessingService.processOrder(homePayload);

        // Then try commercial - should create pending
        var commercialPayload = new OrderPayload(orderId, "jumpseller", commercialCompetitionId, homeCustomer, 1, Instant.now());
        orderProcessingService.processOrder(commercialPayload);
    }
}
