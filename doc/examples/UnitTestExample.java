// == UnitTestExample.java ==
// Pure unit tests. No Spring context. Fast.
// USE WHEN: Testing domain logic, services in isolation, value objects.

package com.example.app.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @InjectMocks OrderService orderService;
    @Mock OrderRepository orderRepository;

    @Test
    void shouldCreateOrderSuccessfully() {
        // Arrange
        var request = new CreateOrderRequest(/* ... */);
        given(orderRepository.save(any(Order.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // Act
        var result = orderService.createOrder(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(OrderStatus.CREATED);
        then(orderRepository).should().save(any(Order.class));
    }

    @Test
    void shouldRejectOrderWhenItemsEmpty() {
        var request = new CreateOrderRequest(/* empty */);

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one item");
    }
}

// KEY: Use BDDMockito (given/then), AssertJ (assertThat).
// Classes referenced DON'T EXIST yet — they're created AFTER the test (TDD).
