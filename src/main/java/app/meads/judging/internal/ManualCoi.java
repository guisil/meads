package app.meads.judging.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * An admin-declared conflict of interest between a judge and an entrant within a
 * competition. Automatic COI detection is account/meadery based and misses the
 * case where one person uses two separate accounts (e.g. a business email for
 * entries and a personal email for judging). A manual COI hard-blocks the judge
 * from judging that entrant's entries, as if they were the same account.
 */
@Entity
@Table(name = "manual_cois")
@Getter
public class ManualCoi {

    @Id
    private UUID id;

    @Column(name = "competition_id", nullable = false)
    private UUID competitionId;

    @Column(name = "judge_user_id", nullable = false)
    private UUID judgeUserId;

    @Column(name = "entrant_user_id", nullable = false)
    private UUID entrantUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    protected ManualCoi() {
    }

    public ManualCoi(UUID competitionId, UUID judgeUserId, UUID entrantUserId, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.competitionId = competitionId;
        this.judgeUserId = judgeUserId;
        this.entrantUserId = entrantUserId;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
