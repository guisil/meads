package app.meads.entry;

import app.meads.competition.CompetitionRole;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import app.meads.competition.DivisionStatus;
import app.meads.entry.internal.EntryCreditRepository;
import app.meads.entry.internal.EntryRepository;
import app.meads.entry.internal.ProductMappingRepository;
import app.meads.identity.Role;
import app.meads.identity.UserService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Validated
public class EntryService {

    private static final String ENTRY_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int ENTRY_CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProductMappingRepository productMappingRepository;
    private final EntryCreditRepository creditRepository;
    private final EntryRepository entryRepository;
    private final CompetitionService competitionService;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    EntryService(ProductMappingRepository productMappingRepository,
                 EntryCreditRepository creditRepository,
                 EntryRepository entryRepository,
                 CompetitionService competitionService,
                 UserService userService,
                 ApplicationEventPublisher eventPublisher) {
        this.productMappingRepository = productMappingRepository;
        this.creditRepository = creditRepository;
        this.entryRepository = entryRepository;
        this.competitionService = competitionService;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
    }

    // --- Product Mapping methods ---

    public ProductMapping createProductMapping(@NotNull UUID divisionId,
                                                @NotBlank String jumpsellerProductId,
                                                String jumpsellerSku,
                                                @NotBlank String productName,
                                                int creditsPerUnit,
                                                @NotNull UUID requestingUserId) {
        requireAuthorizedForDivision(divisionId, requestingUserId);
        if (productMappingRepository.existsByDivisionIdAndJumpsellerProductId(
                divisionId, jumpsellerProductId)) {
            throw new IllegalArgumentException("Product is already mapped to this division");
        }
        var mapping = new ProductMapping(divisionId, jumpsellerProductId, jumpsellerSku,
                productName, creditsPerUnit);
        return productMappingRepository.save(mapping);
    }

    public ProductMapping updateProductMapping(@NotNull UUID mappingId,
                                                @NotBlank String productName,
                                                int creditsPerUnit,
                                                @NotNull UUID requestingUserId) {
        var mapping = productMappingRepository.findById(mappingId)
                .orElseThrow(() -> new IllegalArgumentException("Product mapping not found"));
        requireAuthorizedForDivision(mapping.getDivisionId(), requestingUserId);
        mapping.updateDetails(productName, creditsPerUnit);
        return productMappingRepository.save(mapping);
    }

    public void removeProductMapping(@NotNull UUID mappingId,
                                      @NotNull UUID requestingUserId) {
        var mapping = productMappingRepository.findById(mappingId)
                .orElseThrow(() -> new IllegalArgumentException("Product mapping not found"));
        requireAuthorizedForDivision(mapping.getDivisionId(), requestingUserId);
        productMappingRepository.delete(mapping);
    }

    public List<ProductMapping> findProductMappings(@NotNull UUID divisionId) {
        return productMappingRepository.findByDivisionId(divisionId);
    }

    public List<ProductMapping> findProductMappingsByProductId(
            @NotBlank String jumpsellerProductId) {
        return productMappingRepository.findByJumpsellerProductId(jumpsellerProductId);
    }

    // --- Credit methods ---

    public int getCreditBalance(@NotNull UUID divisionId, @NotNull UUID userId) {
        return creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId);
    }

    public void addCredits(@NotNull UUID divisionId,
                            @NotBlank @Email String userEmail,
                            int amount,
                            @NotNull UUID requestingUserId) {
        requireAuthorizedForDivision(divisionId, requestingUserId);
        var division = competitionService.findDivisionById(divisionId);
        var user = userService.findOrCreateByEmail(userEmail);

        // Mutual exclusivity check
        if (hasCreditConflict(user.getId(), divisionId, division.getCompetitionId())) {
            throw new IllegalArgumentException(
                    "Cannot add credits: mutual exclusivity violation — "
                    + "user already has credits in another division of the same competition");
        }

        var credit = new EntryCredit(divisionId, user.getId(), amount,
                "ADMIN", userService.findById(requestingUserId).getEmail());
        creditRepository.save(credit);

        // Add ENTRANT participant at competition level
        try {
            competitionService.addParticipantByEmail(
                    division.getCompetitionId(), userEmail,
                    CompetitionRole.ENTRANT, requestingUserId);
        } catch (IllegalArgumentException e) {
            // Already has the role — ignore
        }

        eventPublisher.publishEvent(new CreditsAwardedEvent(
                divisionId, user.getId(), amount, "ADMIN"));
    }

    public void removeCredits(@NotNull UUID divisionId,
                               @NotNull UUID userId,
                               int amount,
                               @NotNull UUID requestingUserId) {
        requireAuthorizedForDivision(divisionId, requestingUserId);
        var balance = creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId);
        if (balance < amount) {
            throw new IllegalArgumentException(
                    "Insufficient credit balance: has " + balance + ", trying to remove " + amount);
        }
        var credit = new EntryCredit(divisionId, userId, -amount,
                "ADMIN", userService.findById(requestingUserId).getEmail());
        creditRepository.save(credit);
    }

    public boolean hasCreditsInOtherDivision(@NotNull UUID competitionId,
                                              @NotNull UUID divisionId,
                                              @NotNull UUID userId) {
        return hasCreditConflict(userId, divisionId, competitionId);
    }

    // --- Entry methods ---

    public Entry createEntry(@NotNull UUID divisionId,
                              @NotNull UUID userId,
                              @NotBlank String meadName,
                              @NotNull UUID initialCategoryId,
                              @NotNull Sweetness sweetness,
                              @NotNull Strength strength,
                              @NotNull BigDecimal abv,
                              @NotNull Carbonation carbonation,
                              @NotBlank String honeyVarieties,
                              String otherIngredients,
                              boolean woodAged,
                              String woodAgeingDetails,
                              String additionalInformation) {
        var division = competitionService.findDivisionById(divisionId);

        // Division must be open for registration
        if (division.getStatus() != DivisionStatus.REGISTRATION_OPEN) {
            throw new IllegalArgumentException("Division is not open for registration");
        }

        // Credit check: must have available credits
        var creditBalance = creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId);
        var activeEntries = entryRepository.countByDivisionIdAndUserIdAndStatusNot(
                divisionId, userId, EntryStatus.WITHDRAWN);
        if (creditBalance <= activeEntries) {
            throw new IllegalArgumentException(
                    "No available credits — balance: " + creditBalance
                    + ", active entries: " + activeEntries);
        }

        // Entry limits
        checkEntryLimits(divisionId, userId, initialCategoryId, division);

        // Generate entry number and code
        var entryNumber = entryRepository.findMaxEntryNumberByDivisionId(divisionId) + 1;
        var entryCode = generateEntryCode(divisionId);

        var entry = new Entry(divisionId, userId, entryNumber, entryCode,
                meadName, initialCategoryId, sweetness, strength, abv, carbonation,
                honeyVarieties, otherIngredients, woodAged, woodAgeingDetails,
                additionalInformation);
        return entryRepository.save(entry);
    }

    public Entry updateEntry(@NotNull UUID entryId,
                              @NotNull UUID userId,
                              @NotBlank String meadName,
                              @NotNull UUID initialCategoryId,
                              @NotNull Sweetness sweetness,
                              @NotNull Strength strength,
                              @NotNull BigDecimal abv,
                              @NotNull Carbonation carbonation,
                              @NotBlank String honeyVarieties,
                              String otherIngredients,
                              boolean woodAged,
                              String woodAgeingDetails,
                              String additionalInformation) {
        var entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Entry not found"));
        if (!entry.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User is not the owner of this entry");
        }
        entry.updateDetails(meadName, initialCategoryId, sweetness, strength, abv,
                carbonation, honeyVarieties, otherIngredients, woodAged,
                woodAgeingDetails, additionalInformation);
        return entryRepository.save(entry);
    }

    public void deleteEntry(@NotNull UUID entryId, @NotNull UUID userId) {
        var entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Entry not found"));
        if (!entry.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User is not the owner of this entry");
        }
        if (entry.getStatus() != EntryStatus.DRAFT) {
            throw new IllegalArgumentException("Can only delete entries in DRAFT status — "
                    + "entry is " + entry.getStatus());
        }
        entryRepository.delete(entry);
    }

    public void submitAllDrafts(@NotNull UUID divisionId, @NotNull UUID userId) {
        var drafts = entryRepository.findByDivisionIdAndUserIdAndStatus(
                divisionId, userId, EntryStatus.DRAFT);
        if (drafts.isEmpty()) {
            return;
        }
        for (var entry : drafts) {
            entry.submit();
            entryRepository.save(entry);
        }
        eventPublisher.publishEvent(new EntriesSubmittedEvent(
                divisionId, userId, drafts.size()));
    }

    public Entry markReceived(@NotNull UUID entryId, @NotNull UUID requestingUserId) {
        var entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Entry not found"));
        requireAuthorizedForDivision(entry.getDivisionId(), requestingUserId);
        entry.markReceived();
        return entryRepository.save(entry);
    }

    public Entry withdrawEntry(@NotNull UUID entryId, @NotNull UUID requestingUserId) {
        var entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Entry not found"));
        requireAuthorizedForDivision(entry.getDivisionId(), requestingUserId);
        entry.withdraw();
        return entryRepository.save(entry);
    }

    public Entry adminUpdateEntry(@NotNull UUID entryId,
                                    @NotBlank String meadName,
                                    @NotNull UUID initialCategoryId,
                                    @NotNull Sweetness sweetness,
                                    @NotNull Strength strength,
                                    @NotNull BigDecimal abv,
                                    @NotNull Carbonation carbonation,
                                    @NotBlank String honeyVarieties,
                                    String otherIngredients,
                                    boolean woodAged,
                                    String woodAgeingDetails,
                                    String additionalInformation,
                                    @NotNull UUID requestingUserId) {
        var entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Entry not found"));
        requireAuthorizedForDivision(entry.getDivisionId(), requestingUserId);
        entry.adminUpdateDetails(meadName, initialCategoryId, sweetness, strength, abv,
                carbonation, honeyVarieties, otherIngredients, woodAged,
                woodAgeingDetails, additionalInformation);
        return entryRepository.save(entry);
    }

    public List<Entry> findEntriesByDivision(@NotNull UUID divisionId) {
        return entryRepository.findByDivisionId(divisionId);
    }

    public List<Entry> findEntriesByDivisionAndUser(@NotNull UUID divisionId,
                                                      @NotNull UUID userId) {
        return entryRepository.findByDivisionIdAndUserId(divisionId, userId);
    }

    public Entry findEntryById(@NotNull UUID entryId) {
        return entryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Entry not found"));
    }

    public long countActiveEntries(@NotNull UUID divisionId, @NotNull UUID userId) {
        return entryRepository.countByDivisionIdAndUserIdAndStatusNot(
                divisionId, userId, EntryStatus.WITHDRAWN);
    }

    // --- Private helpers ---

    private void requireAuthorizedForDivision(UUID divisionId, UUID userId) {
        var user = userService.findById(userId);
        if (user.getRole() == Role.SYSTEM_ADMIN) {
            return;
        }
        if (!competitionService.isAuthorizedForDivision(divisionId, userId)) {
            throw new IllegalArgumentException("User is not authorized to perform this action");
        }
    }

    private boolean hasCreditConflict(UUID userId, UUID divisionId, UUID competitionId) {
        var existingDivisionIds = creditRepository.findDistinctDivisionIdsByUserId(userId);
        if (existingDivisionIds.isEmpty()) {
            return false;
        }
        if (existingDivisionIds.size() == 1 && existingDivisionIds.contains(divisionId)) {
            return false; // Same division only — no conflict
        }
        var competitionDivisions = competitionService.findDivisionsByCompetition(competitionId);
        var competitionDivisionIds = competitionDivisions.stream()
                .map(Division::getId)
                .toList();
        return existingDivisionIds.stream()
                .anyMatch(id -> competitionDivisionIds.contains(id) && !id.equals(divisionId));
    }

    private void checkEntryLimits(UUID divisionId, UUID userId,
                                   UUID initialCategoryId, Division division) {
        // Subcategory limit
        if (division.getMaxEntriesPerSubcategory() != null) {
            var count = entryRepository.countByDivisionIdAndUserIdAndInitialCategoryIdAndStatusNot(
                    divisionId, userId, initialCategoryId, EntryStatus.WITHDRAWN);
            if (count >= division.getMaxEntriesPerSubcategory()) {
                throw new IllegalArgumentException(
                        "Entry limit reached for this subcategory (max "
                        + division.getMaxEntriesPerSubcategory() + ")");
            }
        }

        // Main category limit
        if (division.getMaxEntriesPerMainCategory() != null) {
            var categories = competitionService.findDivisionCategories(divisionId);
            var category = categories.stream()
                    .filter(c -> c.getId().equals(initialCategoryId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Category not found"));

            var mainCategoryId = category.getParentId() != null
                    ? category.getParentId() : category.getId();
            var mainCategoryGroupIds = categories.stream()
                    .filter(c -> c.getId().equals(mainCategoryId)
                            || mainCategoryId.equals(c.getParentId()))
                    .map(DivisionCategory::getId)
                    .toList();

            var count = entryRepository.countByDivisionIdAndUserIdAndInitialCategoryIdInAndStatusNot(
                    divisionId, userId, mainCategoryGroupIds, EntryStatus.WITHDRAWN);
            if (count >= division.getMaxEntriesPerMainCategory()) {
                throw new IllegalArgumentException(
                        "Entry limit reached for this main category (max "
                        + division.getMaxEntriesPerMainCategory() + ")");
            }
        }
    }

    private String generateEntryCode(UUID divisionId) {
        for (int attempt = 0; attempt < 10; attempt++) {
            var sb = new StringBuilder(ENTRY_CODE_LENGTH);
            for (int i = 0; i < ENTRY_CODE_LENGTH; i++) {
                sb.append(ENTRY_CODE_CHARS.charAt(RANDOM.nextInt(ENTRY_CODE_CHARS.length())));
            }
            var code = sb.toString();
            if (!entryRepository.existsByDivisionIdAndEntryCode(divisionId, code)) {
                return code;
            }
        }
        throw new IllegalStateException("Unable to generate a unique entry code");
    }
}
