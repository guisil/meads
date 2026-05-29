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

    @Column(name = "round_id", nullable = false)
    private UUID roundId;

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

    /**
     * Minimum length for a per-criterion comment to count as "filled in".
     * Enforced when the judge clicks "Save" (DRAFT → FILLED) so every criterion
     * carries at least a short justification the entrant can read later. High
     * enough to block accidental keystrokes ("ok", a stray space) yet still
     * terse. The overall ("Additional comments") field is now optional and has
     * no minimum.
     */
    public static final int MIN_PER_FIELD_COMMENT_LENGTH = 15;

    public Scoresheet(UUID roundId, UUID entryId) {
        this.id = UUID.randomUUID();
        this.roundId = roundId;
        this.entryId = entryId;
        this.status = ScoresheetStatus.BLANK;
        this.advancedToMedalRound = false;
        for (var def : MjpScoringFieldDefinition.MJP_FIELDS) {
            fields.add(new ScoreField(def.fieldName(), def.maxValue()));
        }
    }

    public List<ScoreField> getFields() {
        return Collections.unmodifiableList(fields);
    }

    /**
     * Allows mutation when the sheet is BLANK, DRAFT, or FILLED. Use this for
     * operations that represent judge content entry (scores, comments) — the
     * first such call promotes BLANK → DRAFT via {@link #promoteFromBlank()},
     * and editing scored content on a FILLED sheet demotes it back to DRAFT
     * via {@link #demoteFromFilled()} (the judge must re-Save to re-validate).
     */
    private void requireMutable(String op) {
        if (status != ScoresheetStatus.BLANK
                && status != ScoresheetStatus.DRAFT
                && status != ScoresheetStatus.FILLED) {
            throw new IllegalStateException(op + " requires BLANK, DRAFT or FILLED, current: " + status);
        }
    }

    private void promoteFromBlank() {
        if (status == ScoresheetStatus.BLANK) {
            this.status = ScoresheetStatus.DRAFT;
        }
    }

    /**
     * Editing scored content on a FILLED sheet invalidates the judge's "Save" —
     * drop it back to DRAFT so it must be re-validated via {@link #markFilled()}
     * before it can be submitted.
     */
    private void demoteFromFilled() {
        if (status == ScoresheetStatus.FILLED) {
            this.status = ScoresheetStatus.DRAFT;
        }
    }

    public void updateScore(String fieldName, Integer value, String comment) {
        requireMutable("updateScore");
        var field = fields.stream()
                .filter(f -> f.getFieldName().equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown field: " + fieldName));
        field.update(value, comment);
        promoteFromBlank();
        demoteFromFilled();
    }

    public void updateOverallComments(String text) {
        requireMutable("updateOverallComments");
        this.overallComments = text;
        promoteFromBlank();
        demoteFromFilled();
    }

    public void setFilledBy(UUID judgeUserId) {
        requireMutable("setFilledBy");
        this.filledByJudgeUserId = judgeUserId;
    }

    public void setAdvancedToMedalRound(boolean advanced) {
        this.advancedToMedalRound = advanced;
    }

    /**
     * Promotes a fully-scored DRAFT sheet to FILLED — the judge's explicit
     * "this is done" signal via the validating "Save" button. Requires every
     * MJP field to carry a value; per-criterion comment-length validation lives
     * in the service (it raises a localized {@link app.meads.BusinessRuleException}).
     * Does NOT compute the total — that happens at {@link #submit()}.
     */
    public void markFilled() {
        if (status == ScoresheetStatus.FILLED) {
            return; // idempotent — an unchanged FILLED sheet is already validated
        }
        if (status != ScoresheetStatus.DRAFT) {
            throw new IllegalStateException("markFilled requires DRAFT, current: " + status);
        }
        for (var f : fields) {
            if (f.getValue() == null) {
                throw new IllegalStateException(
                        "Cannot mark filled — field '" + f.getFieldName() + "' is unscored");
            }
        }
        this.status = ScoresheetStatus.FILLED;
    }

    public void submit() {
        if (status != ScoresheetStatus.FILLED) {
            throw new IllegalStateException("submit requires FILLED, current: " + status);
        }
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

    /**
     * Drops a SUBMITTED sheet back to FILLED (not DRAFT) — used when an admin
     * reopens a COMPLETE round. The sheet stays "validated/filled"; the total +
     * submittedAt are cleared, and only a subsequent content edit demotes it to
     * DRAFT (requiring a fresh Save before the round can finalize again).
     */
    public void revertToFilled() {
        if (status != ScoresheetStatus.SUBMITTED) {
            throw new IllegalStateException("Can only revert to FILLED from SUBMITTED, current: " + status);
        }
        this.status = ScoresheetStatus.FILLED;
        this.totalScore = null;
        this.submittedAt = null;
    }

    public void moveToRound(UUID newRoundId) {
        requireMutable("moveToRound");
        this.roundId = newRoundId;
    }

    public void setCommentLanguage(String code) {
        requireMutable("setCommentLanguage");
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
