package app.meads.entry;

import app.meads.BusinessRuleException;
import app.meads.competition.CompetitionRole;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import app.meads.competition.DivisionStatus;
import app.meads.entry.internal.EntryCreditRepository;
import app.meads.entry.internal.EntryRepository;
import app.meads.entry.internal.JumpsellerOrderLineItemRepository;
import app.meads.entry.internal.JumpsellerOrderRepository;
import app.meads.entry.internal.ProductMappingRepository;
import app.meads.identity.Role;
import app.meads.identity.UserService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
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
    private final JumpsellerOrderRepository orderRepository;
    private final JumpsellerOrderLineItemRepository lineItemRepository;
    private final CompetitionService competitionService;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    EntryService(ProductMappingRepository productMappingRepository,
                 EntryCreditRepository creditRepository,
                 EntryRepository entryRepository,
                 JumpsellerOrderRepository orderRepository,
                 JumpsellerOrderLineItemRepository lineItemRepository,
                 CompetitionService competitionService,
                 UserService userService,
                 ApplicationEventPublisher eventPublisher) {
        this.productMappingRepository = productMappingRepository;
        this.creditRepository = creditRepository;
        this.entryRepository = entryRepository;
        this.orderRepository = orderRepository;
        this.lineItemRepository = lineItemRepository;
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
            throw new BusinessRuleException("error.product.already-mapped");
        }
        var mapping = new ProductMapping(divisionId, jumpsellerProductId, jumpsellerSku,
                productName, creditsPerUnit);
        log.info("Created product mapping: productId={}, division={}, credits={}",
                jumpsellerProductId, divisionId, creditsPerUnit);
        return productMappingRepository.save(mapping);
    }

    public ProductMapping updateProductMapping(@NotNull UUID mappingId,
                                                @NotBlank String productName,
                                                int creditsPerUnit,
                                                @NotNull UUID requestingUserId) {
        var mapping = productMappingRepository.findById(mappingId)
                .orElseThrow(() -> new BusinessRuleException("error.product.not-found"));
        requireAuthorizedForDivision(mapping.getDivisionId(), requestingUserId);
        mapping.updateDetails(productName, creditsPerUnit);
        log.debug("Updated product mapping: {}", mappingId);
        return productMappingRepository.save(mapping);
    }

    public void removeProductMapping(@NotNull UUID mappingId,
                                      @NotNull UUID requestingUserId) {
        var mapping = productMappingRepository.findById(mappingId)
                .orElseThrow(() -> new BusinessRuleException("error.product.not-found"));
        requireAuthorizedForDivision(mapping.getDivisionId(), requestingUserId);
        productMappingRepository.delete(mapping);
        log.info("Removed product mapping: {}", mappingId);
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
            throw new BusinessRuleException("error.credits.mutual-exclusivity");
        }

        // Role compatibility check
        if (competitionService.hasIncompatibleRolesForEntrant(
                division.getCompetitionId(), user.getId())) {
            throw new BusinessRuleException("error.credits.incompatible-role");
        }

        var credit = new EntryCredit(divisionId, user.getId(), amount,
                "ADMIN", userService.findById(requestingUserId).getEmail());
        creditRepository.save(credit);
        log.info("Added {} credits: division={}, user={}", amount, divisionId, user.getEmail());

        // Add ENTRANT participant at competition level (idempotent)
        competitionService.ensureEntrantParticipant(
                division.getCompetitionId(), user.getId());

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
            throw new BusinessRuleException("error.credits.insufficient-balance", balance, amount);
        }
        var activeEntries = entryRepository.countByDivisionIdAndUserIdAndStatusNot(
                divisionId, userId, EntryStatus.WITHDRAWN);
        if (balance - amount < activeEntries) {
            throw new BusinessRuleException("error.credits.balance-below-entries", balance - amount, activeEntries);
        }
        var credit = new EntryCredit(divisionId, userId, -amount,
                "ADMIN", userService.findById(requestingUserId).getEmail());
        creditRepository.save(credit);
        log.info("Removed {} credits: division={}, userId={}", amount, divisionId, userId);
    }

    public int getTotalCreditBalance(@NotNull UUID divisionId) {
        return creditRepository.sumAmountByDivisionId(divisionId);
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
            throw new BusinessRuleException("error.entry.division-not-open");
        }

        // Credit check: must have available credits
        var creditBalance = creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId);
        var activeEntries = entryRepository.countByDivisionIdAndUserIdAndStatusNot(
                divisionId, userId, EntryStatus.WITHDRAWN);
        if (creditBalance <= activeEntries) {
            throw new BusinessRuleException("error.entry.no-credits", creditBalance, activeEntries);
        }

        // Entry limits
        checkEntryLimits(divisionId, userId, initialCategoryId, division);

        // Generate entry number and code
        var entryNumber = entryRepository.findMaxEntryNumberByDivisionId(divisionId) + 1;
        var entryCode = generateEntryCode(divisionId);

        var entry = new Entry(divisionId, userId, entryNumber, entryCode,
                meadName, initialCategoryId, sweetness, abv, carbonation,
                honeyVarieties, otherIngredients, woodAged, woodAgeingDetails,
                additionalInformation);
        var saved = entryRepository.save(entry);
        log.info("Created entry: #{} (code={}, mead={}, division={}, userId={})",
                entryNumber, entryCode, meadName, divisionId, userId);
        return saved;
    }

    public Entry updateEntry(@NotNull UUID entryId,
                              @NotNull UUID userId,
                              @NotBlank String meadName,
                              @NotNull UUID initialCategoryId,
                              @NotNull Sweetness sweetness,
                              @NotNull BigDecimal abv,
                              @NotNull Carbonation carbonation,
                              @NotBlank String honeyVarieties,
                              String otherIngredients,
                              boolean woodAged,
                              String woodAgeingDetails,
                              String additionalInformation) {
        var entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new BusinessRuleException("error.entry.not-found"));
        if (!entry.getUserId().equals(userId)) {
            throw new BusinessRuleException("error.entry.not-owner");
        }
        entry.updateDetails(meadName, initialCategoryId, sweetness, abv,
                carbonation, honeyVarieties, otherIngredients, woodAged,
                woodAgeingDetails, additionalInformation);
        log.debug("Updated entry: {} (mead={})", entryId, meadName);
        return entryRepository.save(entry);
    }

    public void deleteEntry(@NotNull UUID entryId, @NotNull UUID userId) {
        var entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new BusinessRuleException("error.entry.not-found"));
        if (!entry.getUserId().equals(userId)) {
            throw new BusinessRuleException("error.entry.not-owner");
        }
        if (entry.getStatus() != EntryStatus.DRAFT) {
            throw new BusinessRuleException("error.entry.wrong-status");
        }
        entryRepository.delete(entry);
        log.info("Deleted entry: #{} ({})", entry.getEntryNumber(), entryId);
    }

    public void submitEntry(@NotNull UUID entryId, @NotNull UUID userId) {
        var entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new BusinessRuleException("error.entry.not-found"));
        if (!entry.getUserId().equals(userId)) {
            throw new BusinessRuleException("error.entry.not-owner");
        }
        entry.submit();
        entryRepository.save(entry);
        log.info("Submitted entry: #{} ({})", entry.getEntryNumber(), entryId);
        publishSubmissionEventIfComplete(entry.getDivisionId(), userId);
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
        log.info("Submitted {} draft entries: division={}, userId={}", drafts.size(), divisionId, userId);
        publishSubmissionEventIfComplete(divisionId, userId);
    }

    public Entry advanceEntryStatus(@NotNull UUID entryId, @NotNull UUID requestingUserId) {
        var entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new BusinessRuleException("error.entry.not-found"));
        requireAuthorizedForDivision(entry.getDivisionId(), requestingUserId);
        entry.advanceStatus();
        log.info("Advanced entry status to {}: #{} ({})", entry.getStatus(), entry.getEntryNumber(), entryId);
        return entryRepository.save(entry);
    }

    public Entry revertEntryStatus(@NotNull UUID entryId, @NotNull UUID requestingUserId) {
        var entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new BusinessRuleException("error.entry.not-found"));
        requireAuthorizedForDivision(entry.getDivisionId(), requestingUserId);
        entry.revertStatus();
        log.info("Reverted entry status to {}: #{} ({})", entry.getStatus(), entry.getEntryNumber(), entryId);
        return entryRepository.save(entry);
    }

    public Entry markReceived(@NotNull UUID entryId, @NotNull UUID requestingUserId) {
        var entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new BusinessRuleException("error.entry.not-found"));
        requireAuthorizedForDivision(entry.getDivisionId(), requestingUserId);
        entry.markReceived();
        log.info("Marked entry received: #{} ({})", entry.getEntryNumber(), entryId);
        return entryRepository.save(entry);
    }

    public Entry withdrawEntry(@NotNull UUID entryId, @NotNull UUID requestingUserId) {
        var entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new BusinessRuleException("error.entry.not-found"));
        requireAuthorizedForDivision(entry.getDivisionId(), requestingUserId);
        entry.withdraw();
        log.info("Withdrew entry: #{} ({})", entry.getEntryNumber(), entryId);
        return entryRepository.save(entry);
    }

    public Entry adminUpdateEntry(@NotNull UUID entryId,
                                    @NotBlank String meadName,
                                    @NotNull UUID initialCategoryId,
                                    @NotNull Sweetness sweetness,
                                    @NotNull BigDecimal abv,
                                    @NotNull Carbonation carbonation,
                                    @NotBlank String honeyVarieties,
                                    String otherIngredients,
                                    boolean woodAged,
                                    String woodAgeingDetails,
                                    String additionalInformation,
                                    @NotNull UUID requestingUserId) {
        var entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new BusinessRuleException("error.entry.not-found"));
        requireAuthorizedForDivision(entry.getDivisionId(), requestingUserId);
        entry.adminUpdateDetails(meadName, initialCategoryId, sweetness, abv,
                carbonation, honeyVarieties, otherIngredients, woodAged,
                woodAgeingDetails, additionalInformation);
        log.debug("Admin updated entry: #{} ({})", entry.getEntryNumber(), entryId);
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
                .orElseThrow(() -> new BusinessRuleException("error.entry.not-found"));
    }

    public long countActiveEntries(@NotNull UUID divisionId, @NotNull UUID userId) {
        return entryRepository.countByDivisionIdAndUserIdAndStatusNot(
                divisionId, userId, EntryStatus.WITHDRAWN);
    }

    // --- Entrant overview methods ---

    public List<EntrantDivisionOverview> findEntrantDivisionOverviews(@NotNull UUID userId) {
        var divisionIds = creditRepository.findDistinctDivisionIdsByUserId(userId);
        return divisionIds.stream()
                .map(divisionId -> {
                    var division = competitionService.findDivisionById(divisionId);
                    var competition = competitionService.findCompetitionById(
                            division.getCompetitionId());
                    var creditBalance = creditRepository.sumAmountByDivisionIdAndUserId(
                            divisionId, userId);
                    var entryCount = entryRepository.countByDivisionIdAndUserIdAndStatusNot(
                            divisionId, userId, EntryStatus.WITHDRAWN);
                    return new EntrantDivisionOverview(
                            competition.getId(), competition.getName(),
                            competition.getShortName(),
                            divisionId, division.getName(), division.getShortName(),
                            creditBalance, entryCount);
                })
                .toList();
    }

    // --- Order methods ---

    public List<JumpsellerOrder> findOrdersByDivision(@NotNull UUID divisionId) {
        var lineItems = lineItemRepository.findByDivisionId(divisionId);
        var orderIds = lineItems.stream()
                .map(JumpsellerOrderLineItem::getOrderId)
                .distinct()
                .toList();
        return orderRepository.findAllById(orderIds);
    }

    public List<JumpsellerOrderLineItem> findLineItemsByDivision(@NotNull UUID divisionId) {
        return lineItemRepository.findByDivisionId(divisionId);
    }

    public JumpsellerOrder updateOrderAdminDetails(@NotNull UUID orderId,
                                                     @NotNull OrderStatus status,
                                                     String adminNote,
                                                     @NotNull UUID requestingUserId) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessRuleException("error.order.not-found"));
        var divisionId = lineItemRepository.findByOrderId(orderId).stream()
                .map(JumpsellerOrderLineItem::getDivisionId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (divisionId != null) {
            requireAuthorizedForDivision(divisionId, requestingUserId);
        } else {
            var user = userService.findById(requestingUserId);
            if (user.getRole() != Role.SYSTEM_ADMIN) {
                throw new BusinessRuleException("error.auth.unauthorized");
            }
        }
        order.updateAdminDetails(status, adminNote);
        log.debug("Updated order admin details: {} (status={})", orderId, status);
        return orderRepository.save(order);
    }

    // --- Private helpers ---

    private void requireAuthorizedForDivision(UUID divisionId, UUID userId) {
        var user = userService.findById(userId);
        if (user.getRole() == Role.SYSTEM_ADMIN) {
            return;
        }
        if (!competitionService.isAuthorizedForDivision(divisionId, userId)) {
            throw new BusinessRuleException("error.auth.unauthorized");
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
        // Total limit
        if (division.getMaxEntriesTotal() != null) {
            var totalCount = entryRepository.countByDivisionIdAndUserIdAndStatusNot(
                    divisionId, userId, EntryStatus.WITHDRAWN);
            if (totalCount >= division.getMaxEntriesTotal()) {
                throw new BusinessRuleException("error.entry.limit-total", division.getMaxEntriesTotal());
            }
        }

        // Subcategory limit
        if (division.getMaxEntriesPerSubcategory() != null) {
            var count = entryRepository.countByDivisionIdAndUserIdAndInitialCategoryIdAndStatusNot(
                    divisionId, userId, initialCategoryId, EntryStatus.WITHDRAWN);
            if (count >= division.getMaxEntriesPerSubcategory()) {
                throw new BusinessRuleException("error.entry.limit-subcategory", division.getMaxEntriesPerSubcategory());
            }
        }

        // Main category limit
        if (division.getMaxEntriesPerMainCategory() != null) {
            var categories = competitionService.findDivisionCategories(divisionId);
            var category = categories.stream()
                    .filter(c -> c.getId().equals(initialCategoryId))
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException("error.category.not-found"));

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
                throw new BusinessRuleException("error.entry.limit-main-category", division.getMaxEntriesPerMainCategory());
            }
        }
    }

    private void publishSubmissionEventIfComplete(UUID divisionId, UUID userId) {
        var creditBalance = creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId);
        var activeEntries = entryRepository.countByDivisionIdAndUserIdAndStatusNot(
                divisionId, userId, EntryStatus.WITHDRAWN);
        if (creditBalance - activeEntries > 0) {
            log.debug("Submission event skipped: {} credits remaining (division={}, userId={})",
                    creditBalance - activeEntries, divisionId, userId);
            return;
        }
        var remainingDrafts = entryRepository.findByDivisionIdAndUserIdAndStatus(
                divisionId, userId, EntryStatus.DRAFT);
        if (!remainingDrafts.isEmpty()) {
            log.debug("Submission event skipped: {} drafts remain (division={}, userId={})",
                    remainingDrafts.size(), divisionId, userId);
            return;
        }
        var submittedEntries = entryRepository.findByDivisionIdAndUserIdAndStatus(
                divisionId, userId, EntryStatus.SUBMITTED);
        if (submittedEntries.isEmpty()) {
            log.debug("Submission event skipped: no submitted entries (division={}, userId={})",
                    divisionId, userId);
            return;
        }
        var categories = competitionService.findDivisionCategories(divisionId).stream()
                .collect(Collectors.toMap(DivisionCategory::getId, Function.identity()));
        var entryDetails = submittedEntries.stream()
                .map(entry -> {
                    var cat = categories.get(entry.getInitialCategoryId());
                    return new EntryDetail(
                            entry.getEntryNumber(), entry.getMeadName(),
                            cat != null ? cat.getCode() : "—",
                            cat != null ? cat.getName() : "Unknown");
                })
                .toList();
        eventPublisher.publishEvent(new EntriesSubmittedEvent(divisionId, userId, entryDetails));
        log.info("Published EntriesSubmittedEvent: division={}, userId={}, entries={}",
                divisionId, userId, entryDetails.size());
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
        throw new BusinessRuleException("error.entry.code-generation-failed");
    }
}
