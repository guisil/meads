package app.meads.entry;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jumpseller_orders")
@Getter
public class JumpsellerOrder {

    @Id
    private UUID id;

    @Column(name = "jumpseller_order_id", nullable = false, unique = true)
    private String jumpsellerOrderId;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_country", length = 2)
    private String customerCountry;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "TEXT")
    private String rawPayload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected JumpsellerOrder() {} // JPA

    public JumpsellerOrder(String jumpsellerOrderId, String customerEmail,
                           String customerName, String rawPayload) {
        this.id = UUID.randomUUID();
        this.jumpsellerOrderId = jumpsellerOrderId;
        this.customerEmail = customerEmail;
        this.customerName = customerName;
        this.rawPayload = rawPayload;
        this.status = OrderStatus.UNPROCESSED;
        this.createdAt = Instant.now();
    }

    public void assignCustomerCountry(String customerCountry) {
        this.customerCountry = customerCountry;
    }

    public void markProcessed() {
        this.status = OrderStatus.PROCESSED;
        this.processedAt = Instant.now();
    }

    public void markPartiallyProcessed() {
        this.status = OrderStatus.PARTIALLY_PROCESSED;
        this.processedAt = Instant.now();
    }

    public void markNeedsReview() {
        this.status = OrderStatus.NEEDS_REVIEW;
    }

    public void updateAdminDetails(OrderStatus status, String note) {
        this.status = status;
        this.adminNote = note;
        if (status == OrderStatus.PROCESSED && this.processedAt == null) {
            this.processedAt = Instant.now();
        }
    }
}
