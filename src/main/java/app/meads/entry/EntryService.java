package app.meads.entry;

import app.meads.competition.CompetitionService;
import app.meads.entry.internal.ProductMappingRepository;
import app.meads.identity.Role;
import app.meads.identity.UserService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    private final CompetitionService competitionService;
    private final UserService userService;

    EntryService(ProductMappingRepository productMappingRepository,
                 CompetitionService competitionService,
                 UserService userService) {
        this.productMappingRepository = productMappingRepository;
        this.competitionService = competitionService;
        this.userService = userService;
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
}
