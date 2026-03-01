package app.meads.competition;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "competition_participants",
        uniqueConstraints = @UniqueConstraint(columnNames = {"competition_id", "user_id"}))
@Getter
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
    private Instant createdAt;

    protected CompetitionParticipant() {} // JPA

    public CompetitionParticipant(UUID competitionId, UUID userId, CompetitionRole role) {
        this.id = UUID.randomUUID();
        this.competitionId = competitionId;
        this.userId = userId;
        this.role = role;
        this.status = CompetitionParticipantStatus.ACTIVE;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
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
}
