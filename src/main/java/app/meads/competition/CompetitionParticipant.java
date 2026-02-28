package app.meads.competition;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "competition_participants",
        uniqueConstraints = @UniqueConstraint(columnNames = {"competition_id", "user_id"}))
public class CompetitionParticipant {

    @Id
    private UUID id;

    @Column(name = "competition_id", nullable = false)
    private UUID competitionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompetitionRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompetitionParticipantStatus status;

    @Column(name = "access_code", length = 8)
    private String accessCode;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected CompetitionParticipant() {} // JPA

    public CompetitionParticipant(UUID id, UUID competitionId, UUID userId, CompetitionRole role) {
        this.id = id;
        this.competitionId = competitionId;
        this.userId = userId;
        this.role = role;
        this.status = CompetitionParticipantStatus.ACTIVE;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public void assignAccessCode(String code) {
        if (!role.requiresAccessCode()) {
            throw new IllegalStateException(
                    "Access codes can only be assigned to roles that require them");
        }
        if (code == null || code.length() != 8) {
            throw new IllegalArgumentException("Access code must be exactly 8 characters");
        }
        this.accessCode = code.toUpperCase();
    }

    public void withdraw() {
        if (this.status == CompetitionParticipantStatus.WITHDRAWN) {
            throw new IllegalStateException("Participant is already withdrawn");
        }
        this.status = CompetitionParticipantStatus.WITHDRAWN;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompetitionId() {
        return competitionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public CompetitionRole getRole() {
        return role;
    }

    public CompetitionParticipantStatus getStatus() {
        return status;
    }

    public String getAccessCode() {
        return accessCode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
