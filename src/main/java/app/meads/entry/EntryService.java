package app.meads.entry;

import app.meads.competition.CompetitionRole;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.entry.internal.EntryCreditRepository;
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

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Validated
public class EntryService {

    private final ProductMappingRepository productMappingRepository;
    private final EntryCreditRepository creditRepository;
    private final CompetitionService competitionService;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    EntryService(ProductMappingRepository productMappingRepository,
                 EntryCreditRepository creditRepository,
                 CompetitionService competitionService,
                 UserService userService,
                 ApplicationEventPublisher eventPublisher) {
        this.productMappingRepository = productMappingRepository;
        this.creditRepository = creditRepository;
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
}
