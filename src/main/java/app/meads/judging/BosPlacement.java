package app.meads.judging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bos_placements",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"division_id", "place"}),
                @UniqueConstraint(columnNames = {"division_id", "entry_id"})
        })
@Getter
public class BosPlacement {

    @Id
    private UUID id;

    @Column(name = "division_id", nullable = false)
    private UUID divisionId;

    @Column(name = "entry_id", nullable = false)
    private UUID entryId;

    @Column(name = "place", nullable = false)
    private int place;

    @Column(name = "awarded_at", nullable = false)
    private Instant awardedAt;

    @Column(name = "awarded_by", nullable = false)
    private UUID awardedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected BosPlacement() {
    }

    public BosPlacement(UUID divisionId, UUID entryId, int place, UUID awardedBy) {
        if (place < 1) {
            throw new IllegalArgumentException("Place must be >= 1, got: " + place);
        }
        this.id = UUID.randomUUID();
        this.divisionId = divisionId;
        this.entryId = entryId;
        this.place = place;
        this.awardedBy = awardedBy;
    }

    public void updatePlace(int newPlace, UUID awardedBy) {
        if (newPlace < 1) {
            throw new IllegalArgumentException("Place must be >= 1, got: " + newPlace);
        }
        this.place = newPlace;
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
