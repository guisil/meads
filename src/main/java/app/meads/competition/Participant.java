package app.meads.competition;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "participants")
@Getter
public class Participant {

    @Id
    private UUID id;

    @Column(name = "competition_id", nullable = false)
    private UUID competitionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "access_code", length = 8)
    private String accessCode;

    @Column(nullable = false)
    private Instant createdAt;

    protected Participant() {} // JPA

    public Participant(UUID competitionId, UUID userId) {
        this.id = UUID.randomUUID();
        this.competitionId = competitionId;
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
