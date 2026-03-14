package app.meads.competition;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
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

    @Column(name = "meadery_name_required", nullable = false)
    private boolean meaderyNameRequired;

    @Column(name = "registration_deadline", nullable = false)
    private LocalDateTime registrationDeadline;

    @Column(name = "registration_deadline_timezone", nullable = false, length = 50)
    private String registrationDeadlineTimezone;

    private Instant updatedAt;

    protected Division() {} // JPA

    public Division(UUID competitionId, String name, String shortName,
                    ScoringSystem scoringSystem,
                    LocalDateTime registrationDeadline, String registrationDeadlineTimezone) {
        Competition.validateShortName(shortName);
        this.id = UUID.randomUUID();
        this.competitionId = competitionId;
        this.name = name;
        this.shortName = shortName;
        this.scoringSystem = scoringSystem;
        this.status = DivisionStatus.DRAFT;
        this.meaderyNameRequired = false;
        this.registrationDeadline = registrationDeadline;
        this.registrationDeadlineTimezone = registrationDeadlineTimezone;
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

    public void updateMeaderyNameRequired(boolean meaderyNameRequired) {
        if (status != DivisionStatus.DRAFT) {
            throw new IllegalStateException("Meadery name requirement can only be changed in DRAFT status");
        }
        this.meaderyNameRequired = meaderyNameRequired;
    }

    public void updateRegistrationDeadline(LocalDateTime deadline, String timezone) {
        if (status != DivisionStatus.DRAFT && status != DivisionStatus.REGISTRATION_OPEN) {
            throw new IllegalStateException(
                    "Registration deadline can only be changed in DRAFT or REGISTRATION_OPEN status");
        }
        this.registrationDeadline = deadline;
        this.registrationDeadlineTimezone = timezone;
    }

    public void updateDetails(String name, String shortName, ScoringSystem scoringSystem,
                              String entryPrefix) {
        if (status != DivisionStatus.DRAFT && scoringSystem != this.scoringSystem) {
            throw new IllegalStateException("Scoring system can only be changed in DRAFT status");
        }
        if (status != DivisionStatus.DRAFT && !Objects.equals(entryPrefix, this.entryPrefix)) {
            throw new IllegalStateException("Entry prefix can only be changed in DRAFT status");
        }
        Competition.validateShortName(shortName);
        this.name = name;
        this.shortName = shortName;
        this.scoringSystem = scoringSystem;
        this.entryPrefix = entryPrefix;
    }
}
