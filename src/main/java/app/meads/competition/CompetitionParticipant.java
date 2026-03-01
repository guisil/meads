package app.meads.competition;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "competition_participants",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"competition_id", "event_participant_id", "role"}))
@Getter
public class CompetitionParticipant {

    @Id
    private UUID id;

    @Column(name = "competition_id", nullable = false)
    private UUID competitionId;

    @Column(name = "event_participant_id", nullable = false)
    private UUID eventParticipantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompetitionRole role;

    @Column(nullable = false)
    private Instant createdAt;

    protected CompetitionParticipant() {} // JPA

    public CompetitionParticipant(UUID competitionId, UUID eventParticipantId, CompetitionRole role) {
        this.id = UUID.randomUUID();
        this.competitionId = competitionId;
        this.eventParticipantId = eventParticipantId;
        this.role = role;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
