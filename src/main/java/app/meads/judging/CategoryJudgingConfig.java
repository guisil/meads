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
import java.util.EnumSet;
import java.util.UUID;

@Entity
@Table(name = "category_judging_configs")
@Getter
public class CategoryJudgingConfig {

    @Id
    private UUID id;

    @Column(name = "division_category_id", nullable = false, unique = true)
    private UUID divisionCategoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "medal_round_mode", nullable = false, length = 20)
    private MedalRoundMode medalRoundMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "medal_round_status", nullable = false, length = 20)
    private MedalRoundStatus medalRoundStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected CategoryJudgingConfig() {
    }

    public CategoryJudgingConfig(UUID divisionCategoryId) {
        this(divisionCategoryId, MedalRoundMode.COMPARATIVE);
    }

    public CategoryJudgingConfig(UUID divisionCategoryId, MedalRoundMode mode) {
        this.id = UUID.randomUUID();
        this.divisionCategoryId = divisionCategoryId;
        this.medalRoundMode = mode;
        this.medalRoundStatus = MedalRoundStatus.PENDING;
    }

    public void updateMode(MedalRoundMode mode) {
        if (!EnumSet.of(MedalRoundStatus.PENDING, MedalRoundStatus.READY).contains(medalRoundStatus)) {
            throw new IllegalStateException("Mode can only change while PENDING or READY, current: " + medalRoundStatus);
        }
        this.medalRoundMode = mode;
    }

    public void markReady() {
        if (medalRoundStatus != MedalRoundStatus.PENDING) {
            throw new IllegalStateException("Can only mark READY from PENDING, current: " + medalRoundStatus);
        }
        this.medalRoundStatus = MedalRoundStatus.READY;
    }

    public void markPending() {
        if (medalRoundStatus != MedalRoundStatus.READY) {
            throw new IllegalStateException("Can only revert to PENDING from READY, current: " + medalRoundStatus);
        }
        this.medalRoundStatus = MedalRoundStatus.PENDING;
    }

    public void startMedalRound() {
        if (medalRoundStatus != MedalRoundStatus.READY) {
            throw new IllegalStateException("Can only start medal round from READY, current: " + medalRoundStatus);
        }
        this.medalRoundStatus = MedalRoundStatus.ACTIVE;
    }

    public void completeMedalRound() {
        if (medalRoundStatus != MedalRoundStatus.ACTIVE) {
            throw new IllegalStateException("Can only complete medal round from ACTIVE, current: " + medalRoundStatus);
        }
        this.medalRoundStatus = MedalRoundStatus.COMPLETE;
    }

    public void reopenMedalRound() {
        if (medalRoundStatus != MedalRoundStatus.COMPLETE) {
            throw new IllegalStateException("Can only reopen medal round from COMPLETE, current: " + medalRoundStatus);
        }
        this.medalRoundStatus = MedalRoundStatus.ACTIVE;
    }

    public void resetMedalRound() {
        if (medalRoundStatus != MedalRoundStatus.ACTIVE) {
            throw new IllegalStateException("Can only reset medal round from ACTIVE, current: " + medalRoundStatus);
        }
        this.medalRoundStatus = MedalRoundStatus.READY;
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
