package app.meads.entry;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "entry_credits")
@Getter
public class EntryCredit {

    @Id
    private UUID id;

    @Column(name = "division_id", nullable = false)
    private UUID divisionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private int amount;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_reference")
    private String sourceReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EntryCredit() {} // JPA

    public EntryCredit(UUID divisionId, UUID userId, int amount,
                       String sourceType, String sourceReference) {
        if (amount == 0) {
            throw new IllegalArgumentException("Credit amount must not be zero");
        }
        this.id = UUID.randomUUID();
        this.divisionId = divisionId;
        this.userId = userId;
        this.amount = amount;
        this.sourceType = sourceType;
        this.sourceReference = sourceReference;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
