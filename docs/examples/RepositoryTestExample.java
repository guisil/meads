// == RepositoryTestExample.java ==
// JPA repository test with real PostgreSQL via Testcontainers.
// USE WHEN: Testing persistence — saves, queries, schema correctness.

package com.example.app.order;

import com.example.app.TestcontainersConfiguration;
import com.example.app.order.internal.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {

    @Autowired OrderRepository orderRepository;

    @Test
    void shouldSaveAndRetrieveOrder() {
        var order = new Order(UUID.randomUUID(), OrderStatus.CREATED);

        orderRepository.save(order);
        var found = orderRepository.findById(order.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void shouldFindOrdersByStatus() {
        orderRepository.save(new Order(UUID.randomUUID(), OrderStatus.CREATED));
        orderRepository.save(new Order(UUID.randomUUID(), OrderStatus.CONFIRMED));
        orderRepository.save(new Order(UUID.randomUUID(), OrderStatus.CREATED));

        var result = orderRepository.findByStatus(OrderStatus.CREATED);

        assertThat(result).hasSize(2);
    }
}

// TDD FLOW:
// 1. Write test → won't compile (Order, OrderRepository missing) → RED
// 2. Create entity + repo → compiles but fails (no table) → still RED
// 3. Create Flyway migration → GREEN
// 4. Refactor → all tests pass
//
// @DataJpaTest + Replace.NONE = uses Testcontainers PostgreSQL, not H2.
// Each test runs in a rolled-back transaction.
