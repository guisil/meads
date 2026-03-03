package app.meads.entry;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "entries")
@Getter
public class Entry {

    @Id
    private UUID id;

    @Column(name = "division_id", nullable = false)
    private UUID divisionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "entry_number", nullable = false)
    private int entryNumber;

    @Column(name = "entry_code", nullable = false, length = 6)
    private String entryCode;

    @Column(name = "mead_name", nullable = false)
    private String meadName;

    @Column(name = "initial_category_id", nullable = false)
    private UUID initialCategoryId;

    @Column(name = "final_category_id")
    private UUID finalCategoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Sweetness sweetness;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Strength strength;

    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal abv;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Carbonation carbonation;

    @Column(name = "honey_varieties", nullable = false, columnDefinition = "TEXT")
    private String honeyVarieties;

    @Column(name = "other_ingredients", columnDefinition = "TEXT")
    private String otherIngredients;

    @Column(name = "wood_aged", nullable = false)
    private boolean woodAged;

    @Column(name = "wood_ageing_details", columnDefinition = "TEXT")
    private String woodAgeingDetails;

    @Column(name = "additional_information", columnDefinition = "TEXT")
    private String additionalInformation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Entry() {} // JPA

    public Entry(UUID divisionId, UUID userId, int entryNumber, String entryCode,
                 String meadName, UUID initialCategoryId, Sweetness sweetness,
                 Strength strength, BigDecimal abv, Carbonation carbonation,
                 String honeyVarieties, String otherIngredients,
                 boolean woodAged, String woodAgeingDetails,
                 String additionalInformation) {
        validateWoodAgeing(woodAged, woodAgeingDetails);
        this.id = UUID.randomUUID();
        this.divisionId = divisionId;
        this.userId = userId;
        this.entryNumber = entryNumber;
        this.entryCode = entryCode;
        this.meadName = meadName;
        this.initialCategoryId = initialCategoryId;
        this.sweetness = sweetness;
        this.strength = strength;
        this.abv = abv;
        this.carbonation = carbonation;
        this.honeyVarieties = honeyVarieties;
        this.otherIngredients = otherIngredients;
        this.woodAged = woodAged;
        this.woodAgeingDetails = woodAgeingDetails;
        this.additionalInformation = additionalInformation;
        this.status = EntryStatus.DRAFT;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void submit() {
        if (status != EntryStatus.DRAFT) {
            throw new IllegalStateException("Cannot submit entry in status " + status);
        }
        this.status = EntryStatus.SUBMITTED;
    }

    public void markReceived() {
        if (status != EntryStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Cannot mark as received — entry is in status " + status);
        }
        this.status = EntryStatus.RECEIVED;
    }

    public void withdraw() {
        if (status == EntryStatus.WITHDRAWN) {
            throw new IllegalStateException("Cannot withdraw — entry is already withdrawn");
        }
        this.status = EntryStatus.WITHDRAWN;
    }

    public void updateDetails(String meadName, UUID initialCategoryId,
                               Sweetness sweetness, Strength strength,
                               BigDecimal abv, Carbonation carbonation,
                               String honeyVarieties, String otherIngredients,
                               boolean woodAged, String woodAgeingDetails,
                               String additionalInformation) {
        if (status != EntryStatus.DRAFT) {
            throw new IllegalStateException(
                    "Cannot update details — entry is in status " + status);
        }
        validateWoodAgeing(woodAged, woodAgeingDetails);
        this.meadName = meadName;
        this.initialCategoryId = initialCategoryId;
        this.sweetness = sweetness;
        this.strength = strength;
        this.abv = abv;
        this.carbonation = carbonation;
        this.honeyVarieties = honeyVarieties;
        this.otherIngredients = otherIngredients;
        this.woodAged = woodAged;
        this.woodAgeingDetails = woodAgeingDetails;
        this.additionalInformation = additionalInformation;
    }

    public void assignFinalCategory(UUID finalCategoryId) {
        if (status == EntryStatus.WITHDRAWN) {
            throw new IllegalStateException(
                    "Cannot assign final category — entry is withdrawn");
        }
        this.finalCategoryId = finalCategoryId;
    }

    public UUID getEffectiveCategoryId() {
        return finalCategoryId != null ? finalCategoryId : initialCategoryId;
    }

    private void validateWoodAgeing(boolean woodAged, String woodAgeingDetails) {
        if (woodAged && (woodAgeingDetails == null || woodAgeingDetails.isBlank())) {
            throw new IllegalArgumentException(
                    "Wood ageing details are required when wood aged is true");
        }
    }
}
