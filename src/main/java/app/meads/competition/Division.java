package app.meads.competition;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "divisions")
@Getter
public class Division {

    @Id
    private UUID id;

    @Column(name = "competition_id", nullable = false)
    private UUID competitionId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DivisionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "scoring_system", nullable = false)
    private ScoringSystem scoringSystem;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    protected Division() {} // JPA

    public Division(UUID competitionId, String name, ScoringSystem scoringSystem) {
        this.id = UUID.randomUUID();
        this.competitionId = competitionId;
        this.name = name;
        this.scoringSystem = scoringSystem;
        this.status = DivisionStatus.DRAFT;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void advanceStatus() {
        this.status = switch (status) {
            case DRAFT -> DivisionStatus.REGISTRATION_OPEN;
            case REGISTRATION_OPEN -> DivisionStatus.REGISTRATION_CLOSED;
            case REGISTRATION_CLOSED -> DivisionStatus.JUDGING;
            case JUDGING -> DivisionStatus.DELIBERATION;
            case DELIBERATION -> DivisionStatus.RESULTS_PUBLISHED;
            case RESULTS_PUBLISHED ->
                    throw new IllegalStateException("Cannot advance past RESULTS_PUBLISHED");
        };
    }

    public void updateDetails(String name, ScoringSystem scoringSystem) {
        if (status != DivisionStatus.DRAFT) {
            throw new IllegalStateException("Can only update details in DRAFT status");
        }
        this.name = name;
        this.scoringSystem = scoringSystem;
    }
}
