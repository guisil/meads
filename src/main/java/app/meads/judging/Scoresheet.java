package app.meads.judging;

import app.meads.judging.internal.MjpScoringFieldDefinition;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "scoresheets")
@Getter
public class Scoresheet {

    @Id
    private UUID id;

    @Column(name = "table_id", nullable = false)
    private UUID tableId;

    @Column(name = "entry_id", nullable = false, unique = true)
    private UUID entryId;

    @Column(name = "filled_by_judge_user_id")
    private UUID filledByJudgeUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ScoresheetStatus status;

    @Column(name = "total_score")
    private Integer totalScore;

    @Column(name = "overall_comments", length = 2000)
    private String overallComments;

    @Column(name = "advanced_to_medal_round", nullable = false)
    private boolean advancedToMedalRound;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "comment_language", length = 5)
    private String commentLanguage;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "scoresheet_id", nullable = false)
    private List<ScoreField> fields = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Scoresheet() {
    }

    public Scoresheet(UUID tableId, UUID entryId) {
        this.id = UUID.randomUUID();
        this.tableId = tableId;
        this.entryId = entryId;
        this.status = ScoresheetStatus.DRAFT;
        this.advancedToMedalRound = false;
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            fields.add(new ScoreField(def.fieldName(), def.maxValue()));
        }
    }

    public List<ScoreField> getFields() {
        return Collections.unmodifiableList(fields);
    }

    private void requireDraft(String op) {
        if (status != ScoresheetStatus.DRAFT) {
            throw new IllegalStateException(op + " requires DRAFT, current: " + status);
        }
    }

    public void updateScore(String fieldName, Integer value, String comment) {
        requireDraft("updateScore");
        var field = fields.stream()
                .filter(f -> f.getFieldName().equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown field: " + fieldName));
        field.update(value, comment);
    }

    public void updateOverallComments(String text) {
        requireDraft("updateOverallComments");
        this.overallComments = text;
    }

    public void setFilledBy(UUID judgeUserId) {
        requireDraft("setFilledBy");
        this.filledByJudgeUserId = judgeUserId;
    }

    public void setAdvancedToMedalRound(boolean advanced) {
        this.advancedToMedalRound = advanced;
    }

    public void submit() {
        requireDraft("submit");
        int total = 0;
        for (var f : fields) {
            if (f.getValue() == null) {
                throw new IllegalStateException(
                        "Cannot submit scoresheet — field '" + f.getFieldName() + "' is unfilled");
            }
            total += f.getValue();
        }
        this.totalScore = total;
        this.submittedAt = Instant.now();
        this.status = ScoresheetStatus.SUBMITTED;
    }

    public void revertToDraft() {
        if (status != ScoresheetStatus.SUBMITTED) {
            throw new IllegalStateException("Can only revert from SUBMITTED, current: " + status);
        }
        this.status = ScoresheetStatus.DRAFT;
        this.totalScore = null;
        this.submittedAt = null;
    }

    public void moveToTable(UUID newTableId) {
        requireDraft("moveToTable");
        this.tableId = newTableId;
    }

    public void setCommentLanguage(String code) {
        requireDraft("setCommentLanguage");
        this.commentLanguage = code;
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
