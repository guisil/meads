// == AsyncEventTestExample.java ==
// Testing inter-module event publication and handling.
// USE WHEN: Verifying events are published, or that handlers react correctly.

package com.example.app.order;

import com.example.app.TestcontainersConfiguration;
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

@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class OrderEventPublicationTest {

    @Autowired OrderService orderService;

    @Test
    void shouldPublishOrderCreatedEvent(PublishedEvents events) {
        var order = orderService.createOrder(new CreateOrderRequest(/* ... */));

        assertThat(events.ofType(OrderCreatedEvent.class)
                .matching(e -> e.orderId().equals(order.id())))
                .hasSize(1);
    }
}

// --- Pattern 2: Cross-module workflow with Scenario ---

@ApplicationModuleTest(BootstrapMode.DIRECT_DEPENDENCIES)
@Import(TestcontainersConfiguration.class)
class OrderInventoryScenarioTest {

    @Test
    void shouldReserveStockWhenOrderCreated(Scenario scenario) {
        var orderId = UUID.randomUUID();

        scenario.publish(new OrderCreatedEvent(orderId, /* items */))
                .andWaitForEventOfType(StockReservedEvent.class)
                .matching(e -> e.orderId().equals(orderId))
                .toArriveAndVerify(e -> assertThat(e.success()).isTrue());
    }
}

// --- Pattern 3: Async handler with Awaitility ---

@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class AsyncHandlerTest {

    @Autowired OrderService orderService;
    @Autowired SomeQueryService queryService;

    @Test
    void shouldUpdateReadModelAfterOrderCreated() {
        var order = orderService.createOrder(new CreateOrderRequest(/* ... */));

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    var summary = queryService.findOrderSummary(order.id());
                    assertThat(summary).isPresent();
                });
    }
}

// PREFERENCE: PublishedEvents > Scenario > Awaitility (simplest first).
