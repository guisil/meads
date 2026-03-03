package app.meads.entry;

import app.meads.competition.CompetitionService;
import app.meads.entry.internal.ProductMappingRepository;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserService;
import app.meads.identity.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class EntryServiceTest {

    @InjectMocks
    EntryService entryService;

    @Mock
    ProductMappingRepository productMappingRepository;

    @Mock
    CompetitionService competitionService;

    @Mock
    UserService userService;

    private User createSystemAdmin() {
        return new User("admin@test.com", "Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
    }

    // --- Product Mapping tests ---

    @Test
    void shouldCreateProductMapping() {
        var divisionId = UUID.randomUUID();
        var adminUser = createSystemAdmin();
        given(userService.findById(adminUser.getId())).willReturn(adminUser);
        given(productMappingRepository.existsByDivisionIdAndJumpsellerProductId(
                divisionId, "PROD-001")).willReturn(false);
        given(productMappingRepository.save(any(ProductMapping.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        var result = entryService.createProductMapping(
                divisionId, "PROD-001", "SKU-001", "Mead Entry Pack", 1, adminUser.getId());

        assertThat(result).isNotNull();
        assertThat(result.getDivisionId()).isEqualTo(divisionId);
        assertThat(result.getJumpsellerProductId()).isEqualTo("PROD-001");
        assertThat(result.getJumpsellerSku()).isEqualTo("SKU-001");
        assertThat(result.getProductName()).isEqualTo("Mead Entry Pack");
        assertThat(result.getCreditsPerUnit()).isEqualTo(1);
    }

    @Test
    void shouldRejectDuplicateProductMapping() {
        var divisionId = UUID.randomUUID();
        var adminUser = createSystemAdmin();
        given(userService.findById(adminUser.getId())).willReturn(adminUser);
        given(productMappingRepository.existsByDivisionIdAndJumpsellerProductId(
                divisionId, "PROD-001")).willReturn(true);

        assertThatThrownBy(() -> entryService.createProductMapping(
                divisionId, "PROD-001", "SKU-001", "Mead Entry Pack", 1, adminUser.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already mapped");
    }

    @Test
    void shouldRejectCreateProductMappingWhenNotAuthorized() {
        var divisionId = UUID.randomUUID();
        var regularUser = new User("user@test.com", "User", UserStatus.ACTIVE, Role.USER);
        given(userService.findById(regularUser.getId())).willReturn(regularUser);
        given(competitionService.isAuthorizedForDivision(divisionId, regularUser.getId()))
                .willReturn(false);

        assertThatThrownBy(() -> entryService.createProductMapping(
                divisionId, "PROD-001", "SKU-001", "Mead Entry Pack", 1, regularUser.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not authorized");
    }

    @Test
    void shouldUpdateProductMapping() {
        var mapping = new ProductMapping(UUID.randomUUID(), "PROD-001", "SKU-001",
                "Old Name", 1);
        var adminUser = createSystemAdmin();
        given(userService.findById(adminUser.getId())).willReturn(adminUser);
        given(productMappingRepository.findById(mapping.getId()))
                .willReturn(Optional.of(mapping));
        given(productMappingRepository.save(any(ProductMapping.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        var result = entryService.updateProductMapping(
                mapping.getId(), "New Name", 3, adminUser.getId());

        assertThat(result.getProductName()).isEqualTo("New Name");
        assertThat(result.getCreditsPerUnit()).isEqualTo(3);
    }

    @Test
    void shouldRejectUpdateProductMappingWhenNotFound() {
        var mappingId = UUID.randomUUID();
        var adminUser = createSystemAdmin();
        given(productMappingRepository.findById(mappingId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> entryService.updateProductMapping(
                mappingId, "New Name", 3, adminUser.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product mapping not found");
    }

    @Test
    void shouldRemoveProductMapping() {
        var mapping = new ProductMapping(UUID.randomUUID(), "PROD-001", "SKU-001",
                "Mead Entry Pack", 1);
        var adminUser = createSystemAdmin();
        given(userService.findById(adminUser.getId())).willReturn(adminUser);
        given(productMappingRepository.findById(mapping.getId()))
                .willReturn(Optional.of(mapping));

        entryService.removeProductMapping(mapping.getId(), adminUser.getId());

        then(productMappingRepository).should().delete(mapping);
    }

    @Test
    void shouldFindProductMappingsByDivision() {
        var divisionId = UUID.randomUUID();
        var mapping1 = new ProductMapping(divisionId, "PROD-001", "SKU-001",
                "Entry Pack", 1);
        var mapping2 = new ProductMapping(divisionId, "PROD-002", "SKU-002",
                "Entry Pack x3", 3);
        given(productMappingRepository.findByDivisionId(divisionId))
                .willReturn(List.of(mapping1, mapping2));

        var result = entryService.findProductMappings(divisionId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ProductMapping::getJumpsellerProductId)
                .containsExactly("PROD-001", "PROD-002");
    }

    @Test
    void shouldFindProductMappingsByProductId() {
        var mapping = new ProductMapping(UUID.randomUUID(), "PROD-001", "SKU-001",
                "Entry Pack", 1);
        given(productMappingRepository.findByJumpsellerProductId("PROD-001"))
                .willReturn(List.of(mapping));

        var result = entryService.findProductMappingsByProductId("PROD-001");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getJumpsellerProductId()).isEqualTo("PROD-001");
    }
}
