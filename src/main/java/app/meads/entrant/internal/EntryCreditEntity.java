package app.meads.entrant.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "entry_credits")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntryCreditEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entrant_id", nullable = false)
    private EntrantEntity entrant;

    @Column(name = "competition_id", nullable = false)
    private UUID competitionId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "external_order_id", nullable = false)
    private String externalOrderId;

    @Column(name = "external_source", nullable = false, length = 100)
    private String externalSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EntryCreditStatus status;

    @Column(name = "purchased_at")
    private Instant purchasedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = EntryCreditStatus.ACTIVE;
        }
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
