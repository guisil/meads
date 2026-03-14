package app.meads.entry;

import app.meads.TestcontainersConfiguration;
import app.meads.entry.internal.JumpsellerOrderLineItemRepository;
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
class JumpsellerOrderLineItemRepositoryTest {

    @Autowired
    JumpsellerOrderLineItemRepository lineItemRepository;

    @Autowired
    JumpsellerOrderRepository orderRepository;

    private JumpsellerOrder createAndSaveOrder() {
        return orderRepository.save(new JumpsellerOrder("ORDER-001",
                "entrant@test.com", "Test Entrant", "{}"));
    }

    @Test
    void shouldSaveAndFindByOrderId() {
        var order = createAndSaveOrder();

        var item1 = new JumpsellerOrderLineItem(order.getId(), "PROD-001",
                "SKU-001", "Entry Pack", 1);
        var item2 = new JumpsellerOrderLineItem(order.getId(), "PROD-002",
                null, "Entry Pack x3", 3);
        lineItemRepository.save(item1);
        lineItemRepository.save(item2);

        var found = lineItemRepository.findByOrderId(order.getId());

        assertThat(found).hasSize(2);
        assertThat(found).extracting(JumpsellerOrderLineItem::getJumpsellerProductId)
                .containsExactlyInAnyOrder("PROD-001", "PROD-002");
        assertThat(found.getFirst().getCreatedAt()).isNotNull();
        assertThat(found.getFirst().getStatus()).isEqualTo(LineItemStatus.UNPROCESSED);
        assertThat(found.getFirst().getCreditsAwarded()).isZero();
    }

    @Test
    void shouldPersistProcessedState() {
        var order = createAndSaveOrder();
        var item = new JumpsellerOrderLineItem(order.getId(), "PROD-001",
                "SKU-001", "Entry Pack", 2);
        var divisionId = java.util.UUID.randomUUID();
        item.markProcessed(divisionId, 2);
        lineItemRepository.save(item);

        var found = lineItemRepository.findById(item.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(LineItemStatus.PROCESSED);
        assertThat(found.get().getDivisionId()).isEqualTo(divisionId);
        assertThat(found.get().getCreditsAwarded()).isEqualTo(2);
    }

    @Test
    void shouldReturnEmptyForNonExistentOrder() {
        var found = lineItemRepository.findByOrderId(java.util.UUID.randomUUID());
        assertThat(found).isEmpty();
    }
}
