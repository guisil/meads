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
    }

    public void updateMode(MedalRoundMode mode) {
        this.medalRoundMode = mode;
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
