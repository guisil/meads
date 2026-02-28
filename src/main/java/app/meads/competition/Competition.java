package app.meads.competition;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "competitions")
public class Competition {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompetitionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "scoring_system", nullable = false)
    private ScoringSystem scoringSystem;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    protected Competition() {} // JPA

    public Competition(UUID id, UUID eventId, String name, ScoringSystem scoringSystem) {
        this.id = id;
        this.eventId = eventId;
        this.name = name;
        this.scoringSystem = scoringSystem;
        this.status = CompetitionStatus.DRAFT;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void advanceStatus() {
        this.status = switch (status) {
            case DRAFT -> CompetitionStatus.REGISTRATION_OPEN;
            case REGISTRATION_OPEN -> CompetitionStatus.REGISTRATION_CLOSED;
            case REGISTRATION_CLOSED -> CompetitionStatus.JUDGING;
            case JUDGING -> CompetitionStatus.DELIBERATION;
            case DELIBERATION -> CompetitionStatus.RESULTS_PUBLISHED;
            case RESULTS_PUBLISHED ->
                    throw new IllegalStateException("Cannot advance past RESULTS_PUBLISHED");
        };
    }

    public void updateDetails(String name, ScoringSystem scoringSystem) {
        if (status != CompetitionStatus.DRAFT) {
            throw new IllegalStateException("Can only update details in DRAFT status");
        }
        this.name = name;
        this.scoringSystem = scoringSystem;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getName() {
        return name;
    }

    public CompetitionStatus getStatus() {
        return status;
    }

    public ScoringSystem getScoringSystem() {
        return scoringSystem;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
