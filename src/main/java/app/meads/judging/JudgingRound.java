package app.meads.judging;

import app.meads.judging.internal.JudgeAssignment;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private RoundType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "medal_mode", length = 20)
    private MedalRoundMode medalMode;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JudgingRoundStatus status;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "judging_round_id", nullable = false)
    @OrderBy("assignedAt ASC")
    private List<JudgeAssignment> assignments = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "judging_round_entries",
                     joinColumns = @JoinColumn(name = "judging_round_id"))
    @Column(name = "entry_id", nullable = false)
    private Set<UUID> entries = new HashSet<>();

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
        this.type = RoundType.SCORING;
        this.medalMode = null;
        this.status = JudgingRoundStatus.PENDING;
    }

    public void assignToPhysicalTable(UUID physicalTableId) {
        this.physicalTableId = physicalTableId;
    }

    public void convertToMedalRound(MedalRoundMode mode) {
        if (status != JudgingRoundStatus.PENDING) {
            throw new IllegalStateException("Can only convert to medal round while PENDING, current: " + status);
        }
        this.type = RoundType.MEDAL;
        this.medalMode = mode;
    }

    public List<JudgeAssignment> getAssignments() {
        return Collections.unmodifiableList(assignments);
    }

    public Set<UUID> getEntries() {
        return Collections.unmodifiableSet(entries);
    }

    public void assignEntry(UUID entryId) {
        entries.add(entryId);
    }

    public void unassignEntry(UUID entryId) {
        entries.remove(entryId);
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

    public void markReady() {
        if (status != JudgingRoundStatus.PENDING) {
            throw new IllegalStateException("Round can only become READY from PENDING, current: " + status);
        }
        this.status = JudgingRoundStatus.READY;
    }

    public void markPending() {
        if (status != JudgingRoundStatus.READY) {
            throw new IllegalStateException("Round can only revert to PENDING from READY, current: " + status);
        }
        this.status = JudgingRoundStatus.PENDING;
    }

    public void start() {
        if (status != JudgingRoundStatus.PENDING && status != JudgingRoundStatus.READY) {
            throw new IllegalStateException("Round can only start from PENDING or READY, current: " + status);
        }
        this.status = JudgingRoundStatus.ACTIVE;
    }

    public void markComplete() {
        if (status != JudgingRoundStatus.ACTIVE) {
            throw new IllegalStateException("Round can only complete from ACTIVE, current: " + status);
        }
        this.status = JudgingRoundStatus.COMPLETE;
    }

    public void reopen() {
        if (status != JudgingRoundStatus.COMPLETE) {
            throw new IllegalStateException("Round can only reopen from COMPLETE, current: " + status);
        }
        this.status = JudgingRoundStatus.ACTIVE;
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
