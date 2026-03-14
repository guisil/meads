package app.meads.entry;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_mappings")
@Getter
public class ProductMapping {

    @Id
    private UUID id;

    @Column(name = "division_id", nullable = false)
    private UUID divisionId;

    @Column(name = "jumpseller_product_id", nullable = false)
    private String jumpsellerProductId;

    @Column(name = "jumpseller_sku")
    private String jumpsellerSku;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "credits_per_unit", nullable = false)
    private int creditsPerUnit;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProductMapping() {} // JPA

    public ProductMapping(UUID divisionId, String jumpsellerProductId, String jumpsellerSku,
                          String productName, int creditsPerUnit) {
        this.id = UUID.randomUUID();
        this.divisionId = divisionId;
        this.jumpsellerProductId = jumpsellerProductId;
        this.jumpsellerSku = jumpsellerSku;
        this.productName = productName;
        this.creditsPerUnit = creditsPerUnit;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public void updateDetails(String productName, int creditsPerUnit) {
        this.productName = productName;
        this.creditsPerUnit = creditsPerUnit;
    }
}
