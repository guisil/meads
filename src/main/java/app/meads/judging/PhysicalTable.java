package app.meads.judging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * A physical judging station within a division — e.g., "Table 1", "Table 2". A
 * {@link JudgingRound} happens AT one physical table; over time the same
 * physical table can host several rounds (across categories or repetitions),
 * but only one round can be active (status {@code ROUND_1}) at any given moment.
 * <p>
 * Judges live on the {@link JudgingRound}, not on the physical table, so the
 * judge roster can change between rounds at the same table.
 */
@Entity
@Table(name = "physical_tables")
@Getter
public class PhysicalTable {

    @Id
    private UUID id;

    @Column(name = "division_id", nullable = false)
    private UUID divisionId;

    @Column(name = "label", nullable = false, length = 50)
    private String label;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected PhysicalTable() {}

    public PhysicalTable(UUID divisionId, String label) {
        this.id = UUID.randomUUID();
        this.divisionId = divisionId;
        this.label = label;
    }

    public void updateLabel(String label) {
        this.label = label;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
