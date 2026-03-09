package app.meads.competition;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "divisions")
@Getter
public class Division {

    @Id
    private UUID id;

    @Column(name = "competition_id", nullable = false)
    private UUID competitionId;

    @Column(nullable = false)
    private String name;

    @Column(name = "short_name", nullable = false)
    private String shortName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DivisionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "scoring_system", nullable = false)
    private ScoringSystem scoringSystem;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(name = "max_entries_per_subcategory")
    private Integer maxEntriesPerSubcategory;

    @Column(name = "max_entries_per_main_category")
    private Integer maxEntriesPerMainCategory;

    @Column(name = "max_entries_total")
    private Integer maxEntriesTotal;

    @Column(name = "entry_prefix", length = 5)
    private String entryPrefix;

    private Instant updatedAt;

    protected Division() {} // JPA

    public Division(UUID competitionId, String name, String shortName, ScoringSystem scoringSystem) {
        Competition.validateShortName(shortName);
        this.id = UUID.randomUUID();
        this.competitionId = competitionId;
        this.name = name;
        this.shortName = shortName;
        this.scoringSystem = scoringSystem;
        this.status = DivisionStatus.DRAFT;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void advanceStatus() {
        this.status = switch (status) {
            case DRAFT -> DivisionStatus.REGISTRATION_OPEN;
            case REGISTRATION_OPEN -> DivisionStatus.REGISTRATION_CLOSED;
            case REGISTRATION_CLOSED -> DivisionStatus.JUDGING;
            case JUDGING -> DivisionStatus.DELIBERATION;
            case DELIBERATION -> DivisionStatus.RESULTS_PUBLISHED;
            case RESULTS_PUBLISHED ->
                    throw new IllegalStateException("Cannot advance past RESULTS_PUBLISHED");
        };
    }

    public void revertStatus() {
        this.status = status.previous()
                .orElseThrow(() -> new IllegalStateException("Cannot revert from DRAFT"));
    }

    public void updateEntryLimits(Integer maxEntriesPerSubcategory,
                                  Integer maxEntriesPerMainCategory,
                                  Integer maxEntriesTotal) {
        if (status != DivisionStatus.DRAFT) {
            throw new IllegalStateException("Entry limits can only be changed in DRAFT status");
        }
        this.maxEntriesPerSubcategory = maxEntriesPerSubcategory;
        this.maxEntriesPerMainCategory = maxEntriesPerMainCategory;
        this.maxEntriesTotal = maxEntriesTotal;
    }

    public void updateDetails(String name, String shortName, ScoringSystem scoringSystem,
                              String entryPrefix) {
        if (status != DivisionStatus.DRAFT && scoringSystem != this.scoringSystem) {
            throw new IllegalStateException("Scoring system can only be changed in DRAFT status");
        }
        Competition.validateShortName(shortName);
        this.name = name;
        this.shortName = shortName;
        this.scoringSystem = scoringSystem;
        this.entryPrefix = entryPrefix;
    }
}
