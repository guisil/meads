// == UnitTestExample.java ==
// Pure unit tests. No Spring context. Fast.
// USE WHEN: Testing domain logic, services in isolation, value objects.
// REFERENCE: EntryServiceTest.java, WebhookServiceTest.java, EntryTest.java

package app.meads.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @InjectMocks OrderService orderService;
    @Mock OrderRepository orderRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    @Test
    void shouldCreateOrderSuccessfully() {
        // Arrange
        given(orderRepository.save(any(Order.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // Act
        var result = orderService.createOrder("Test order");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CREATED);
        then(orderRepository).should().save(any(Order.class));
    }

    @Test
    void shouldPublishEventWhenOrderCreated() {
        given(orderRepository.save(any(Order.class)))
                .willAnswer(inv -> inv.getArgument(0));

        orderService.createOrder("Test order");

        // Use ArgumentCaptor for detailed event assertions
        var eventCaptor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        then(eventPublisher).should().publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().description()).isEqualTo("Test order");
    }

    @Test
    void shouldRejectOrderWhenDescriptionBlank() {
        assertThatThrownBy(() -> orderService.createOrder(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description");
    }

    @Test
    void shouldNotPublishEventWhenCreationFails() {
        assertThatThrownBy(() -> orderService.createOrder(""))
                .isInstanceOf(IllegalArgumentException.class);

        then(eventPublisher).should(never()).publishEvent(any());
    }
}

// KEY:
// - Use BDDMockito: given(...).willReturn(...), then(...).should()
// - Use AssertJ: assertThat(...), assertThatThrownBy(...)
// - Use ArgumentCaptor for detailed assertions on captured arguments
// - .willAnswer(inv -> inv.getArgument(0)) — returns the saved object as-is
// - then(...).should(never()) — verify method was NOT called
// - Classes referenced DON'T EXIST yet — they're created AFTER the test (TDD).
// - Test naming: should{Behavior}When{Condition}
