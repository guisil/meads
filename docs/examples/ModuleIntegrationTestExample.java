// == ModuleIntegrationTestExample.java ==
// Tests a single module with a real Spring context, isolated from other modules.
// USE WHEN: Verifying a module's components work together, checking events.

package com.example.app.order;

import com.example.app.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.PublishedEvents;

import static org.assertj.core.api.Assertions.assertThat;

@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class OrderModuleIntegrationTest {

    @Autowired OrderService orderService;

    @Test
    void shouldBootstrapOrderModule() {
        assertThat(orderService).isNotNull();
    }

    @Test
    void shouldPublishEventWhenOrderCreated(PublishedEvents events) {
        var request = new CreateOrderRequest(/* ... */);

        orderService.createOrder(request);

        var orderEvents = events.ofType(OrderCreatedEvent.class);
        assertThat(orderEvents).hasSize(1);
        assertThat(orderEvents)
                .element(0)
                .extracting(OrderCreatedEvent::orderId)
                .isNotNull();
    }
}

// @ApplicationModuleTest bootstraps ONLY this module + allowed dependencies.
// PublishedEvents captures events published during the test — no mock needed.
