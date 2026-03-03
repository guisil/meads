package app.meads.entry;

import app.meads.TestcontainersConfiguration;
import app.meads.entry.internal.JumpsellerOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class JumpsellerOrderRepositoryTest {

    @Autowired
    JumpsellerOrderRepository jumpsellerOrderRepository;

    @Test
    void shouldSaveAndFindByJumpsellerOrderId() {
        var order = new JumpsellerOrder("ORDER-001", "entrant@test.com",
                "Test Entrant", "{\"raw\": \"payload\"}");
        jumpsellerOrderRepository.save(order);

        var found = jumpsellerOrderRepository.findByJumpsellerOrderId("ORDER-001");

        assertThat(found).isPresent();
        assertThat(found.get().getCustomerEmail()).isEqualTo("entrant@test.com");
        assertThat(found.get().getCustomerName()).isEqualTo("Test Entrant");
        assertThat(found.get().getRawPayload()).isEqualTo("{\"raw\": \"payload\"}");
        assertThat(found.get().getStatus()).isEqualTo(OrderStatus.UNPROCESSED);
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void shouldCheckExistsByJumpsellerOrderId() {
        jumpsellerOrderRepository.save(new JumpsellerOrder("ORDER-001",
                "entrant@test.com", "Test Entrant", "{}"));

        assertThat(jumpsellerOrderRepository.existsByJumpsellerOrderId("ORDER-001")).isTrue();
        assertThat(jumpsellerOrderRepository.existsByJumpsellerOrderId("ORDER-999")).isFalse();
    }

    @Test
    void shouldFindByStatus() {
        var order1 = new JumpsellerOrder("ORDER-001", "a@test.com", "A", "{}");
        order1.markProcessed();
        jumpsellerOrderRepository.save(order1);

        var order2 = new JumpsellerOrder("ORDER-002", "b@test.com", "B", "{}");
        order2.markNeedsReview();
        jumpsellerOrderRepository.save(order2);

        var processed = jumpsellerOrderRepository.findByStatus(OrderStatus.PROCESSED);
        assertThat(processed).hasSize(1);
        assertThat(processed.getFirst().getJumpsellerOrderId()).isEqualTo("ORDER-001");

        var needsReview = jumpsellerOrderRepository.findByStatus(OrderStatus.NEEDS_REVIEW);
        assertThat(needsReview).hasSize(1);
        assertThat(needsReview.getFirst().getJumpsellerOrderId()).isEqualTo("ORDER-002");
    }
}
