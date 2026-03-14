// == RepositoryTestExample.java ==
// JPA repository test with real PostgreSQL via Testcontainers.
// USE WHEN: Testing persistence — saves, queries, schema correctness.
// REFERENCE: EntryRepositoryTest.java, UserRepositoryTest.java

package app.meads.order;

import app.meads.TestcontainersConfiguration;
import app.meads.order.internal.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class OrderRepositoryTest {

    @Autowired OrderRepository orderRepository;

    @Test
    void shouldSaveAndRetrieveOrder() {
        var order = new Order("Test order");

        orderRepository.save(order);
        var found = orderRepository.findById(order.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void shouldFindOrdersByStatus() {
        orderRepository.save(new Order("Order 1"));
        orderRepository.save(new Order("Order 2"));

        var confirmed = new Order("Order 3");
        confirmed.confirm();
        orderRepository.save(confirmed);

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
// @SpringBootTest + @Transactional = full Spring context, real Testcontainers PostgreSQL.
// Each test runs in a rolled-back transaction.
// NOTE: NOT @DataJpaTest — this project uses @SpringBootTest for all repository tests.
