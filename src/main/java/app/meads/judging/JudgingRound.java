package app.meads.judging;

import app.meads.judging.internal.JudgeAssignment;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "judging_rounds")
@Getter
public class JudgingRound {

    @Id
    private UUID id;

    @Column(name = "judging_id", nullable = false)
    private UUID judgingId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "division_category_id", nullable = false)
    private UUID divisionCategoryId;

    @Column(name = "physical_table_id")
    private UUID physicalTableId;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JudgingRoundStatus status;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "judging_round_id", nullable = false)
    @OrderBy("assignedAt ASC")
    private List<JudgeAssignment> assignments = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected JudgingRound() {
    }

    public JudgingRound(UUID judgingId, String name, UUID divisionCategoryId, LocalDate scheduledDate) {
        this(judgingId, null, name, divisionCategoryId, scheduledDate);
    }

    public JudgingRound(UUID judgingId, UUID physicalTableId, String name,
                         UUID divisionCategoryId, LocalDate scheduledDate) {
        this.id = UUID.randomUUID();
        this.judgingId = judgingId;
        this.physicalTableId = physicalTableId;
        this.name = name;
        this.divisionCategoryId = divisionCategoryId;
        this.scheduledDate = scheduledDate;
        this.status = JudgingRoundStatus.NOT_STARTED;
    }

    public void assignToPhysicalTable(UUID physicalTableId) {
        this.physicalTableId = physicalTableId;
    }

    public List<JudgeAssignment> getAssignments() {
        return Collections.unmodifiableList(assignments);
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void updateScheduledDate(LocalDate scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public void assignJudge(UUID judgeUserId) {
        boolean alreadyAssigned = assignments.stream()
                .anyMatch(a -> a.getJudgeUserId().equals(judgeUserId));
        if (alreadyAssigned) {
            return;
        }
        assignments.add(new JudgeAssignment(judgeUserId));
    }

    public void removeJudge(UUID judgeUserId) {
        assignments.removeIf(a -> a.getJudgeUserId().equals(judgeUserId));
    }

    public void startRound1() {
        if (status != JudgingRoundStatus.NOT_STARTED) {
            throw new IllegalStateException("Table can only start ROUND_1 from NOT_STARTED, current: " + status);
        }
        this.status = JudgingRoundStatus.ROUND_1;
    }

    public void markComplete() {
        if (status != JudgingRoundStatus.ROUND_1) {
            throw new IllegalStateException("Table can only complete from ROUND_1, current: " + status);
        }
        this.status = JudgingRoundStatus.COMPLETE;
    }

    public void reopenToRound1() {
        if (status != JudgingRoundStatus.COMPLETE) {
            throw new IllegalStateException("Table can only reopen from COMPLETE, current: " + status);
        }
        this.status = JudgingRoundStatus.ROUND_1;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
