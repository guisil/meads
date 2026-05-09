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
@Table(name = "medal_awards")
@Getter
public class MedalAward {

    @Id
    private UUID id;

    @Column(name = "entry_id", nullable = false, unique = true)
    private UUID entryId;

    @Column(name = "division_id", nullable = false)
    private UUID divisionId;

    @Column(name = "final_category_id", nullable = false)
    private UUID finalCategoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "medal", length = 10)
    private Medal medal;

    @Column(name = "awarded_at", nullable = false)
    private Instant awardedAt;

    @Column(name = "awarded_by", nullable = false)
    private UUID awardedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected MedalAward() {
    }

    public MedalAward(UUID entryId, UUID divisionId, UUID finalCategoryId, Medal medal, UUID awardedBy) {
        this.id = UUID.randomUUID();
        this.entryId = entryId;
        this.divisionId = divisionId;
        this.finalCategoryId = finalCategoryId;
        this.medal = medal;
        this.awardedBy = awardedBy;
    }

    public void updateMedal(Medal newValue, UUID awardedBy) {
        this.medal = newValue;
        this.awardedBy = awardedBy;
    }

    @PrePersist
    void onCreate() {
        this.awardedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
