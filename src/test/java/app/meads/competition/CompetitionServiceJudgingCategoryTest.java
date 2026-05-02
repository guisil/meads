package app.meads.competition;

import app.meads.BusinessRuleException;
import app.meads.competition.internal.CategoryRepository;
import app.meads.competition.internal.CompetitionDocumentRepository;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.DivisionCategoryRepository;
import app.meads.competition.internal.DivisionRepository;
import app.meads.competition.internal.ParticipantRepository;
import app.meads.competition.internal.ParticipantRoleRepository;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserService;
import app.meads.identity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CompetitionServiceJudgingCategoryTest {

    CompetitionService competitionService;

    @Mock CompetitionRepository competitionRepository;
    @Mock DivisionRepository divisionRepository;
    @Mock ParticipantRepository participantRepository;
    @Mock ParticipantRoleRepository participantRoleRepository;
    @Mock DivisionCategoryRepository divisionCategoryRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock CompetitionDocumentRepository competitionDocumentRepository;
    @Mock UserService userService;
    @Mock ApplicationEventPublisher eventPublisher;

    List<DivisionRevertGuard> revertGuards = new ArrayList<>();
    List<DivisionDeletionGuard> deletionGuards = new ArrayList<>();
    List<ParticipantRemovalCleanup> removalCleanups = new ArrayList<>();
    List<JudgingCategoryDeletionGuard> judgingCategoryDeletionGuards = new ArrayList<>();

    @BeforeEach
    void setUp() {
        competitionService = new CompetitionService(
                competitionRepository, divisionRepository,
                participantRepository, participantRoleRepository,
                divisionCategoryRepository, categoryRepository,
                competitionDocumentRepository, userService,
                eventPublisher, revertGuards, deletionGuards, removalCleanups,
                judgingCategoryDeletionGuards);
    }

    private User createAdmin() {
        return new User("admin@example.com", "Admin", UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
    }

    private Division createDivisionAtStatus(UUID competitionId, DivisionStatus targetStatus) {
        var division = new Division(competitionId, "Amadores", "amadores",
                ScoringSystem.MJP, LocalDateTime.of(2026, 6, 15, 23, 59), "UTC");
        int steps = targetStatus.ordinal() - DivisionStatus.DRAFT.ordinal();
        for (int i = 0; i < steps; i++) {
            division.advanceStatus();
        }
        return division;
    }

    private DivisionCategory registrationCategory(UUID divisionId, String code, UUID parentId) {
        return new DivisionCategory(divisionId, null, code, code + " Name", code + " desc",
                parentId, 0, CategoryScope.REGISTRATION);
    }

    // --- initializeJudgingCategories ---

    @Test
    void shouldInitializeJudgingCategoriesFromRegistrationCategories() {
        var admin = createAdmin();
        var competitionId = UUID.randomUUID();
        var division = createDivisionAtStatus(competitionId, DivisionStatus.REGISTRATION_CLOSED);

        var mainCat = registrationCategory(division.getId(), "M1", null);
        var subCat = registrationCategory(division.getId(), "M1A", mainCat.getId());

        given(divisionRepository.findById(division.getId())).willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionCategoryRepository.findByDivisionIdAndScopeOrderByCode(
                division.getId(), CategoryScope.JUDGING)).willReturn(List.of());
        given(divisionCategoryRepository.findByDivisionIdAndScopeOrderByCode(
                division.getId(), CategoryScope.REGISTRATION)).willReturn(List.of(mainCat, subCat));
        given(divisionCategoryRepository.save(any(DivisionCategory.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.initializeJudgingCategories(division.getId(), admin.getId());

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(dc -> dc.getScope() == CategoryScope.JUDGING);
        assertThat(result).allMatch(dc -> dc.getCatalogCategoryId() == null);
        assertThat(result.stream().map(DivisionCategory::getCode))
                .containsExactlyInAnyOrder("M1", "M1A");
    }

    @Test
    void shouldRejectInitializeJudgingCategoriesWhenAlreadyInitialized() {
        var admin = createAdmin();
        var competitionId = UUID.randomUUID();
        var division = createDivisionAtStatus(competitionId, DivisionStatus.REGISTRATION_CLOSED);
        var existing = registrationCategory(division.getId(), "M1", null);
        existing = new DivisionCategory(division.getId(), null, "M1", "M1 Name", "desc",
                null, 0, CategoryScope.JUDGING);

        given(divisionRepository.findById(division.getId())).willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionCategoryRepository.findByDivisionIdAndScopeOrderByCode(
                division.getId(), CategoryScope.JUDGING)).willReturn(List.of(existing));

        assertThatThrownBy(() ->
                competitionService.initializeJudgingCategories(division.getId(), admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.category.judging-already-initialized");
    }

    @Test
    void shouldRejectInitializeJudgingCategoriesWhenStatusNotAllowed() {
        var admin = createAdmin();
        var competitionId = UUID.randomUUID();
        var division = createDivisionAtStatus(competitionId, DivisionStatus.REGISTRATION_OPEN);

        given(divisionRepository.findById(division.getId())).willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);

        assertThatThrownBy(() ->
                competitionService.initializeJudgingCategories(division.getId(), admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.category.judging-not-allowed-status");
    }

    // --- addJudgingCategory ---

    @Test
    void shouldAddJudgingCategoryWhenStatusAllows() {
        var admin = createAdmin();
        var competitionId = UUID.randomUUID();
        var division = createDivisionAtStatus(competitionId, DivisionStatus.REGISTRATION_CLOSED);

        given(divisionRepository.findById(division.getId())).willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionCategoryRepository.existsByDivisionIdAndCodeAndScope(
                division.getId(), "CX", CategoryScope.JUDGING)).willReturn(false);
        given(divisionCategoryRepository.save(any(DivisionCategory.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addJudgingCategory(
                division.getId(), "CX", "Combined", "Combined meads", null, admin.getId());

        assertThat(result.getScope()).isEqualTo(CategoryScope.JUDGING);
        assertThat(result.getCode()).isEqualTo("CX");
        assertThat(result.getName()).isEqualTo("Combined");
        assertThat(result.getCatalogCategoryId()).isNull();
    }

    @Test
    void shouldRejectAddJudgingCategoryWhenStatusNotAllowed() {
        var admin = createAdmin();
        var competitionId = UUID.randomUUID();
        var division = createDivisionAtStatus(competitionId, DivisionStatus.DRAFT);

        given(divisionRepository.findById(division.getId())).willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);

        assertThatThrownBy(() ->
                competitionService.addJudgingCategory(
                        division.getId(), "CX", "Combined", "desc", null, admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.category.judging-not-allowed-status");
    }

    @Test
    void shouldRejectAddJudgingCategoryWhenCodeAlreadyExists() {
        var admin = createAdmin();
        var competitionId = UUID.randomUUID();
        var division = createDivisionAtStatus(competitionId, DivisionStatus.JUDGING);

        given(divisionRepository.findById(division.getId())).willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionCategoryRepository.existsByDivisionIdAndCodeAndScope(
                division.getId(), "M1", CategoryScope.JUDGING)).willReturn(true);

        assertThatThrownBy(() ->
                competitionService.addJudgingCategory(
                        division.getId(), "M1", "Traditional", "desc", null, admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.category.code-exists");
    }

    // --- updateJudgingCategory ---

    @Test
    void shouldUpdateJudgingCategoryDetails() {
        var admin = createAdmin();
        var competitionId = UUID.randomUUID();
        var division = createDivisionAtStatus(competitionId, DivisionStatus.JUDGING);
        var category = new DivisionCategory(division.getId(), null, "M1", "Old Name", "Old desc",
                null, 0, CategoryScope.JUDGING);

        given(divisionRepository.findById(division.getId())).willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionCategoryRepository.findById(category.getId())).willReturn(Optional.of(category));
        given(divisionCategoryRepository.save(any(DivisionCategory.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.updateJudgingCategory(
                division.getId(), category.getId(), "M1", "New Name", "New desc", admin.getId());

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getDescription()).isEqualTo("New desc");
    }

    @Test
    void shouldRejectUpdateJudgingCategoryWhenStatusNotAllowed() {
        var admin = createAdmin();
        var competitionId = UUID.randomUUID();
        var division = createDivisionAtStatus(competitionId, DivisionStatus.REGISTRATION_OPEN);

        given(divisionRepository.findById(division.getId())).willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);

        assertThatThrownBy(() ->
                competitionService.updateJudgingCategory(
                        division.getId(), UUID.randomUUID(), "M1", "Name", "desc", admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.category.judging-not-allowed-status");
    }

    // --- removeJudgingCategory ---

    @Test
    void shouldRemoveJudgingCategoryWhenNotReferenced() {
        var admin = createAdmin();
        var competitionId = UUID.randomUUID();
        var division = createDivisionAtStatus(competitionId, DivisionStatus.JUDGING);
        var category = new DivisionCategory(division.getId(), null, "CX", "Combined", "desc",
                null, 0, CategoryScope.JUDGING);

        given(divisionRepository.findById(division.getId())).willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionCategoryRepository.findById(category.getId())).willReturn(Optional.of(category));
        given(divisionCategoryRepository.findByParentId(category.getId())).willReturn(List.of());

        competitionService.removeJudgingCategory(division.getId(), category.getId(), admin.getId());

        then(divisionCategoryRepository).should().delete(category);
    }

    @Test
    void shouldRejectRemoveJudgingCategoryWhenGuardBlocks() {
        var admin = createAdmin();
        var competitionId = UUID.randomUUID();
        var division = createDivisionAtStatus(competitionId, DivisionStatus.JUDGING);
        var category = new DivisionCategory(division.getId(), null, "CX", "Combined", "desc",
                null, 0, CategoryScope.JUDGING);

        var guard = (JudgingCategoryDeletionGuard) categoryId ->
                { throw new BusinessRuleException("error.category.judging-referenced"); };
        judgingCategoryDeletionGuards.add(guard);

        given(divisionRepository.findById(division.getId())).willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionCategoryRepository.findById(category.getId())).willReturn(Optional.of(category));

        assertThatThrownBy(() ->
                competitionService.removeJudgingCategory(division.getId(), category.getId(), admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.category.judging-referenced");

        then(divisionCategoryRepository).should(never()).delete(any());
    }

    @Test
    void shouldRejectRemoveJudgingCategoryWhenStatusNotAllowed() {
        var admin = createAdmin();
        var competitionId = UUID.randomUUID();
        var division = createDivisionAtStatus(competitionId, DivisionStatus.REGISTRATION_OPEN);

        given(divisionRepository.findById(division.getId())).willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);

        assertThatThrownBy(() ->
                competitionService.removeJudgingCategory(division.getId(), UUID.randomUUID(), admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.category.judging-not-allowed-status");

        then(divisionCategoryRepository).should(never()).delete(any());
    }
}
