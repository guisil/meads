package app.meads.entry;

import app.meads.competition.Competition;
import app.meads.competition.CompetitionRole;
import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.DivisionCategory;
import app.meads.competition.ScoringSystem;
import app.meads.entry.internal.EntryCreditRepository;
import app.meads.entry.internal.EntryRepository;
import app.meads.entry.internal.JumpsellerOrderLineItemRepository;
import app.meads.entry.internal.JumpsellerOrderRepository;
import app.meads.entry.internal.ProductMappingRepository;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserService;
import app.meads.identity.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class EntryServiceTest {

    @InjectMocks
    EntryService entryService;

    @Mock
    ProductMappingRepository productMappingRepository;

    @Mock
    EntryCreditRepository creditRepository;

    @Mock
    CompetitionService competitionService;

    @Mock
    UserService userService;

    @Mock
    EntryRepository entryRepository;

    @Mock
    JumpsellerOrderRepository orderRepository;

    @Mock
    JumpsellerOrderLineItemRepository lineItemRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    private User createSystemAdmin() {
        return new User("admin@test.com", "Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
    }

    private Division createRegistrationOpenDivision(UUID competitionId) {
        var division = new Division(competitionId, "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        division.advanceStatus(); // DRAFT → REGISTRATION_OPEN
        return division;
    }

    private Division createRegistrationOpenDivisionWithLimits(UUID competitionId,
                                                               Integer maxPerSubcategory,
                                                               Integer maxPerMainCategory,
                                                               Integer maxTotal) {
        var division = new Division(competitionId, "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        division.updateEntryLimits(maxPerSubcategory, maxPerMainCategory, maxTotal);
        division.advanceStatus(); // DRAFT → REGISTRATION_OPEN
        return division;
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
        given(productMappingRepository.findById(mappingId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> entryService.updateProductMapping(
                mappingId, "New Name", 3, UUID.randomUUID()))
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

    // --- Credit tests ---

    @Test
    void shouldGetCreditBalance() {
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        given(creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId))
                .willReturn(5);

        var balance = entryService.getCreditBalance(divisionId, userId);

        assertThat(balance).isEqualTo(5);
    }

    @Test
    void shouldAddCreditsWithMutualExclusivityCheck() {
        var competitionId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var division = new Division(competitionId, "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var adminUser = createSystemAdmin();
        var entrant = new User("entrant@test.com", "Entrant", UserStatus.ACTIVE, Role.USER);

        given(userService.findById(adminUser.getId())).willReturn(adminUser);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(userService.findOrCreateByEmail("entrant@test.com")).willReturn(entrant);
        given(creditRepository.findDistinctDivisionIdsByUserId(entrant.getId()))
                .willReturn(List.of());
        given(creditRepository.save(any(EntryCredit.class)))
                .willAnswer(inv -> inv.getArgument(0));

        entryService.addCredits(divisionId, "entrant@test.com", 3, adminUser.getId());

        var creditCaptor = ArgumentCaptor.forClass(EntryCredit.class);
        then(creditRepository).should().save(creditCaptor.capture());
        assertThat(creditCaptor.getValue().getAmount()).isEqualTo(3);
        assertThat(creditCaptor.getValue().getDivisionId()).isEqualTo(divisionId);
        assertThat(creditCaptor.getValue().getUserId()).isEqualTo(entrant.getId());
        assertThat(creditCaptor.getValue().getSourceType()).isEqualTo("ADMIN");

        // Should publish event
        then(eventPublisher).should().publishEvent(any(CreditsAwardedEvent.class));
    }

    @Test
    void shouldRejectAddCreditsWhenMutualExclusivityViolated() {
        var competitionId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var otherDivisionId = UUID.randomUUID();
        var division = new Division(competitionId, "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var otherDivision = new Division(competitionId, "Pro", "pro", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var adminUser = createSystemAdmin();
        var entrant = new User("entrant@test.com", "Entrant", UserStatus.ACTIVE, Role.USER);

        given(userService.findById(adminUser.getId())).willReturn(adminUser);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(userService.findOrCreateByEmail("entrant@test.com")).willReturn(entrant);
        given(creditRepository.findDistinctDivisionIdsByUserId(entrant.getId()))
                .willReturn(List.of(otherDivision.getId()));
        given(competitionService.findDivisionsByCompetition(competitionId))
                .willReturn(List.of(division, otherDivision));

        assertThatThrownBy(() -> entryService.addCredits(
                divisionId, "entrant@test.com", 3, adminUser.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutual exclusivity");
    }

    @Test
    void shouldAddEntrantParticipantWhenAddingCredits() {
        var competitionId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var division = new Division(competitionId, "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var adminUser = createSystemAdmin();
        var entrant = new User("entrant@test.com", "Entrant", UserStatus.ACTIVE, Role.USER);

        given(userService.findById(adminUser.getId())).willReturn(adminUser);
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(userService.findOrCreateByEmail("entrant@test.com")).willReturn(entrant);
        given(creditRepository.findDistinctDivisionIdsByUserId(entrant.getId()))
                .willReturn(List.of());
        given(creditRepository.save(any(EntryCredit.class)))
                .willAnswer(inv -> inv.getArgument(0));

        entryService.addCredits(divisionId, "entrant@test.com", 1, adminUser.getId());

        // Should add ENTRANT participant at competition level (idempotent)
        then(competitionService).should().ensureEntrantParticipant(
                eq(competitionId), eq(entrant.getId()));
    }

    @Test
    void shouldRemoveCreditsWhenBalanceSufficient() {
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var adminUser = createSystemAdmin();

        given(userService.findById(adminUser.getId())).willReturn(adminUser);
        given(creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId))
                .willReturn(5);
        given(creditRepository.save(any(EntryCredit.class)))
                .willAnswer(inv -> inv.getArgument(0));

        entryService.removeCredits(divisionId, userId, 3, adminUser.getId());

        var creditCaptor = ArgumentCaptor.forClass(EntryCredit.class);
        then(creditRepository).should().save(creditCaptor.capture());
        assertThat(creditCaptor.getValue().getAmount()).isEqualTo(-3);
        assertThat(creditCaptor.getValue().getSourceType()).isEqualTo("ADMIN");
    }

    @Test
    void shouldRejectRemoveCreditsWhenInsufficientBalance() {
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var adminUser = createSystemAdmin();

        given(userService.findById(adminUser.getId())).willReturn(adminUser);
        given(creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId))
                .willReturn(2);

        assertThatThrownBy(() -> entryService.removeCredits(
                divisionId, userId, 5, adminUser.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient credit balance");
    }

    @Test
    void shouldCheckHasCreditsInOtherDivision() {
        var competitionId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var otherDivisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var otherDivision = new Division(competitionId, "Pro", "pro", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var division = new Division(competitionId, "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");

        given(creditRepository.findDistinctDivisionIdsByUserId(userId))
                .willReturn(List.of(otherDivision.getId()));
        given(competitionService.findDivisionsByCompetition(competitionId))
                .willReturn(List.of(division, otherDivision));

        var result = entryService.hasCreditsInOtherDivision(competitionId, divisionId, userId);

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenNoCreditsInOtherDivision() {
        var competitionId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();

        given(creditRepository.findDistinctDivisionIdsByUserId(userId))
                .willReturn(List.of());

        var result = entryService.hasCreditsInOtherDivision(competitionId, divisionId, userId);

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenCreditsOnlyInSameDivision() {
        var competitionId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var division = new Division(competitionId, "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");

        given(creditRepository.findDistinctDivisionIdsByUserId(userId))
                .willReturn(List.of(divisionId));

        var result = entryService.hasCreditsInOtherDivision(competitionId, divisionId, userId);

        assertThat(result).isFalse();
    }

    // --- Entry tests (Phase 6) ---

    // Cycle 1: createEntry validates credits > active entries

    @Test
    void shouldCreateEntryWhenCreditsAvailable() {
        var competitionId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var division = createRegistrationOpenDivision(competitionId);

        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId)).willReturn(3);
        given(entryRepository.countByDivisionIdAndUserIdAndStatusNot(
                divisionId, userId, EntryStatus.WITHDRAWN)).willReturn(2L);
        given(entryRepository.findMaxEntryNumberByDivisionId(divisionId)).willReturn(0);
        given(entryRepository.existsByDivisionIdAndEntryCode(eq(divisionId), anyString()))
                .willReturn(false);
        given(entryRepository.save(any(Entry.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = entryService.createEntry(divisionId, userId, "My Mead", categoryId,
                Sweetness.DRY, Strength.STANDARD, new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(EntryStatus.DRAFT);
        assertThat(result.getDivisionId()).isEqualTo(divisionId);
        assertThat(result.getUserId()).isEqualTo(userId);
    }

    @Test
    void shouldRejectCreateEntryWhenNoCreditsAvailable() {
        var competitionId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var division = createRegistrationOpenDivision(competitionId);

        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId)).willReturn(2);
        given(entryRepository.countByDivisionIdAndUserIdAndStatusNot(
                divisionId, userId, EntryStatus.WITHDRAWN)).willReturn(2L);

        assertThatThrownBy(() -> entryService.createEntry(divisionId, userId,
                "My Mead", categoryId, Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credit");
    }

    // Cycle 2: createEntry rejects if division not REGISTRATION_OPEN

    @Test
    void shouldRejectCreateEntryWhenDivisionNotOpen() {
        var competitionId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var division = new Division(competitionId, "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC"); // DRAFT

        given(competitionService.findDivisionById(divisionId)).willReturn(division);

        assertThatThrownBy(() -> entryService.createEntry(divisionId, userId,
                "My Mead", categoryId, Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not open for registration");
    }

    // Cycle 3: createEntry sequential entry number

    @Test
    void shouldAssignSequentialEntryNumber() {
        var competitionId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var division = createRegistrationOpenDivision(competitionId);

        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId)).willReturn(5);
        given(entryRepository.countByDivisionIdAndUserIdAndStatusNot(
                divisionId, userId, EntryStatus.WITHDRAWN)).willReturn(0L);
        given(entryRepository.findMaxEntryNumberByDivisionId(divisionId)).willReturn(7);
        given(entryRepository.existsByDivisionIdAndEntryCode(eq(divisionId), anyString()))
                .willReturn(false);
        given(entryRepository.save(any(Entry.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = entryService.createEntry(divisionId, userId, "My Mead", categoryId,
                Sweetness.DRY, Strength.STANDARD, new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null);

        assertThat(result.getEntryNumber()).isEqualTo(8);
    }

    // Cycle 4: createEntry generates unique entry code

    @Test
    void shouldGenerateUniqueEntryCode() {
        var competitionId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var division = createRegistrationOpenDivision(competitionId);

        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId)).willReturn(1);
        given(entryRepository.countByDivisionIdAndUserIdAndStatusNot(
                divisionId, userId, EntryStatus.WITHDRAWN)).willReturn(0L);
        given(entryRepository.findMaxEntryNumberByDivisionId(divisionId)).willReturn(0);
        given(entryRepository.existsByDivisionIdAndEntryCode(eq(divisionId), anyString()))
                .willReturn(false);
        given(entryRepository.save(any(Entry.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = entryService.createEntry(divisionId, userId, "My Mead", categoryId,
                Sweetness.DRY, Strength.STANDARD, new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null);

        assertThat(result.getEntryCode()).hasSize(6);
        assertThat(result.getEntryCode()).matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}");
    }

    // Cycle 5: createEntry rejects if subcategory limit exceeded

    @Test
    void shouldRejectCreateEntryWhenSubcategoryLimitExceeded() {
        var competitionId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var division = createRegistrationOpenDivisionWithLimits(competitionId, 2, null, null);

        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId)).willReturn(5);
        given(entryRepository.countByDivisionIdAndUserIdAndStatusNot(
                divisionId, userId, EntryStatus.WITHDRAWN)).willReturn(2L);
        given(entryRepository.countByDivisionIdAndUserIdAndInitialCategoryIdAndStatusNot(
                divisionId, userId, categoryId, EntryStatus.WITHDRAWN)).willReturn(2L);

        assertThatThrownBy(() -> entryService.createEntry(divisionId, userId,
                "My Mead", categoryId, Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subcategory");
    }

    // Cycle 6: createEntry rejects if main category limit exceeded

    @Test
    void shouldRejectCreateEntryWhenMainCategoryLimitExceeded() {
        var competitionId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var division = createRegistrationOpenDivisionWithLimits(competitionId, null, 3, null);

        // Create category hierarchy: M2 (parent) → M2A, M2B (subcategories)
        var parentCategory = new DivisionCategory(divisionId, null, "M2",
                "Fruit Mead", "Fruit mead category", null, 1);
        var subCategoryA = new DivisionCategory(divisionId, null, "M2A",
                "Melomel", "Melomel description", parentCategory.getId(), 2);
        var subCategoryB = new DivisionCategory(divisionId, null, "M2B",
                "Cyser", "Cyser description", parentCategory.getId(), 3);

        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId)).willReturn(10);
        given(entryRepository.countByDivisionIdAndUserIdAndStatusNot(
                divisionId, userId, EntryStatus.WITHDRAWN)).willReturn(3L);
        given(competitionService.findDivisionCategories(divisionId))
                .willReturn(List.of(parentCategory, subCategoryA, subCategoryB));
        given(entryRepository.countByDivisionIdAndUserIdAndInitialCategoryIdInAndStatusNot(
                eq(divisionId), eq(userId),
                eq(List.of(parentCategory.getId(), subCategoryA.getId(), subCategoryB.getId())),
                eq(EntryStatus.WITHDRAWN))).willReturn(3L);

        assertThatThrownBy(() -> entryService.createEntry(divisionId, userId,
                "My Mead", subCategoryA.getId(), Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("main category");
    }

    @Test
    void shouldRejectCreateEntryWhenTotalLimitExceeded() {
        var competitionId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var division = createRegistrationOpenDivisionWithLimits(competitionId, null, null, 5);

        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId)).willReturn(10);
        given(entryRepository.countByDivisionIdAndUserIdAndStatusNot(
                divisionId, userId, EntryStatus.WITHDRAWN)).willReturn(5L);

        assertThatThrownBy(() -> entryService.createEntry(divisionId, userId,
                "My Mead", categoryId, Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("total");
    }

    // Cycle 7: createEntry allows entry when limits are null (unlimited)

    @Test
    void shouldAllowEntryWhenLimitsAreNull() {
        var competitionId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var division = createRegistrationOpenDivision(competitionId);
        // limits are null by default (unlimited)

        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId)).willReturn(10);
        given(entryRepository.countByDivisionIdAndUserIdAndStatusNot(
                divisionId, userId, EntryStatus.WITHDRAWN)).willReturn(5L);
        given(entryRepository.findMaxEntryNumberByDivisionId(divisionId)).willReturn(5);
        given(entryRepository.existsByDivisionIdAndEntryCode(eq(divisionId), anyString()))
                .willReturn(false);
        given(entryRepository.save(any(Entry.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = entryService.createEntry(divisionId, userId, "My Mead", categoryId,
                Sweetness.DRY, Strength.STANDARD, new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null);

        assertThat(result).isNotNull();
        assertThat(result.getEntryNumber()).isEqualTo(6);
    }

    // Cycle 8: updateEntry — only owning user, only DRAFT

    @Test
    void shouldUpdateEntryWhenOwnerAndDraft() {
        var userId = UUID.randomUUID();
        var entry = new Entry(UUID.randomUUID(), userId, 1, "ABC123",
                "Old Mead", UUID.randomUUID(), Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null);
        var newCategoryId = UUID.randomUUID();

        given(entryRepository.findById(entry.getId())).willReturn(Optional.of(entry));
        given(entryRepository.save(any(Entry.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = entryService.updateEntry(entry.getId(), userId, "New Mead",
                newCategoryId, Sweetness.SWEET, Strength.SACK, new BigDecimal("18.0"),
                Carbonation.SPARKLING, "Orange blossom", "Spices",
                true, "Oak barrel", "Notes");

        assertThat(result.getMeadName()).isEqualTo("New Mead");
        assertThat(result.getInitialCategoryId()).isEqualTo(newCategoryId);
    }

    @Test
    void shouldRejectUpdateEntryWhenNotOwner() {
        var userId = UUID.randomUUID();
        var otherUserId = UUID.randomUUID();
        var entry = new Entry(UUID.randomUUID(), userId, 1, "ABC123",
                "Old Mead", UUID.randomUUID(), Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null);

        given(entryRepository.findById(entry.getId())).willReturn(Optional.of(entry));

        assertThatThrownBy(() -> entryService.updateEntry(entry.getId(), otherUserId,
                "New Mead", UUID.randomUUID(), Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not the owner");
    }

    // Cycle 9: deleteEntry — only DRAFT

    @Test
    void shouldDeleteDraftEntry() {
        var userId = UUID.randomUUID();
        var entry = new Entry(UUID.randomUUID(), userId, 1, "ABC123",
                "My Mead", UUID.randomUUID(), Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null);

        given(entryRepository.findById(entry.getId())).willReturn(Optional.of(entry));

        entryService.deleteEntry(entry.getId(), userId);

        then(entryRepository).should().delete(entry);
    }

    @Test
    void shouldRejectDeleteEntryWhenNotDraft() {
        var userId = UUID.randomUUID();
        var entry = new Entry(UUID.randomUUID(), userId, 1, "ABC123",
                "My Mead", UUID.randomUUID(), Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null);
        entry.submit(); // SUBMITTED

        given(entryRepository.findById(entry.getId())).willReturn(Optional.of(entry));

        assertThatThrownBy(() -> entryService.deleteEntry(entry.getId(), userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    void shouldRejectDeleteEntryWhenNotOwner() {
        var userId = UUID.randomUUID();
        var otherUserId = UUID.randomUUID();
        var entry = new Entry(UUID.randomUUID(), userId, 1, "ABC123",
                "My Mead", UUID.randomUUID(), Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null);

        given(entryRepository.findById(entry.getId())).willReturn(Optional.of(entry));

        assertThatThrownBy(() -> entryService.deleteEntry(entry.getId(), otherUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not the owner");
    }

    // Cycle 10: submitAllDrafts — batch submit + event

    @Test
    void shouldSubmitAllDraftsAndPublishEvent() {
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var entry1 = new Entry(divisionId, userId, 1, "ABC123",
                "Mead One", categoryId, Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null);
        var entry2 = new Entry(divisionId, userId, 2, "DEF456",
                "Mead Two", categoryId, Sweetness.SWEET, Strength.SACK,
                new BigDecimal("18.0"), Carbonation.SPARKLING,
                "Orange blossom", null, false, null, null);

        // First call returns drafts for the loop, second call (from helper) returns empty
        given(entryRepository.findByDivisionIdAndUserIdAndStatus(
                divisionId, userId, EntryStatus.DRAFT))
                .willReturn(List.of(entry1, entry2))
                .willReturn(List.of());
        // 2 credits, 2 active → 0 remaining
        given(creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId)).willReturn(2);
        given(entryRepository.countByDivisionIdAndUserIdAndStatusNot(
                divisionId, userId, EntryStatus.WITHDRAWN)).willReturn(2L);
        // Submitted entries for details
        given(entryRepository.findByDivisionIdAndUserIdAndStatus(
                divisionId, userId, EntryStatus.SUBMITTED))
                .willReturn(List.of(entry1, entry2));
        // Category resolution
        var category = mock(DivisionCategory.class);
        given(category.getId()).willReturn(categoryId);
        given(category.getCode()).willReturn("M1A");
        given(category.getName()).willReturn("Dry Mead");
        given(competitionService.findDivisionCategories(divisionId))
                .willReturn(List.of(category));

        entryService.submitAllDrafts(divisionId, userId);

        assertThat(entry1.getStatus()).isEqualTo(EntryStatus.SUBMITTED);
        assertThat(entry2.getStatus()).isEqualTo(EntryStatus.SUBMITTED);
        then(entryRepository).should().save(entry1);
        then(entryRepository).should().save(entry2);

        var eventCaptor = ArgumentCaptor.forClass(EntriesSubmittedEvent.class);
        then(eventPublisher).should().publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().divisionId()).isEqualTo(divisionId);
        assertThat(eventCaptor.getValue().userId()).isEqualTo(userId);
        assertThat(eventCaptor.getValue().entryDetails()).hasSize(2);
    }

    // Cycle 11: submitAllDrafts — no drafts = no-op

    @Test
    void shouldNotPublishEventWhenNoDraftsToSubmit() {
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();

        given(entryRepository.findByDivisionIdAndUserIdAndStatus(
                divisionId, userId, EntryStatus.DRAFT))
                .willReturn(List.of());

        entryService.submitAllDrafts(divisionId, userId);

        then(eventPublisher).should(never()).publishEvent(any(EntriesSubmittedEvent.class));
    }

    // Cycle 18: submitEntry — conditional event (credits remain)

    @Test
    void shouldNotPublishEventWhenCreditsRemain() {
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var entry = new Entry(divisionId, userId, 1, "ABC123",
                "My Mead", UUID.randomUUID(), Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null);

        given(entryRepository.findById(entry.getId())).willReturn(Optional.of(entry));
        given(entryRepository.save(any(Entry.class)))
                .willAnswer(inv -> inv.getArgument(0));
        // 3 credits, 1 active entry after submit → 2 remaining
        given(creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId)).willReturn(3);
        given(entryRepository.countByDivisionIdAndUserIdAndStatusNot(
                divisionId, userId, EntryStatus.WITHDRAWN)).willReturn(1L);

        entryService.submitEntry(entry.getId(), userId);

        assertThat(entry.getStatus()).isEqualTo(EntryStatus.SUBMITTED);
        then(eventPublisher).should(never()).publishEvent(any(EntriesSubmittedEvent.class));
    }

    @Test
    void shouldNotPublishEventWhenDraftsRemain() {
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var entry = new Entry(divisionId, userId, 1, "ABC123",
                "My Mead", UUID.randomUUID(), Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null);
        var draftEntry = new Entry(divisionId, userId, 2, "DEF456",
                "Other Mead", UUID.randomUUID(), Sweetness.SWEET, Strength.SACK,
                new BigDecimal("14.0"), Carbonation.SPARKLING,
                "Orange blossom", null, false, null, null);

        given(entryRepository.findById(entry.getId())).willReturn(Optional.of(entry));
        given(entryRepository.save(any(Entry.class)))
                .willAnswer(inv -> inv.getArgument(0));
        // 2 credits, 2 active entries → 0 remaining credits
        given(creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId)).willReturn(2);
        given(entryRepository.countByDivisionIdAndUserIdAndStatusNot(
                divisionId, userId, EntryStatus.WITHDRAWN)).willReturn(2L);
        // But there's still a draft entry
        given(entryRepository.findByDivisionIdAndUserIdAndStatus(
                divisionId, userId, EntryStatus.DRAFT))
                .willReturn(List.of(draftEntry));

        entryService.submitEntry(entry.getId(), userId);

        assertThat(entry.getStatus()).isEqualTo(EntryStatus.SUBMITTED);
        then(eventPublisher).should(never()).publishEvent(any(EntriesSubmittedEvent.class));
    }

    @Test
    void shouldPublishEventWithDetailsWhenAllComplete() {
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var entry = new Entry(divisionId, userId, 1, "ABC123",
                "My Mead", categoryId, Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null);

        given(entryRepository.findById(entry.getId())).willReturn(Optional.of(entry));
        given(entryRepository.save(any(Entry.class)))
                .willAnswer(inv -> inv.getArgument(0));
        // 1 credit, 1 active entry → 0 remaining
        given(creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId)).willReturn(1);
        given(entryRepository.countByDivisionIdAndUserIdAndStatusNot(
                divisionId, userId, EntryStatus.WITHDRAWN)).willReturn(1L);
        // No drafts remain
        given(entryRepository.findByDivisionIdAndUserIdAndStatus(
                divisionId, userId, EntryStatus.DRAFT))
                .willReturn(List.of());
        // The submitted entry
        given(entryRepository.findByDivisionIdAndUserIdAndStatus(
                divisionId, userId, EntryStatus.SUBMITTED))
                .willReturn(List.of(entry));
        // Category resolution
        var category = mock(DivisionCategory.class);
        given(category.getId()).willReturn(categoryId);
        given(category.getCode()).willReturn("M1A");
        given(category.getName()).willReturn("Traditional Mead (Dry)");
        given(competitionService.findDivisionCategories(divisionId))
                .willReturn(List.of(category));

        entryService.submitEntry(entry.getId(), userId);

        var eventCaptor = ArgumentCaptor.forClass(EntriesSubmittedEvent.class);
        then(eventPublisher).should().publishEvent(eventCaptor.capture());
        var event = eventCaptor.getValue();
        assertThat(event.divisionId()).isEqualTo(divisionId);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.entryDetails()).hasSize(1);
        assertThat(event.entryDetails().getFirst().entryNumber()).isEqualTo(1);
        assertThat(event.entryDetails().getFirst().meadName()).isEqualTo("My Mead");
        assertThat(event.entryDetails().getFirst().categoryCode()).isEqualTo("M1A");
    }

    @Test
    void shouldNotPublishEventWhenNoSubmittedEntries() {
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var entry = new Entry(divisionId, userId, 1, "ABC123",
                "My Mead", UUID.randomUUID(), Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null);

        given(entryRepository.findById(entry.getId())).willReturn(Optional.of(entry));
        given(entryRepository.save(any(Entry.class)))
                .willAnswer(inv -> inv.getArgument(0));
        // 1 credit, 1 active entry → 0 remaining
        given(creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId)).willReturn(1);
        given(entryRepository.countByDivisionIdAndUserIdAndStatusNot(
                divisionId, userId, EntryStatus.WITHDRAWN)).willReturn(1L);
        // No drafts
        given(entryRepository.findByDivisionIdAndUserIdAndStatus(
                divisionId, userId, EntryStatus.DRAFT))
                .willReturn(List.of());
        // No submitted entries (edge case)
        given(entryRepository.findByDivisionIdAndUserIdAndStatus(
                divisionId, userId, EntryStatus.SUBMITTED))
                .willReturn(List.of());

        entryService.submitEntry(entry.getId(), userId);

        then(eventPublisher).should(never()).publishEvent(any(EntriesSubmittedEvent.class));
    }

    @Test
    void shouldPublishEventWhenLastSingleEntryCompletes() {
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        // This is the last DRAFT entry — 2 others already submitted
        var lastEntry = new Entry(divisionId, userId, 3, "GHI789",
                "Third Mead", categoryId, Sweetness.SWEET, Strength.SACK,
                new BigDecimal("16.0"), Carbonation.SPARKLING,
                "Manuka honey", null, false, null, null);
        var submitted1 = new Entry(divisionId, userId, 1, "ABC123",
                "First Mead", categoryId, Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null);
        submitted1.submit();
        var submitted2 = new Entry(divisionId, userId, 2, "DEF456",
                "Second Mead", categoryId, Sweetness.MEDIUM, Strength.STANDARD,
                new BigDecimal("13.0"), Carbonation.PETILLANT,
                "Clover honey", null, false, null, null);
        submitted2.submit();

        given(entryRepository.findById(lastEntry.getId())).willReturn(Optional.of(lastEntry));
        given(entryRepository.save(any(Entry.class)))
                .willAnswer(inv -> inv.getArgument(0));
        // 3 credits, 3 active entries → 0 remaining
        given(creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId)).willReturn(3);
        given(entryRepository.countByDivisionIdAndUserIdAndStatusNot(
                divisionId, userId, EntryStatus.WITHDRAWN)).willReturn(3L);
        // No drafts remain after this submit
        given(entryRepository.findByDivisionIdAndUserIdAndStatus(
                divisionId, userId, EntryStatus.DRAFT))
                .willReturn(List.of());
        // All 3 entries are submitted
        given(entryRepository.findByDivisionIdAndUserIdAndStatus(
                divisionId, userId, EntryStatus.SUBMITTED))
                .willReturn(List.of(submitted1, submitted2, lastEntry));
        // Category resolution
        var category = mock(DivisionCategory.class);
        given(category.getId()).willReturn(categoryId);
        given(category.getCode()).willReturn("M2C");
        given(category.getName()).willReturn("Berry Melomel");
        given(competitionService.findDivisionCategories(divisionId))
                .willReturn(List.of(category));

        entryService.submitEntry(lastEntry.getId(), userId);

        var eventCaptor = ArgumentCaptor.forClass(EntriesSubmittedEvent.class);
        then(eventPublisher).should().publishEvent(eventCaptor.capture());
        var event = eventCaptor.getValue();
        assertThat(event.entryDetails()).hasSize(3);
    }

    // Cycle 12: markReceived — admin only

    @Test
    void shouldMarkEntryAsReceived() {
        var divisionId = UUID.randomUUID();
        var adminUser = createSystemAdmin();
        var entry = new Entry(divisionId, UUID.randomUUID(), 1, "ABC123",
                "My Mead", UUID.randomUUID(), Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null);
        entry.submit(); // SUBMITTED

        given(userService.findById(adminUser.getId())).willReturn(adminUser);
        given(entryRepository.findById(entry.getId())).willReturn(Optional.of(entry));
        given(entryRepository.save(any(Entry.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = entryService.markReceived(entry.getId(), adminUser.getId());

        assertThat(result.getStatus()).isEqualTo(EntryStatus.RECEIVED);
    }

    // Cycle 13: withdrawEntry — admin only

    @Test
    void shouldWithdrawEntry() {
        var divisionId = UUID.randomUUID();
        var adminUser = createSystemAdmin();
        var entry = new Entry(divisionId, UUID.randomUUID(), 1, "ABC123",
                "My Mead", UUID.randomUUID(), Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null);
        entry.submit(); // SUBMITTED

        given(userService.findById(adminUser.getId())).willReturn(adminUser);
        given(entryRepository.findById(entry.getId())).willReturn(Optional.of(entry));
        given(entryRepository.save(any(Entry.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = entryService.withdrawEntry(entry.getId(), adminUser.getId());

        assertThat(result.getStatus()).isEqualTo(EntryStatus.WITHDRAWN);
    }

    // Cycle 14: adminUpdateEntry — any non-WITHDRAWN

    @Test
    void shouldAdminUpdateSubmittedEntry() {
        var divisionId = UUID.randomUUID();
        var adminUser = createSystemAdmin();
        var newCategoryId = UUID.randomUUID();
        var entry = new Entry(divisionId, UUID.randomUUID(), 1, "ABC123",
                "Old Mead", UUID.randomUUID(), Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null);
        entry.submit(); // SUBMITTED

        given(userService.findById(adminUser.getId())).willReturn(adminUser);
        given(entryRepository.findById(entry.getId())).willReturn(Optional.of(entry));
        given(entryRepository.save(any(Entry.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = entryService.adminUpdateEntry(entry.getId(), "Admin Mead",
                newCategoryId, Sweetness.SWEET, Strength.SACK, new BigDecimal("18.0"),
                Carbonation.SPARKLING, "Orange blossom", "Spices",
                true, "Oak barrel", "Notes", adminUser.getId());

        assertThat(result.getMeadName()).isEqualTo("Admin Mead");
        assertThat(result.getInitialCategoryId()).isEqualTo(newCategoryId);
    }

    // Cycle 15: findEntriesByDivisionAndUser

    @Test
    void shouldFindEntriesByDivisionAndUser() {
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var entry = new Entry(divisionId, userId, 1, "ABC123",
                "My Mead", UUID.randomUUID(), Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null);

        given(entryRepository.findByDivisionIdAndUserId(divisionId, userId))
                .willReturn(List.of(entry));

        var result = entryService.findEntriesByDivisionAndUser(divisionId, userId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getMeadName()).isEqualTo("My Mead");
    }

    // Cycle 16: countActiveEntries

    @Test
    void shouldCountActiveEntries() {
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();

        given(entryRepository.countByDivisionIdAndUserIdAndStatusNot(
                divisionId, userId, EntryStatus.WITHDRAWN)).willReturn(3L);

        var count = entryService.countActiveEntries(divisionId, userId);

        assertThat(count).isEqualTo(3);
    }

    // Cycle 17: deleteEntry frees credit slot (verified by successful createEntry after delete)

    @Test
    void shouldFreeCreditSlotAfterDeletion() {
        var competitionId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var division = createRegistrationOpenDivision(competitionId);

        // Entry to delete
        var entry = new Entry(divisionId, userId, 1, "ABC123",
                "My Mead", categoryId, Sweetness.DRY, Strength.STANDARD,
                new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null);

        given(entryRepository.findById(entry.getId())).willReturn(Optional.of(entry));

        // Delete the entry
        entryService.deleteEntry(entry.getId(), userId);
        then(entryRepository).should().delete(entry);

        // Now createEntry should work (2 credits, 1 active entry after deletion)
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(creditRepository.sumAmountByDivisionIdAndUserId(divisionId, userId)).willReturn(2);
        given(entryRepository.countByDivisionIdAndUserIdAndStatusNot(
                divisionId, userId, EntryStatus.WITHDRAWN)).willReturn(1L);
        given(entryRepository.findMaxEntryNumberByDivisionId(divisionId)).willReturn(1);
        given(entryRepository.existsByDivisionIdAndEntryCode(eq(divisionId), anyString()))
                .willReturn(false);
        given(entryRepository.save(any(Entry.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var newEntry = entryService.createEntry(divisionId, userId, "New Mead", categoryId,
                Sweetness.DRY, Strength.STANDARD, new BigDecimal("12.5"), Carbonation.STILL,
                "Wildflower honey", null, false, null, null);

        assertThat(newEntry).isNotNull();
        assertThat(newEntry.getEntryNumber()).isEqualTo(2);
    }

    // --- Entrant overview tests ---

    @Test
    void shouldReturnEntrantDivisionOverviews() {
        var userId = UUID.randomUUID();
        var competition = new Competition("CHIP 2026", "chip-2026",
                LocalDate.of(2026, 6, 11), LocalDate.of(2026, 6, 14), "Amarante");
        var division = new Division(competition.getId(), "Amadora", "amadora", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");

        given(creditRepository.findDistinctDivisionIdsByUserId(userId))
                .willReturn(List.of(division.getId()));
        given(competitionService.findDivisionById(division.getId()))
                .willReturn(division);
        given(competitionService.findCompetitionById(competition.getId()))
                .willReturn(competition);
        given(creditRepository.sumAmountByDivisionIdAndUserId(division.getId(), userId))
                .willReturn(3);
        given(entryRepository.countByDivisionIdAndUserIdAndStatusNot(
                division.getId(), userId, EntryStatus.WITHDRAWN))
                .willReturn(1L);

        var overviews = entryService.findEntrantDivisionOverviews(userId);

        assertThat(overviews).hasSize(1);
        var overview = overviews.getFirst();
        assertThat(overview.competitionName()).isEqualTo("CHIP 2026");
        assertThat(overview.competitionShortName()).isEqualTo("chip-2026");
        assertThat(overview.divisionName()).isEqualTo("Amadora");
        assertThat(overview.divisionShortName()).isEqualTo("amadora");
        assertThat(overview.creditBalance()).isEqualTo(3);
        assertThat(overview.activeEntryCount()).isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyOverviewsWhenNoCredits() {
        var userId = UUID.randomUUID();
        given(creditRepository.findDistinctDivisionIdsByUserId(userId))
                .willReturn(List.of());

        var overviews = entryService.findEntrantDivisionOverviews(userId);

        assertThat(overviews).isEmpty();
    }
}
