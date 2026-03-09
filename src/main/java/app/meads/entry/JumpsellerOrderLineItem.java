package app.meads.entry;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jumpseller_order_line_items")
@Getter
public class JumpsellerOrderLineItem {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "jumpseller_product_id", nullable = false)
    private String jumpsellerProductId;

    @Column(name = "jumpseller_sku")
    private String jumpsellerSku;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LineItemStatus status;

    @Column(name = "division_id")
    private UUID divisionId;

    @Column(name = "credits_awarded", nullable = false)
    private int creditsAwarded;

    @Column(name = "review_reason", columnDefinition = "TEXT")
    private String reviewReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected JumpsellerOrderLineItem() {} // JPA

    public JumpsellerOrderLineItem(UUID orderId, String jumpsellerProductId,
                                    String jumpsellerSku, String productName,
                                    int quantity) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.jumpsellerProductId = jumpsellerProductId;
        this.jumpsellerSku = jumpsellerSku;
        this.productName = productName;
        this.quantity = quantity;
        this.status = LineItemStatus.UNPROCESSED;
        this.creditsAwarded = 0;
        this.createdAt = Instant.now();
    }

    public void markProcessed(UUID divisionId, int creditsAwarded) {
        this.status = LineItemStatus.PROCESSED;
        this.divisionId = divisionId;
        this.creditsAwarded = creditsAwarded;
    }

    public void markNeedsReview(String reason) {
        this.status = LineItemStatus.NEEDS_REVIEW;
        this.reviewReason = reason;
    }

    public void markIgnored() {
        this.status = LineItemStatus.IGNORED;
    }
}
