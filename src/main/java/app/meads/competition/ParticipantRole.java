package app.meads.competition;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "participant_roles",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"participant_id", "role"}))
@Getter
public class ParticipantRole {

    @Id
    private UUID id;

    @Column(name = "participant_id", nullable = false)
    private UUID participantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompetitionRole role;

    @Column(nullable = false)
    private Instant createdAt;

    protected ParticipantRole() {} // JPA

    public ParticipantRole(UUID participantId, CompetitionRole role) {
        this.id = UUID.randomUUID();
        this.participantId = participantId;
        this.role = role;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
