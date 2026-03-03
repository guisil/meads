package app.meads.entry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JumpsellerOrderTest {

    @Test
    void shouldCreateOrderWithUnprocessedStatus() {
        var order = new JumpsellerOrder("ORDER-001", "entrant@test.com",
                "Test Entrant", "{\"raw\": \"payload\"}");

        assertThat(order.getId()).isNotNull();
        assertThat(order.getJumpsellerOrderId()).isEqualTo("ORDER-001");
        assertThat(order.getCustomerEmail()).isEqualTo("entrant@test.com");
        assertThat(order.getCustomerName()).isEqualTo("Test Entrant");
        assertThat(order.getRawPayload()).isEqualTo("{\"raw\": \"payload\"}");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.UNPROCESSED);
        assertThat(order.getProcessedAt()).isNull();
        assertThat(order.getAdminNote()).isNull();
    }

    @Test
    void shouldMarkProcessed() {
        var order = new JumpsellerOrder("ORDER-001", "entrant@test.com",
                "Test Entrant", "{}");

        order.markProcessed();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSED);
        assertThat(order.getProcessedAt()).isNotNull();
    }

    @Test
    void shouldMarkPartiallyProcessed() {
        var order = new JumpsellerOrder("ORDER-001", "entrant@test.com",
                "Test Entrant", "{}");

        order.markPartiallyProcessed();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_PROCESSED);
        assertThat(order.getProcessedAt()).isNotNull();
    }

    @Test
    void shouldMarkNeedsReview() {
        var order = new JumpsellerOrder("ORDER-001", "entrant@test.com",
                "Test Entrant", "{}");

        order.markNeedsReview();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEEDS_REVIEW);
    }

    @Test
    void shouldAddAdminNote() {
        var order = new JumpsellerOrder("ORDER-001", "entrant@test.com",
                "Test Entrant", "{}");

        order.addAdminNote("Reviewed and approved");

        assertThat(order.getAdminNote()).isEqualTo("Reviewed and approved");
    }
}
