package app.meads.entry;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JumpsellerOrderLineItemTest {

    @Test
    void shouldCreateLineItemWithUnprocessedStatus() {
        var orderId = UUID.randomUUID();
        var item = new JumpsellerOrderLineItem(orderId, "PROD-001", "SKU-001",
                "Entry Pack", 2);

        assertThat(item.getId()).isNotNull();
        assertThat(item.getOrderId()).isEqualTo(orderId);
        assertThat(item.getJumpsellerProductId()).isEqualTo("PROD-001");
        assertThat(item.getJumpsellerSku()).isEqualTo("SKU-001");
        assertThat(item.getProductName()).isEqualTo("Entry Pack");
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getStatus()).isEqualTo(LineItemStatus.UNPROCESSED);
        assertThat(item.getCreditsAwarded()).isZero();
        assertThat(item.getDivisionId()).isNull();
        assertThat(item.getReviewReason()).isNull();
    }

    @Test
    void shouldMarkProcessed() {
        var item = new JumpsellerOrderLineItem(UUID.randomUUID(), "PROD-001",
                "SKU-001", "Entry Pack", 2);
        var divisionId = UUID.randomUUID();

        item.markProcessed(divisionId, 2);

        assertThat(item.getStatus()).isEqualTo(LineItemStatus.PROCESSED);
        assertThat(item.getDivisionId()).isEqualTo(divisionId);
        assertThat(item.getCreditsAwarded()).isEqualTo(2);
    }

    @Test
    void shouldMarkNeedsReview() {
        var item = new JumpsellerOrderLineItem(UUID.randomUUID(), "PROD-001",
                "SKU-001", "Entry Pack", 1);

        item.markNeedsReview("Mutual exclusivity conflict");

        assertThat(item.getStatus()).isEqualTo(LineItemStatus.NEEDS_REVIEW);
        assertThat(item.getReviewReason()).isEqualTo("Mutual exclusivity conflict");
    }

    @Test
    void shouldMarkIgnored() {
        var item = new JumpsellerOrderLineItem(UUID.randomUUID(), "TSHIRT-001",
                null, "Conference T-Shirt", 1);

        item.markIgnored();

        assertThat(item.getStatus()).isEqualTo(LineItemStatus.IGNORED);
    }
}
