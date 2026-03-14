// == ModuleIntegrationTestExample.java ==
// Tests a single module with a real Spring context, isolated from other modules.
// USE WHEN: Verifying a module's components work together, checking events.
// REFERENCE: EntryModuleTest.java, CompetitionModuleTest.java

package app.meads.order;

import app.meads.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.PublishedEvents;

import static org.assertj.core.api.Assertions.assertThat;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
@Import(TestcontainersConfiguration.class)
class OrderModuleIntegrationTest {

    @Autowired OrderService orderService;

    @Test
    void shouldBootstrapOrderModule() {
        assertThat(orderService).isNotNull();
    }

    @Test
    void shouldPublishEventWhenOrderCreated(PublishedEvents events) {
        var order = orderService.createOrder("Test order");

        var orderEvents = events.ofType(OrderCreatedEvent.class);
        assertThat(orderEvents).hasSize(1);
        assertThat(orderEvents)
                .element(0)
                .satisfies(e -> {
                    assertThat(e.orderId()).isEqualTo(order.getId());
                    assertThat(e.description()).isEqualTo("Test order");
                });
    }
}

// @ApplicationModuleTest(mode = DIRECT_DEPENDENCIES) bootstraps this module
// + its declared allowedDependencies — nothing else.
// PublishedEvents captures events published during the test — no mock needed.
// Use .satisfies() for multi-field assertions on events (record accessors).
