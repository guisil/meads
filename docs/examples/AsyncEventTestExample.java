// == AsyncEventTestExample.java ==
// Testing inter-module event publication and handling.
// USE WHEN: Verifying events are published, or that handlers react correctly.
// REFERENCE: EntryModuleTest.java, RegistrationClosedListenerTest.java

package app.meads.order;

import app.meads.TestcontainersConfiguration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.modulith.test.PublishedEvents;
import org.springframework.modulith.test.Scenario;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// --- Pattern 1: Verify event PUBLICATION (synchronous) ---

@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES)
@Import(TestcontainersConfiguration.class)
class OrderEventPublicationTest {

    @Autowired OrderService orderService;

    @Test
    void shouldPublishOrderCreatedEvent(PublishedEvents events) {
        var order = orderService.createOrder("Test order");

        assertThat(events.ofType(OrderCreatedEvent.class)
                .matching(e -> e.orderId().equals(order.getId())))
                .hasSize(1);
    }
}

// --- Pattern 2: Cross-module workflow with Scenario ---

@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES)
@Import(TestcontainersConfiguration.class)
class OrderInventoryScenarioTest {

    @Test
    void shouldReserveStockWhenOrderCreated(Scenario scenario) {
        var orderId = UUID.randomUUID();

        scenario.publish(new OrderCreatedEvent(orderId, "Test order"))
                .andWaitForEventOfType(StockReservedEvent.class)
                .matching(e -> e.orderId().equals(orderId))
                .toArriveAndVerify(e -> assertThat(e.success()).isTrue());
    }
}

// --- Pattern 3: Async handler with Awaitility ---

@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES)
@Import(TestcontainersConfiguration.class)
class AsyncHandlerTest {

    @Autowired OrderService orderService;
    @Autowired SomeQueryService queryService;

    @Test
    void shouldUpdateReadModelAfterOrderCreated() {
        var order = orderService.createOrder("Test order");

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    var summary = queryService.findOrderSummary(order.getId());
                    assertThat(summary).isPresent();
                });
    }
}

// PREFERENCE: PublishedEvents > Scenario > Awaitility (simplest first).
// Always use BootstrapMode.DIRECT_DEPENDENCIES to respect module isolation.
