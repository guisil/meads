package app.meads.judging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "judgings")
@Getter
public class Judging {

    @Id
    private UUID id;

    @Column(name = "division_id", nullable = false, unique = true)
    private UUID divisionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false, length = 20)
    private JudgingPhase phase;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Judging() {
    }

    public Judging(UUID divisionId) {
        this.id = UUID.randomUUID();
        this.divisionId = divisionId;
        this.phase = JudgingPhase.NOT_STARTED;
    }

    public void markActive() {
        if (phase != JudgingPhase.NOT_STARTED) {
            throw new IllegalStateException("Judging can only become ACTIVE from NOT_STARTED, current phase: " + phase);
        }
        this.phase = JudgingPhase.ACTIVE;
    }

    public void startBos() {
        if (phase != JudgingPhase.ACTIVE) {
            throw new IllegalStateException("Judging can only start BOS from ACTIVE, current phase: " + phase);
        }
        this.phase = JudgingPhase.BOS;
    }

    public void completeBos() {
        if (phase != JudgingPhase.BOS) {
            throw new IllegalStateException("Judging can only complete BOS from BOS phase, current phase: " + phase);
        }
        this.phase = JudgingPhase.COMPLETE;
    }

    public void reopenBos() {
        if (phase != JudgingPhase.COMPLETE) {
            throw new IllegalStateException("Judging can only reopen BOS from COMPLETE, current phase: " + phase);
        }
        this.phase = JudgingPhase.BOS;
    }

    public void resetBos() {
        if (phase != JudgingPhase.BOS) {
            throw new IllegalStateException("Judging can only reset BOS from BOS phase, current phase: " + phase);
        }
        this.phase = JudgingPhase.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
