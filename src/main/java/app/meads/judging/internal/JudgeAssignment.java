package app.meads.judging.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "judge_assignments")
@Getter
public class JudgeAssignment {

    @Id
    private UUID id;

    @Column(name = "judge_user_id", nullable = false)
    private UUID judgeUserId;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    protected JudgeAssignment() {
    }

    public JudgeAssignment(UUID judgeUserId) {
        this.id = UUID.randomUUID();
        this.judgeUserId = judgeUserId;
    }

    @PrePersist
    void onCreate() {
        this.assignedAt = Instant.now();
    }
}
