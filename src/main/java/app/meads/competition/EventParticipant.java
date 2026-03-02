package app.meads.competition;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_participants")
@Getter
public class EventParticipant {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "access_code", length = 8)
    private String accessCode;

    @Column(nullable = false)
    private Instant createdAt;

    protected EventParticipant() {} // JPA

    public EventParticipant(UUID eventId, UUID userId) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.userId = userId;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public void assignAccessCode(String code) {
        if (code == null || code.length() != 8) {
            throw new IllegalArgumentException("Access code must be exactly 8 characters");
        }
        this.accessCode = code.toUpperCase();
    }
}
