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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeast;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CompetitionServiceTest {

    CompetitionService competitionService;

    @Mock
    CompetitionRepository competitionRepository;

    @Mock
    DivisionRepository divisionRepository;

    @Mock
    ParticipantRepository participantRepository;

    @Mock
    ParticipantRoleRepository participantRoleRepository;

    @Mock
    DivisionCategoryRepository divisionCategoryRepository;

    @Mock
    CategoryRepository categoryRepository;

    @Mock
    CompetitionDocumentRepository competitionDocumentRepository;

    @Mock
    UserService userService;

    @Mock
    ApplicationEventPublisher eventPublisher;

    List<DivisionRevertGuard> revertGuards = new ArrayList<>();

    List<DivisionDeletionGuard> deletionGuards = new ArrayList<>();

    List<ParticipantRemovalCleanup> removalCleanups = new ArrayList<>();

    List<JudgingCategoryDeletionGuard> judgingCategoryDeletionGuards = new ArrayList<>();

    List<MinJudgesPerTableLockGuard> minJudgesPerTableLockGuards = new ArrayList<>();

    @BeforeEach
    void setUp() {
        competitionService = new CompetitionService(
                competitionRepository, divisionRepository,
                participantRepository, participantRoleRepository,
                divisionCategoryRepository, categoryRepository,
                competitionDocumentRepository, userService,
                eventPublisher, revertGuards, deletionGuards, removalCleanups,
                judgingCategoryDeletionGuards, minJudgesPerTableLockGuards);
    }

    private Competition createCompetition() {
        return new Competition("Test Competition", "test-competition",
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto");
    }

    private User createAdmin() {
        return new User("admin@example.com", "Admin",
                UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
    }

    private User createRegularUser() {
        return new User("user@example.com", "User",
                UserStatus.ACTIVE, Role.USER);
    }

    // --- createCompetition ---

    @Test
    void shouldCreateCompetitionWhenRequestedBySystemAdmin() {
        var admin = createAdmin();
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionRepository.save(any(Competition.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.createCompetition(
                "Regional 2026", "regional-2026", LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 6, 17), "Porto", admin.getId());

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Regional 2026");
        assertThat(result.getStartDate()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(result.getEndDate()).isEqualTo(LocalDate.of(2026, 6, 17));
        assertThat(result.getLocation()).isEqualTo("Porto");
        then(competitionRepository).should().save(any(Competition.class));
    }

    @Test
    void shouldRejectCreateCompetitionWhenUserNotSystemAdmin() {
        var user = createRegularUser();
        given(userService.findById(user.getId())).willReturn(user);

        assertThatThrownBy(() -> competitionService.createCompetition(
                "Regional 2026", "regional-2026", LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 6, 17), "Porto", user.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.auth.unauthorized");

        then(competitionRepository).should(never()).save(any());
    }

    // --- findAllCompetitions ---

    @Test
    void shouldFindAllCompetitions() {
        var competition = createCompetition();
        given(competitionRepository.findAll(any(org.springframework.data.domain.Sort.class))).willReturn(List.of(competition));

        var result = competitionService.findAllCompetitions();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo("Test Competition");
    }

    // --- updateCompetition ---

    @Test
    void shouldUpdateCompetitionWhenRequestedBySystemAdmin() {
        var admin = createAdmin();
        var competition = createCompetition();
        given(competitionRepository.findById(competition.getId())).willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionRepository.save(any(Competition.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.updateCompetition(
                competition.getId(), "Updated Name", "updated-name", LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 3), "Lisbon", admin.getId());

        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getStartDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(result.getLocation()).isEqualTo("Lisbon");
        then(competitionRepository).should().save(competition);
    }

    @Test
    void shouldUpdateCompetitionWhenRequestedByCompetitionAdmin() {
        var compAdmin = createRegularUser();
        var competition = createCompetition();
        var participant = new Participant(competition.getId(), compAdmin.getId());
        given(competitionRepository.findById(competition.getId())).willReturn(Optional.of(competition));
        given(userService.findById(compAdmin.getId())).willReturn(compAdmin);
        given(participantRepository.findByCompetitionIdAndUserId(
                competition.getId(), compAdmin.getId()))
                .willReturn(Optional.of(participant));
        given(participantRoleRepository.existsByParticipantIdAndRole(
                participant.getId(), CompetitionRole.ADMIN))
                .willReturn(true);
        given(competitionRepository.save(any(Competition.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.updateCompetition(
                competition.getId(), "Updated Name", "updated-name", LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 3), "Lisbon", compAdmin.getId());

        assertThat(result.getName()).isEqualTo("Updated Name");
        then(competitionRepository).should().save(competition);
    }

    @Test
    void shouldRejectUpdateCompetitionWhenUserNotAuthorized() {
        var user = createRegularUser();
        var competition = createCompetition();
        given(competitionRepository.findById(competition.getId())).willReturn(Optional.of(competition));
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.findByCompetitionIdAndUserId(competition.getId(), user.getId()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> competitionService.updateCompetition(
                competition.getId(), "Updated", "updated", LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 3), "Lisbon", user.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.auth.unauthorized");

        then(competitionRepository).should(never()).save(any());
    }

    // --- updateCompetitionLogo ---

    @Test
    void shouldUpdateCompetitionLogoWhenRequestedBySystemAdmin() {
        var admin = createAdmin();
        var competition = createCompetition();
        given(competitionRepository.findById(competition.getId())).willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionRepository.save(any(Competition.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var logo = new byte[]{1, 2, 3};
        var result = competitionService.updateCompetitionLogo(
                competition.getId(), logo, "image/png", admin.getId());

        assertThat(result.hasLogo()).isTrue();
        assertThat(result.getLogoContentType()).isEqualTo("image/png");
        then(competitionRepository).should().save(competition);
    }

    @Test
    void shouldClearCompetitionLogoWhenNullProvided() {
        var admin = createAdmin();
        var competition = createCompetition();
        competition.updateLogo(new byte[]{1, 2, 3}, "image/png");
        given(competitionRepository.findById(competition.getId())).willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionRepository.save(any(Competition.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.updateCompetitionLogo(
                competition.getId(), null, null, admin.getId());

        assertThat(result.hasLogo()).isFalse();
    }

    // --- updateCompetitionContactEmail ---

    @Test
    void shouldUpdateCompetitionContactEmail() {
        var admin = createAdmin();
        var competition = createCompetition();
        given(competitionRepository.findById(competition.getId())).willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionRepository.save(any(Competition.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.updateCompetitionContactEmail(
                competition.getId(), "organizer@example.com", admin.getId());

        assertThat(result.getContactEmail()).isEqualTo("organizer@example.com");
        then(competitionRepository).should().save(competition);
    }

    // --- updateCommentLanguages ---

    @Test
    void shouldUpdateCommentLanguagesWhenSystemAdmin() {
        var admin = createAdmin();
        var competition = createCompetition();
        given(competitionRepository.findById(competition.getId())).willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionRepository.save(any(Competition.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.updateCommentLanguages(
                competition.getId(), java.util.Set.of("pt", "en"), admin.getId());

        assertThat(result.getCommentLanguages()).containsExactlyInAnyOrder("pt", "en");
        then(competitionRepository).should().save(competition);
    }

    @Test
    void shouldRejectUpdateCommentLanguagesWhenNotAuthorized() {
        var regular = createRegularUser();
        var competition = createCompetition();
        given(competitionRepository.findById(competition.getId())).willReturn(Optional.of(competition));
        given(userService.findById(regular.getId())).willReturn(regular);
        given(participantRepository.findByCompetitionIdAndUserId(competition.getId(), regular.getId()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> competitionService.updateCommentLanguages(
                competition.getId(), java.util.Set.of("pt"), regular.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.auth.unauthorized");

        then(competitionRepository).should(never()).save(any(Competition.class));
    }

    @Test
    void shouldRejectUpdateCommentLanguagesWithInvalidCode() {
        var admin = createAdmin();
        var competition = createCompetition();
        given(competitionRepository.findById(competition.getId())).willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);

        assertThatThrownBy(() -> competitionService.updateCommentLanguages(
                competition.getId(), java.util.Set.of("INVALID!"), admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.competition.invalid-language-code");

        then(competitionRepository).should(never()).save(any(Competition.class));
    }

    @Test
    void shouldClearCommentLanguagesWithEmptySet() {
        var admin = createAdmin();
        var competition = createCompetition();
        competition.updateCommentLanguages(java.util.Set.of("pt", "en"));
        given(competitionRepository.findById(competition.getId())).willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionRepository.save(any(Competition.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.updateCommentLanguages(
                competition.getId(), java.util.Set.of(), admin.getId());

        assertThat(result.getCommentLanguages()).isEmpty();
    }

    // --- updateDivisionBosPlaces / updateDivisionMinJudgesPerTable ---

    private Division createDraftDivision(UUID competitionId) {
        return new Division(competitionId, "Amateur", "amateur",
                ScoringSystem.MJP,
                LocalDateTime.of(2026, 6, 1, 23, 59),
                "Europe/Lisbon");
    }

    @Test
    void shouldUpdateDivisionBosPlacesWhenAuthorized() {
        var admin = createAdmin();
        var competition = createCompetition();
        var division = createDraftDivision(competition.getId());
        given(divisionRepository.findById(division.getId())).willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionRepository.save(any(Division.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.updateDivisionBosPlaces(division.getId(), 3, admin.getId());

        assertThat(result.getBosPlaces()).isEqualTo(3);
    }

    @Test
    void shouldRejectUpdateBosPlacesWhenLessThanOne() {
        var admin = createAdmin();
        var competition = createCompetition();
        var division = createDraftDivision(competition.getId());
        given(divisionRepository.findById(division.getId())).willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);

        assertThatThrownBy(() -> competitionService.updateDivisionBosPlaces(
                division.getId(), 0, admin.getId()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldUpdateDivisionMinJudgesPerTableWhenNotLocked() {
        var admin = createAdmin();
        var competition = createCompetition();
        var division = createDraftDivision(competition.getId());
        var unlockedGuard = mock(MinJudgesPerTableLockGuard.class);
        given(unlockedGuard.isLocked(division.getId())).willReturn(false);
        minJudgesPerTableLockGuards.add(unlockedGuard);
        given(divisionRepository.findById(division.getId())).willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionRepository.save(any(Division.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.updateDivisionMinJudgesPerTable(
                division.getId(), 3, admin.getId());

        assertThat(result.getMinJudgesPerTable()).isEqualTo(3);
    }

    @Test
    void shouldRejectUpdateMinJudgesPerTableWhenLockedByGuard() {
        var admin = createAdmin();
        var competition = createCompetition();
        var division = createDraftDivision(competition.getId());
        var lockedGuard = mock(MinJudgesPerTableLockGuard.class);
        given(lockedGuard.isLocked(division.getId())).willReturn(true);
        minJudgesPerTableLockGuards.add(lockedGuard);
        given(divisionRepository.findById(division.getId())).willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);

        assertThatThrownBy(() -> competitionService.updateDivisionMinJudgesPerTable(
                division.getId(), 3, admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.division.min-judges-locked");

        then(divisionRepository).should(never()).save(any(Division.class));
    }

    @Test
    void shouldReportMinJudgesLockedWhenAnyGuardSaysSo() {
        var divisionId = UUID.randomUUID();
        var unlocked = mock(MinJudgesPerTableLockGuard.class);
        given(unlocked.isLocked(divisionId)).willReturn(false);
        var locked = mock(MinJudgesPerTableLockGuard.class);
        given(locked.isLocked(divisionId)).willReturn(true);
        minJudgesPerTableLockGuards.add(unlocked);
        minJudgesPerTableLockGuards.add(locked);

        assertThat(competitionService.isMinJudgesPerTableLocked(divisionId)).isTrue();
    }

    @Test
    void shouldReportMinJudgesUnlockedWhenAllGuardsAgree() {
        var divisionId = UUID.randomUUID();
        var unlocked = mock(MinJudgesPerTableLockGuard.class);
        given(unlocked.isLocked(divisionId)).willReturn(false);
        minJudgesPerTableLockGuards.add(unlocked);

        assertThat(competitionService.isMinJudgesPerTableLocked(divisionId)).isFalse();
    }

    // --- updateCompetitionShippingDetails ---

    @Test
    void shouldUpdateCompetitionShippingDetails() {
        var admin = createAdmin();
        var competition = createCompetition();
        given(competitionRepository.findById(competition.getId())).willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionRepository.save(any(Competition.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.updateCompetitionShippingDetails(
                competition.getId(), "123 Main St", "+1-555-0123", "https://chip.pt", admin.getId());

        assertThat(result.getShippingAddress()).isEqualTo("123 Main St");
        assertThat(result.getPhoneNumber()).isEqualTo("+1-555-0123");
        assertThat(result.getWebsite()).isEqualTo("https://chip.pt");
        then(competitionRepository).should().save(competition);
    }

    // --- deleteCompetition ---

    @Test
    void shouldDeleteCompetitionWhenNoDivisionsExist() {
        var admin = createAdmin();
        var competition = createCompetition();
        given(competitionRepository.findById(competition.getId())).willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionRepository.findByCompetitionId(competition.getId())).willReturn(List.of());
        given(competitionDocumentRepository.findByCompetitionIdOrderByDisplayOrder(competition.getId()))
                .willReturn(List.of());

        competitionService.deleteCompetition(competition.getId(), admin.getId());

        then(competitionDocumentRepository).should().deleteAll(List.of());
        then(competitionRepository).should().delete(competition);
    }

    @Test
    void shouldDeleteCompetitionAndCleanUpParticipants() {
        var admin = createAdmin();
        var competition = createCompetition();
        var userId = UUID.randomUUID();
        var participant = new Participant(competition.getId(), userId);
        var role = new ParticipantRole(participant.getId(), CompetitionRole.JUDGE);
        given(competitionRepository.findById(competition.getId())).willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionRepository.findByCompetitionId(competition.getId())).willReturn(List.of());
        given(competitionDocumentRepository.findByCompetitionIdOrderByDisplayOrder(competition.getId()))
                .willReturn(List.of());
        given(participantRepository.findByCompetitionId(competition.getId()))
                .willReturn(List.of(participant));
        given(participantRoleRepository.findByParticipantId(participant.getId()))
                .willReturn(List.of(role));

        competitionService.deleteCompetition(competition.getId(), admin.getId());

        then(participantRoleRepository).should().deleteAll(List.of(role));
        then(participantRepository).should().deleteAll(List.of(participant));
        then(competitionRepository).should().delete(competition);
    }

    @Test
    void shouldRejectDeleteCompetitionWhenDivisionsExist() {
        var admin = createAdmin();
        var competition = createCompetition();
        var division = new Division(competition.getId(),
                "Home", "home", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        given(competitionRepository.findById(competition.getId())).willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionRepository.findByCompetitionId(competition.getId()))
                .willReturn(List.of(division));

        assertThatThrownBy(() -> competitionService.deleteCompetition(
                competition.getId(), admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.competition.has-divisions");

        then(competitionRepository).should(never()).delete(any());
    }

    // --- createDivision ---

    @Test
    void shouldCreateDivisionWhenRequestedBySystemAdmin() {
        var competition = createCompetition();
        var admin = createAdmin();
        given(competitionRepository.findById(competition.getId())).willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionRepository.save(any(Division.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.createDivision(
                competition.getId(), "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC", admin.getId());

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Home");
        assertThat(result.getCompetitionId()).isEqualTo(competition.getId());
        assertThat(result.getStatus()).isEqualTo(DivisionStatus.DRAFT);
        assertThat(result.getScoringSystem()).isEqualTo(ScoringSystem.MJP);
        then(divisionRepository).should().save(any(Division.class));
    }

    @Test
    void shouldInitializeCategoriesOnDivisionCreation() {
        var competition = createCompetition();
        var admin = createAdmin();
        var mainCat = org.mockito.Mockito.mock(Category.class);
        var sub1 = org.mockito.Mockito.mock(Category.class);
        var sub2 = org.mockito.Mockito.mock(Category.class);
        given(mainCat.getId()).willReturn(UUID.randomUUID());
        given(mainCat.getCode()).willReturn("M1");
        given(mainCat.getName()).willReturn("Traditional Mead");
        given(mainCat.getDescription()).willReturn("Traditional mead category");
        given(mainCat.getParentCode()).willReturn(null);
        given(sub1.getId()).willReturn(UUID.randomUUID());
        given(sub1.getCode()).willReturn("M1A");
        given(sub1.getName()).willReturn("Traditional Mead (Dry)");
        given(sub1.getDescription()).willReturn("Traditional mead, dry");
        given(sub1.getParentCode()).willReturn("M1");
        given(sub2.getId()).willReturn(UUID.randomUUID());
        given(sub2.getCode()).willReturn("M1B");
        given(sub2.getName()).willReturn("Traditional Mead (Medium)");
        given(sub2.getDescription()).willReturn("Traditional mead, semi-sweet");
        given(sub2.getParentCode()).willReturn("M1");
        given(competitionRepository.findById(competition.getId())).willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionRepository.save(any(Division.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(categoryRepository.findByScoringSystemOrderByCode(ScoringSystem.MJP))
                .willReturn(List.of(mainCat, sub1, sub2));
        given(divisionCategoryRepository.save(any(DivisionCategory.class)))
                .willAnswer(inv -> inv.getArgument(0));

        competitionService.createDivision(
                competition.getId(), "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC", admin.getId());

        then(divisionCategoryRepository).should(org.mockito.Mockito.times(3))
                .save(any(DivisionCategory.class));
    }

    @Test
    void shouldRejectCreateDivisionWhenCompetitionNotFound() {
        var competitionId = UUID.randomUUID();
        var admin = createAdmin();
        given(competitionRepository.findById(competitionId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> competitionService.createDivision(
                competitionId, "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC", admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.competition.not-found");

        then(divisionRepository).should(never()).save(any());
    }

    @Test
    void shouldRejectCreateDivisionWhenUserNotAuthorized() {
        var competition = createCompetition();
        var user = createRegularUser();
        given(competitionRepository.findById(competition.getId())).willReturn(Optional.of(competition));
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.findByCompetitionIdAndUserId(competition.getId(), user.getId()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> competitionService.createDivision(
                competition.getId(), "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC", user.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.auth.unauthorized");

        then(divisionRepository).should(never()).save(any());
    }

    @Test
    void shouldAllowCompetitionAdminToCreateDivision() {
        var competition = createCompetition();
        var compAdmin = createRegularUser();
        var participant = new Participant(competition.getId(), compAdmin.getId());
        given(competitionRepository.findById(competition.getId())).willReturn(Optional.of(competition));
        given(userService.findById(compAdmin.getId())).willReturn(compAdmin);
        given(participantRepository.findByCompetitionIdAndUserId(
                competition.getId(), compAdmin.getId()))
                .willReturn(Optional.of(participant));
        given(participantRoleRepository.existsByParticipantIdAndRole(
                participant.getId(), CompetitionRole.ADMIN))
                .willReturn(true);
        given(divisionRepository.save(any(Division.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.createDivision(
                competition.getId(), "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC", compAdmin.getId());

        assertThat(result.getName()).isEqualTo("Home");
    }

    // --- advanceDivisionStatus ---

    @Test
    void shouldAdvanceDivisionStatusAndPublishEvent() {
        var admin = createAdmin();
        var division = new Division(UUID.randomUUID(),
                "Home", "home", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionRepository.save(any(Division.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.advanceDivisionStatus(division.getId(), admin.getId());

        assertThat(result.getStatus()).isEqualTo(DivisionStatus.REGISTRATION_OPEN);
        then(divisionRepository).should().save(division);
        then(eventPublisher).should().publishEvent(any(DivisionStatusAdvancedEvent.class));
    }

    @Test
    void shouldRejectAdvanceDivisionStatusWhenUserNotAuthorized() {
        var user = createRegularUser();
        var division = new Division(UUID.randomUUID(),
                "Home", "home", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.findByCompetitionIdAndUserId(
                division.getCompetitionId(), user.getId()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> competitionService.advanceDivisionStatus(
                division.getId(), user.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.auth.unauthorized");

        then(divisionRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    void shouldAllowCompetitionAdminToAdvanceDivisionStatus() {
        var compAdmin = createRegularUser();
        var competitionId = UUID.randomUUID();
        var division = new Division(competitionId, "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var participant = new Participant(competitionId, compAdmin.getId());
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(compAdmin.getId())).willReturn(compAdmin);
        given(participantRepository.findByCompetitionIdAndUserId(competitionId, compAdmin.getId()))
                .willReturn(Optional.of(participant));
        given(participantRoleRepository.existsByParticipantIdAndRole(
                participant.getId(), CompetitionRole.ADMIN))
                .willReturn(true);
        given(divisionRepository.save(any(Division.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.advanceDivisionStatus(
                division.getId(), compAdmin.getId());

        assertThat(result.getStatus()).isEqualTo(DivisionStatus.REGISTRATION_OPEN);
        then(eventPublisher).should().publishEvent(any(DivisionStatusAdvancedEvent.class));
    }

    // --- revertDivisionStatus ---

    @Test
    void shouldRevertDivisionStatusOneStepBack() {
        var admin = createAdmin();
        var division = new Division(UUID.randomUUID(),
                "Home", "home", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        division.advanceStatus(); // REGISTRATION_OPEN
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionRepository.save(any(Division.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.revertDivisionStatus(division.getId(), admin.getId());

        assertThat(result.getStatus()).isEqualTo(DivisionStatus.DRAFT);
        then(divisionRepository).should().save(division);
    }

    @Test
    void shouldRejectRevertDivisionStatusWhenUserNotAuthorized() {
        var user = createRegularUser();
        var division = new Division(UUID.randomUUID(),
                "Home", "home", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        division.advanceStatus(); // REGISTRATION_OPEN
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.findByCompetitionIdAndUserId(
                division.getCompetitionId(), user.getId()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> competitionService.revertDivisionStatus(
                division.getId(), user.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.auth.unauthorized");

        then(divisionRepository).should(never()).save(any());
    }

    @Test
    void shouldRejectRevertWhenGuardBlocks() {
        var guard = mock(DivisionRevertGuard.class);
        revertGuards.add(guard);

        var admin = createAdmin();
        var division = new Division(UUID.randomUUID(),
                "Home", "home", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        division.advanceStatus(); // REGISTRATION_OPEN
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);
        willThrow(new BusinessRuleException("Cannot revert to DRAFT: entries exist in this division"))
                .given(guard).checkRevertAllowed(division.getId(),
                        DivisionStatus.REGISTRATION_OPEN, DivisionStatus.DRAFT);

        assertThatThrownBy(() -> competitionService.revertDivisionStatus(
                division.getId(), admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("entries exist");

        then(divisionRepository).should(never()).save(any());
    }

    // --- updateDivision ---

    @Test
    void shouldUpdateDivisionWhenInDraftAndRequestedByAdmin() {
        var admin = createAdmin();
        var division = new Division(UUID.randomUUID(),
                "Home", "home", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionRepository.save(any(Division.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.updateDivision(
                division.getId(), "Updated Name", "updated-name", ScoringSystem.MJP, null, admin.getId());

        assertThat(result.getName()).isEqualTo("Updated Name");
        then(divisionRepository).should().save(division);
    }

    @Test
    void shouldRejectUpdateDivisionWhenUserNotAuthorized() {
        var user = createRegularUser();
        var division = new Division(UUID.randomUUID(),
                "Home", "home", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.findByCompetitionIdAndUserId(
                division.getCompetitionId(), user.getId()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> competitionService.updateDivision(
                division.getId(), "Updated", "updated", ScoringSystem.MJP, null, user.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.auth.unauthorized");

        then(divisionRepository).should(never()).save(any());
    }

    @Test
    void shouldAllowCompetitionAdminToUpdateDivision() {
        var compAdmin = createRegularUser();
        var competitionId = UUID.randomUUID();
        var division = new Division(competitionId, "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var participant = new Participant(competitionId, compAdmin.getId());
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(compAdmin.getId())).willReturn(compAdmin);
        given(participantRepository.findByCompetitionIdAndUserId(competitionId, compAdmin.getId()))
                .willReturn(Optional.of(participant));
        given(participantRoleRepository.existsByParticipantIdAndRole(
                participant.getId(), CompetitionRole.ADMIN))
                .willReturn(true);
        given(divisionRepository.save(any(Division.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.updateDivision(
                division.getId(), "Updated", "updated", ScoringSystem.MJP, null, compAdmin.getId());

        assertThat(result.getName()).isEqualTo("Updated");
        then(divisionRepository).should().save(division);
    }

    // --- deleteDivision ---

    @Test
    void shouldDeleteDivisionAndCleanUpCategories() {
        var admin = createAdmin();
        var division = new Division(UUID.randomUUID(),
                "Home", "home", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);

        var category = new DivisionCategory(
                division.getId(), UUID.randomUUID(), "T1", "Trad", "Trad mead", null, 0);
        given(divisionCategoryRepository.findByDivisionIdOrderByCode(division.getId()))
                .willReturn(List.of(category));

        competitionService.deleteDivision(division.getId(), admin.getId());

        then(divisionCategoryRepository).should().deleteAll(List.of(category));
        then(divisionRepository).should().delete(division);
    }

    @Test
    void shouldRejectDeleteDivisionWhenDeletionGuardBlocks() {
        var admin = createAdmin();
        var division = new Division(UUID.randomUUID(),
                "Home", "home", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);

        var guard = mock(DivisionDeletionGuard.class);
        deletionGuards.add(guard);
        willThrow(new BusinessRuleException("error.division.cannot-delete-has-entries"))
                .given(guard).checkDeletionAllowed(division.getId());

        assertThatThrownBy(() -> competitionService.deleteDivision(
                division.getId(), admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.division.cannot-delete-has-entries");

        then(divisionRepository).should(never()).delete(any());
    }

    // --- addParticipant ---

    @Test
    void shouldAddParticipantAndCreateParticipantRecord() {
        var admin = createAdmin();
        var user = createRegularUser();
        var competition = createCompetition();
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.findByCompetitionIdAndUserId(competition.getId(), user.getId()))
                .willReturn(Optional.empty());
        given(participantRepository.save(any(Participant.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(participantRoleRepository.existsByParticipantIdAndRole(
                any(), any())).willReturn(false);
        given(participantRoleRepository.save(any(ParticipantRole.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addParticipant(
                competition.getId(), user.getId(), CompetitionRole.JUDGE, admin.getId());

        assertThat(result.getRole()).isEqualTo(CompetitionRole.JUDGE);
        then(participantRepository).should().save(any(Participant.class));
        then(participantRoleRepository).should().save(any(ParticipantRole.class));
    }

    @Test
    void shouldReuseExistingParticipantWhenAdding() {
        var admin = createAdmin();
        var user = createRegularUser();
        var competition = createCompetition();
        var existingParticipant = new Participant(competition.getId(), user.getId());
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.findByCompetitionIdAndUserId(competition.getId(), user.getId()))
                .willReturn(Optional.of(existingParticipant));
        given(participantRepository.existsByAccessCode(any())).willReturn(false);
        given(participantRepository.save(any(Participant.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(participantRoleRepository.existsByParticipantIdAndRole(
                existingParticipant.getId(), CompetitionRole.JUDGE)).willReturn(false);
        given(participantRoleRepository.save(any(ParticipantRole.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addParticipant(
                competition.getId(), user.getId(), CompetitionRole.JUDGE, admin.getId());

        assertThat(result.getParticipantId()).isEqualTo(existingParticipant.getId());
        // Participant is saved now because access code is assigned for JUDGE role
        then(participantRepository).should().save(argThat(
                (Participant p) -> p.getAccessCode() != null));
    }

    @Test
    void shouldAllowAddingEntrantToExistingJudge() {
        var admin = createAdmin();
        var user = createRegularUser();
        var competition = createCompetition();
        var existingParticipant = new Participant(competition.getId(), user.getId());
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.findByCompetitionIdAndUserId(competition.getId(), user.getId()))
                .willReturn(Optional.of(existingParticipant));
        given(participantRoleRepository.existsByParticipantIdAndRole(
                existingParticipant.getId(), CompetitionRole.ENTRANT)).willReturn(false);
        given(participantRoleRepository.findByParticipantId(existingParticipant.getId()))
                .willReturn(List.of(new ParticipantRole(existingParticipant.getId(), CompetitionRole.JUDGE)));
        given(participantRoleRepository.save(any(ParticipantRole.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addParticipant(
                competition.getId(), user.getId(), CompetitionRole.ENTRANT, admin.getId());

        assertThat(result.getRole()).isEqualTo(CompetitionRole.ENTRANT);
        then(participantRoleRepository).should().save(any(ParticipantRole.class));
    }

    @Test
    void shouldAllowAddingJudgeToExistingEntrant() {
        var admin = createAdmin();
        var user = createRegularUser();
        var competition = createCompetition();
        var existingParticipant = new Participant(competition.getId(), user.getId());
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.findByCompetitionIdAndUserId(competition.getId(), user.getId()))
                .willReturn(Optional.of(existingParticipant));
        given(participantRepository.existsByAccessCode(any())).willReturn(false);
        given(participantRepository.save(any(Participant.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(participantRoleRepository.existsByParticipantIdAndRole(
                existingParticipant.getId(), CompetitionRole.JUDGE)).willReturn(false);
        given(participantRoleRepository.findByParticipantId(existingParticipant.getId()))
                .willReturn(List.of(new ParticipantRole(existingParticipant.getId(), CompetitionRole.ENTRANT)));
        given(participantRoleRepository.save(any(ParticipantRole.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addParticipant(
                competition.getId(), user.getId(), CompetitionRole.JUDGE, admin.getId());

        assertThat(result.getRole()).isEqualTo(CompetitionRole.JUDGE);
    }

    @Test
    void shouldAssignAccessCodeWhenPromotingEntrantToJudge() {
        var admin = createAdmin();
        var user = createRegularUser();
        var competition = createCompetition();
        var existingParticipant = new Participant(competition.getId(), user.getId());
        // existingParticipant has no access code (was created as ENTRANT)
        assertThat(existingParticipant.getAccessCode()).isNull();

        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.findByCompetitionIdAndUserId(competition.getId(), user.getId()))
                .willReturn(Optional.of(existingParticipant));
        given(participantRoleRepository.existsByParticipantIdAndRole(
                existingParticipant.getId(), CompetitionRole.JUDGE)).willReturn(false);
        given(participantRoleRepository.findByParticipantId(existingParticipant.getId()))
                .willReturn(List.of(new ParticipantRole(existingParticipant.getId(), CompetitionRole.ENTRANT)));
        given(participantRepository.existsByAccessCode(any())).willReturn(false);
        given(participantRepository.save(any(Participant.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(participantRoleRepository.save(any(ParticipantRole.class)))
                .willAnswer(inv -> inv.getArgument(0));

        competitionService.addParticipant(
                competition.getId(), user.getId(), CompetitionRole.JUDGE, admin.getId());

        then(participantRepository).should().save(argThat(
                (Participant p) -> p.getAccessCode() != null));
    }

    @Test
    void shouldRejectAddingAdminToExistingJudge() {
        var admin = createAdmin();
        var user = createRegularUser();
        var competition = createCompetition();
        var existingParticipant = new Participant(competition.getId(), user.getId());
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.findByCompetitionIdAndUserId(competition.getId(), user.getId()))
                .willReturn(Optional.of(existingParticipant));
        given(participantRoleRepository.existsByParticipantIdAndRole(
                existingParticipant.getId(), CompetitionRole.ADMIN)).willReturn(false);
        given(participantRoleRepository.findByParticipantId(existingParticipant.getId()))
                .willReturn(List.of(new ParticipantRole(existingParticipant.getId(), CompetitionRole.JUDGE)));

        assertThatThrownBy(() -> competitionService.addParticipant(
                competition.getId(), user.getId(), CompetitionRole.ADMIN, admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.participant.incompatible-role");

        then(participantRoleRepository).should(never()).save(any());
    }

    @Test
    void shouldRejectAddingStewardToExistingEntrant() {
        var admin = createAdmin();
        var user = createRegularUser();
        var competition = createCompetition();
        var existingParticipant = new Participant(competition.getId(), user.getId());
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.findByCompetitionIdAndUserId(competition.getId(), user.getId()))
                .willReturn(Optional.of(existingParticipant));
        given(participantRepository.existsByAccessCode(any())).willReturn(false);
        given(participantRepository.save(any(Participant.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(participantRoleRepository.existsByParticipantIdAndRole(
                existingParticipant.getId(), CompetitionRole.STEWARD)).willReturn(false);
        given(participantRoleRepository.findByParticipantId(existingParticipant.getId()))
                .willReturn(List.of(new ParticipantRole(existingParticipant.getId(), CompetitionRole.ENTRANT)));

        assertThatThrownBy(() -> competitionService.addParticipant(
                competition.getId(), user.getId(), CompetitionRole.STEWARD, admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.participant.incompatible-role");

        then(participantRoleRepository).should(never()).save(any());
    }

    @Test
    void shouldRejectAddingEntrantToExistingAdmin() {
        var admin = createAdmin();
        var user = createRegularUser();
        var competition = createCompetition();
        var existingParticipant = new Participant(competition.getId(), user.getId());
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.findByCompetitionIdAndUserId(competition.getId(), user.getId()))
                .willReturn(Optional.of(existingParticipant));
        given(participantRoleRepository.existsByParticipantIdAndRole(
                existingParticipant.getId(), CompetitionRole.ENTRANT)).willReturn(false);
        given(participantRoleRepository.findByParticipantId(existingParticipant.getId()))
                .willReturn(List.of(new ParticipantRole(existingParticipant.getId(), CompetitionRole.ADMIN)));

        assertThatThrownBy(() -> competitionService.addParticipant(
                competition.getId(), user.getId(), CompetitionRole.ENTRANT, admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.participant.incompatible-role");

        then(participantRoleRepository).should(never()).save(any());
    }

    @Test
    void shouldRejectDuplicateRoleForSameParticipant() {
        var admin = createAdmin();
        var user = createRegularUser();
        var competition = createCompetition();
        var existingParticipant = new Participant(competition.getId(), user.getId());
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.findByCompetitionIdAndUserId(competition.getId(), user.getId()))
                .willReturn(Optional.of(existingParticipant));
        given(participantRepository.existsByAccessCode(any())).willReturn(false);
        given(participantRepository.save(any(Participant.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(participantRoleRepository.existsByParticipantIdAndRole(
                existingParticipant.getId(), CompetitionRole.JUDGE)).willReturn(true);

        assertThatThrownBy(() -> competitionService.addParticipant(
                competition.getId(), user.getId(), CompetitionRole.JUDGE, admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.participant.role-exists");

        then(participantRoleRepository).should(never()).save(any());
    }

    // --- ensureEntrantParticipant ---

    @Test
    void shouldRejectEnsureEntrantWhenUserIsAdmin() {
        var competition = createCompetition();
        var userId = UUID.randomUUID();
        var existingParticipant = new Participant(competition.getId(), userId);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(participantRepository.findByCompetitionIdAndUserId(competition.getId(), userId))
                .willReturn(Optional.of(existingParticipant));
        given(participantRoleRepository.existsByParticipantIdAndRole(
                existingParticipant.getId(), CompetitionRole.ENTRANT)).willReturn(false);
        given(participantRoleRepository.findByParticipantId(existingParticipant.getId()))
                .willReturn(List.of(new ParticipantRole(existingParticipant.getId(), CompetitionRole.ADMIN)));

        assertThatThrownBy(() -> competitionService.ensureEntrantParticipant(
                competition.getId(), userId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.participant.incompatible-role");

        then(participantRoleRepository).should(never()).save(any());
    }

    @Test
    void shouldNotAssignAccessCodeForEntrant() {
        var admin = createAdmin();
        var user = createRegularUser();
        var competition = createCompetition();
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.findByCompetitionIdAndUserId(competition.getId(), user.getId()))
                .willReturn(Optional.empty());
        given(participantRepository.save(any(Participant.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(participantRoleRepository.existsByParticipantIdAndRole(
                any(), any())).willReturn(false);
        given(participantRoleRepository.save(any(ParticipantRole.class)))
                .willAnswer(inv -> inv.getArgument(0));

        competitionService.addParticipant(
                competition.getId(), user.getId(), CompetitionRole.ENTRANT, admin.getId());

        then(participantRepository).should().save(argThat(
                (Participant p) -> p.getAccessCode() == null));
    }

    @Test
    void shouldAllowCompetitionAdminToAddParticipant() {
        var compAdmin = createRegularUser();
        var user = new User("target@example.com", "Target",
                UserStatus.ACTIVE, Role.USER);
        var competition = createCompetition();
        var compAdminParticipant = new Participant(competition.getId(), compAdmin.getId());
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(compAdmin.getId())).willReturn(compAdmin);
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.findByCompetitionIdAndUserId(
                competition.getId(), compAdmin.getId()))
                .willReturn(Optional.of(compAdminParticipant));
        given(participantRoleRepository.existsByParticipantIdAndRole(
                compAdminParticipant.getId(), CompetitionRole.ADMIN))
                .willReturn(true);
        given(participantRepository.findByCompetitionIdAndUserId(
                competition.getId(), user.getId()))
                .willReturn(Optional.empty());
        given(participantRepository.save(any(Participant.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(participantRoleRepository.existsByParticipantIdAndRole(
                any(), org.mockito.ArgumentMatchers.eq(CompetitionRole.JUDGE))).willReturn(false);
        given(participantRoleRepository.save(any(ParticipantRole.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addParticipant(
                competition.getId(), user.getId(), CompetitionRole.JUDGE, compAdmin.getId());

        assertThat(result.getRole()).isEqualTo(CompetitionRole.JUDGE);
    }

    @Test
    void shouldRejectUnauthorizedUserForCompetitionOperations() {
        var user = createRegularUser();
        var competition = createCompetition();
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.findByCompetitionIdAndUserId(competition.getId(), user.getId()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> competitionService.addParticipant(
                competition.getId(), UUID.randomUUID(), CompetitionRole.JUDGE, user.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.auth.unauthorized");

        then(participantRoleRepository).should(never()).save(any());
    }

    @Test
    void shouldRegenerateAccessCodeWhenCollisionDetected() {
        var admin = createAdmin();
        var user = createRegularUser();
        var competition = createCompetition();
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.findByCompetitionIdAndUserId(competition.getId(), user.getId()))
                .willReturn(Optional.empty());
        // First generated code collides, second does not
        given(participantRepository.existsByAccessCode(any()))
                .willReturn(true)
                .willReturn(false);
        given(participantRepository.save(any(Participant.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(participantRoleRepository.existsByParticipantIdAndRole(
                any(), any())).willReturn(false);
        given(participantRoleRepository.save(any(ParticipantRole.class)))
                .willAnswer(inv -> inv.getArgument(0));

        competitionService.addParticipant(
                competition.getId(), user.getId(), CompetitionRole.JUDGE, admin.getId());

        // existsByAccessCode should have been called at least twice (collision + success)
        then(participantRepository).should(atLeast(2)).existsByAccessCode(any());
        then(participantRepository).should().save(argThat(
                (Participant p) -> p.getAccessCode() != null));
    }

    // --- removeParticipant ---

    @Test
    void shouldRemoveParticipantFromCompetition() {
        var admin = createAdmin();
        var competition = createCompetition();
        var participant = new Participant(competition.getId(), UUID.randomUUID());
        var pr = new ParticipantRole(participant.getId(), CompetitionRole.JUDGE);
        given(userService.findById(admin.getId())).willReturn(admin);
        given(participantRepository.findById(participant.getId()))
                .willReturn(Optional.of(participant));
        given(participantRoleRepository.findByParticipantId(participant.getId()))
                .willReturn(List.of(pr));

        competitionService.removeParticipant(
                competition.getId(), participant.getId(), admin.getId());

        then(participantRoleRepository).should().deleteAll(List.of(pr));
        then(participantRepository).should().delete(participant);
    }

    @Test
    void shouldCallRemovalCleanupsBeforeRemovingParticipant() {
        var admin = createAdmin();
        var competition = createCompetition();
        var userId = UUID.randomUUID();
        var participant = new Participant(competition.getId(), userId);
        var pr = new ParticipantRole(participant.getId(), CompetitionRole.ENTRANT);
        given(userService.findById(admin.getId())).willReturn(admin);
        given(participantRepository.findById(participant.getId()))
                .willReturn(Optional.of(participant));
        given(participantRoleRepository.findByParticipantId(participant.getId()))
                .willReturn(List.of(pr));

        var cleanup = mock(ParticipantRemovalCleanup.class);
        removalCleanups.add(cleanup);

        competitionService.removeParticipant(
                competition.getId(), participant.getId(), admin.getId());

        then(cleanup).should().cleanupForParticipant(competition.getId(), userId);
        then(participantRoleRepository).should().deleteAll(List.of(pr));
        then(participantRepository).should().delete(participant);
    }

    // --- removeParticipantRole ---

    @Test
    void shouldRemoveSingleRoleFromParticipantWithMultipleRoles() {
        var admin = createAdmin();
        var competition = createCompetition();
        var participant = new Participant(competition.getId(), UUID.randomUUID());
        var judgeRole = new ParticipantRole(participant.getId(), CompetitionRole.JUDGE);
        var entrantRole = new ParticipantRole(participant.getId(), CompetitionRole.ENTRANT);
        given(userService.findById(admin.getId())).willReturn(admin);
        given(participantRepository.findById(participant.getId()))
                .willReturn(Optional.of(participant));
        given(participantRoleRepository.findByParticipantId(participant.getId()))
                .willReturn(List.of(judgeRole, entrantRole));

        competitionService.removeParticipantRole(
                competition.getId(), participant.getId(), CompetitionRole.ENTRANT, admin.getId());

        then(participantRoleRepository).should().delete(entrantRole);
        then(participantRepository).should(never()).delete(any());
    }

    @Test
    void shouldRemoveParticipantWhenLastRoleRemoved() {
        var admin = createAdmin();
        var competition = createCompetition();
        var participant = new Participant(competition.getId(), UUID.randomUUID());
        var judgeRole = new ParticipantRole(participant.getId(), CompetitionRole.JUDGE);
        given(userService.findById(admin.getId())).willReturn(admin);
        given(participantRepository.findById(participant.getId()))
                .willReturn(Optional.of(participant));
        given(participantRoleRepository.findByParticipantId(participant.getId()))
                .willReturn(List.of(judgeRole));

        competitionService.removeParticipantRole(
                competition.getId(), participant.getId(), CompetitionRole.JUDGE, admin.getId());

        then(participantRoleRepository).should().delete(judgeRole);
        then(participantRepository).should().delete(participant);
    }

    @Test
    void shouldInvokeCleanupWhenLastRoleRemoved() {
        var admin = createAdmin();
        var competition = createCompetition();
        var userId = UUID.randomUUID();
        var participant = new Participant(competition.getId(), userId);
        var judgeRole = new ParticipantRole(participant.getId(), CompetitionRole.JUDGE);
        given(userService.findById(admin.getId())).willReturn(admin);
        given(participantRepository.findById(participant.getId()))
                .willReturn(Optional.of(participant));
        given(participantRoleRepository.findByParticipantId(participant.getId()))
                .willReturn(List.of(judgeRole));

        var cleanup = mock(ParticipantRemovalCleanup.class);
        removalCleanups.add(cleanup);

        competitionService.removeParticipantRole(
                competition.getId(), participant.getId(), CompetitionRole.JUDGE, admin.getId());

        then(cleanup).should().cleanupForParticipant(competition.getId(), userId);
        then(participantRoleRepository).should().delete(judgeRole);
        then(participantRepository).should().delete(participant);
    }

    @Test
    void shouldRejectRemoveRoleWhenParticipantDoesNotHaveRole() {
        var admin = createAdmin();
        var competition = createCompetition();
        var participant = new Participant(competition.getId(), UUID.randomUUID());
        var judgeRole = new ParticipantRole(participant.getId(), CompetitionRole.JUDGE);
        given(userService.findById(admin.getId())).willReturn(admin);
        given(participantRepository.findById(participant.getId()))
                .willReturn(Optional.of(participant));
        given(participantRoleRepository.findByParticipantId(participant.getId()))
                .willReturn(List.of(judgeRole));

        assertThatThrownBy(() -> competitionService.removeParticipantRole(
                competition.getId(), participant.getId(), CompetitionRole.ENTRANT, admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.participant.role-not-found");
    }

    // --- findRolesForParticipant ---

    @Test
    void shouldFindRolesForParticipant() {
        var participantId = UUID.randomUUID();
        var judgeRole = new ParticipantRole(participantId, CompetitionRole.JUDGE);
        var entrantRole = new ParticipantRole(participantId, CompetitionRole.ENTRANT);
        given(participantRoleRepository.findByParticipantId(participantId))
                .willReturn(List.of(judgeRole, entrantRole));

        var result = competitionService.findRolesForParticipant(participantId);

        assertThat(result).containsExactlyInAnyOrder(judgeRole, entrantRole);
    }

    // --- addParticipantByEmail ---

    @Test
    void shouldAddParticipantByEmailWhenUserExists() {
        var admin = createAdmin();
        var user = createRegularUser();
        var competition = createCompetition();
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(userService.findOrCreateByEmail("user@example.com")).willReturn(user);
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.findByCompetitionIdAndUserId(competition.getId(), user.getId()))
                .willReturn(Optional.empty());
        given(participantRepository.save(any(Participant.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(participantRoleRepository.existsByParticipantIdAndRole(
                any(), any())).willReturn(false);
        given(participantRoleRepository.save(any(ParticipantRole.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addParticipantByEmail(
                competition.getId(), "user@example.com", CompetitionRole.JUDGE, admin.getId());

        assertThat(result.getRole()).isEqualTo(CompetitionRole.JUDGE);
        then(userService).should().findOrCreateByEmail("user@example.com");
    }

    // --- isAuthorizedForCompetition ---

    @Test
    void shouldReturnTrueWhenAuthorizedForCompetition() {
        var admin = createAdmin();
        given(userService.findById(admin.getId())).willReturn(admin);

        var result = competitionService.isAuthorizedForCompetition(
                UUID.randomUUID(), admin.getId());

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenNotAuthorizedForCompetition() {
        var user = createRegularUser();
        var competitionId = UUID.randomUUID();
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.findByCompetitionIdAndUserId(competitionId, user.getId()))
                .willReturn(Optional.empty());

        var result = competitionService.isAuthorizedForCompetition(
                competitionId, user.getId());

        assertThat(result).isFalse();
    }

    // --- findCompetitionsByAdmin ---

    @Test
    void shouldFindCompetitionsWhereUserIsAdmin() {
        var user = createRegularUser();
        var comp1 = createCompetition();
        var comp2 = new Competition("Other Competition", "other-competition",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "Porto");
        var participant1 = new Participant(comp1.getId(), user.getId());
        var participant2 = new Participant(comp2.getId(), user.getId());
        given(participantRepository.findByUserId(user.getId()))
                .willReturn(List.of(participant1, participant2));
        given(participantRoleRepository.existsByParticipantIdAndRole(
                participant1.getId(), CompetitionRole.ADMIN)).willReturn(true);
        given(participantRoleRepository.existsByParticipantIdAndRole(
                participant2.getId(), CompetitionRole.ADMIN)).willReturn(true);
        given(competitionRepository.findById(comp1.getId()))
                .willReturn(Optional.of(comp1));
        given(competitionRepository.findById(comp2.getId()))
                .willReturn(Optional.of(comp2));

        var result = competitionService.findCompetitionsByAdmin(user.getId());

        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyInAnyOrder(comp1, comp2);
    }

    @Test
    void shouldReturnEmptyWhenUserIsNotAdminOfAnyCompetition() {
        var user = createRegularUser();
        var comp = createCompetition();
        var participant = new Participant(comp.getId(), user.getId());
        given(participantRepository.findByUserId(user.getId()))
                .willReturn(List.of(participant));
        given(participantRoleRepository.existsByParticipantIdAndRole(
                participant.getId(), CompetitionRole.ADMIN)).willReturn(false);

        var result = competitionService.findCompetitionsByAdmin(user.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenUserHasNoParticipations() {
        var user = createRegularUser();
        given(participantRepository.findByUserId(user.getId()))
                .willReturn(List.of());

        var result = competitionService.findCompetitionsByAdmin(user.getId());

        assertThat(result).isEmpty();
    }

    // --- findDivisionsByCompetition ---

    @Test
    void shouldFindDivisionsByCompetition() {
        var competitionId = UUID.randomUUID();
        var div1 = new Division(competitionId, "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var div2 = new Division(competitionId, "Pro", "pro", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        given(divisionRepository.findByCompetitionIdOrderByName(competitionId))
                .willReturn(List.of(div1, div2));

        var result = competitionService.findDivisionsByCompetition(competitionId);

        assertThat(result).hasSize(2);
        then(divisionRepository).should().findByCompetitionIdOrderByName(competitionId);
    }

    // --- isAuthorizedForDivision ---

    @Test
    void shouldReturnTrueWhenUserIsAdminForDivisionCompetition() {
        var admin = createAdmin();
        var division = new Division(UUID.randomUUID(), "Home", "home", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);

        var result = competitionService.isAuthorizedForDivision(
                division.getId(), admin.getId());

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenUserIsNotAdminForDivisionCompetition() {
        var user = createRegularUser();
        var division = new Division(UUID.randomUUID(), "Home", "home", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.findByCompetitionIdAndUserId(
                division.getCompetitionId(), user.getId()))
                .willReturn(Optional.empty());

        var result = competitionService.isAuthorizedForDivision(
                division.getId(), user.getId());

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenDivisionNotFound() {
        var user = createRegularUser();
        var divisionId = UUID.randomUUID();
        given(divisionRepository.findById(divisionId)).willReturn(Optional.empty());

        var result = competitionService.isAuthorizedForDivision(
                divisionId, user.getId());

        assertThat(result).isFalse();
    }

    // --- findAuthorizedDivisions ---

    @Test
    void shouldFindAuthorizedDivisionsForSystemAdmin() {
        var admin = createAdmin();
        var competitionId = UUID.randomUUID();
        var div1 = new Division(competitionId, "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var div2 = new Division(competitionId, "Pro", "pro", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionRepository.findByCompetitionIdOrderByName(competitionId))
                .willReturn(List.of(div1, div2));

        var result = competitionService.findAuthorizedDivisions(
                competitionId, admin.getId());

        assertThat(result).hasSize(2);
    }

    @Test
    void shouldFindAuthorizedDivisionsForCompetitionAdmin() {
        var compAdmin = createRegularUser();
        var competitionId = UUID.randomUUID();
        var div1 = new Division(competitionId, "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var div2 = new Division(competitionId, "Pro", "pro", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var participant = new Participant(competitionId, compAdmin.getId());
        given(userService.findById(compAdmin.getId())).willReturn(compAdmin);
        given(participantRepository.findByCompetitionIdAndUserId(competitionId, compAdmin.getId()))
                .willReturn(Optional.of(participant));
        given(participantRoleRepository.existsByParticipantIdAndRole(
                participant.getId(), CompetitionRole.ADMIN))
                .willReturn(true);
        given(divisionRepository.findByCompetitionIdOrderByName(competitionId))
                .willReturn(List.of(div1, div2));

        var result = competitionService.findAuthorizedDivisions(
                competitionId, compAdmin.getId());

        // ADMIN role is competition-scoped — sees all divisions
        assertThat(result).hasSize(2);
    }

    @Test
    void shouldReturnEmptyDivisionsForUnauthorizedUser() {
        var user = createRegularUser();
        var competitionId = UUID.randomUUID();
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.findByCompetitionIdAndUserId(competitionId, user.getId()))
                .willReturn(Optional.empty());

        var result = competitionService.findAuthorizedDivisions(
                competitionId, user.getId());

        assertThat(result).isEmpty();
    }

    // --- findDivisionCategories ---

    @Test
    void shouldFindDivisionCategories() {
        var divisionId = UUID.randomUUID();
        var dc1 = new DivisionCategory(divisionId, null,
                "M1A", "Traditional Mead", "A traditional mead", null, 0);
        var dc2 = new DivisionCategory(divisionId, null,
                "M1B", "Semi-Sweet Mead", "A semi-sweet mead", null, 1);
        given(divisionCategoryRepository.findByDivisionIdOrderByCode(divisionId))
                .willReturn(List.of(dc1, dc2));

        var result = competitionService.findDivisionCategories(divisionId);

        assertThat(result).hasSize(2);
        then(divisionCategoryRepository).should()
                .findByDivisionIdOrderByCode(divisionId);
    }

    // --- addCatalogCategory ---

    @Test
    void shouldAddCatalogCategoryToDivision() {
        var admin = createAdmin();
        var division = new Division(UUID.randomUUID(),
                "Home", "home", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var catalogCat = new Category();
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(categoryRepository.findById(catalogCat.getId()))
                .willReturn(Optional.of(catalogCat));
        given(divisionCategoryRepository.existsByDivisionIdAndCatalogCategoryId(
                division.getId(), catalogCat.getId())).willReturn(false);
        given(divisionCategoryRepository.save(any(DivisionCategory.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addCatalogCategory(
                division.getId(), catalogCat.getId(), admin.getId());

        assertThat(result).isNotNull();
        assertThat(result.getDivisionId()).isEqualTo(division.getId());
        assertThat(result.getCatalogCategoryId()).isEqualTo(catalogCat.getId());
        then(divisionCategoryRepository).should().save(any(DivisionCategory.class));
    }

    @Test
    void shouldRejectDuplicateCatalogCategory() {
        var admin = createAdmin();
        var division = new Division(UUID.randomUUID(),
                "Home", "home", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var catalogCat = new Category();
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(categoryRepository.findById(catalogCat.getId()))
                .willReturn(Optional.of(catalogCat));
        given(divisionCategoryRepository.existsByDivisionIdAndCatalogCategoryId(
                division.getId(), catalogCat.getId())).willReturn(true);

        assertThatThrownBy(() -> competitionService.addCatalogCategory(
                division.getId(), catalogCat.getId(), admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.category.already-added");

        then(divisionCategoryRepository).should(never()).save(any());
    }

    // --- addCustomCategory ---

    @Test
    void shouldAddCustomCategory() {
        var admin = createAdmin();
        var division = new Division(UUID.randomUUID(),
                "Home", "home", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionCategoryRepository.existsByDivisionIdAndCode(
                division.getId(), "CUSTOM1")).willReturn(false);
        given(divisionCategoryRepository.save(any(DivisionCategory.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addCustomCategory(
                division.getId(), "CUSTOM1", "Best Local Honey",
                "Mead made with local honey", null, admin.getId());

        assertThat(result).isNotNull();
        assertThat(result.getDivisionId()).isEqualTo(division.getId());
        assertThat(result.getCatalogCategoryId()).isNull();
        assertThat(result.getCode()).isEqualTo("CUSTOM1");
        assertThat(result.getName()).isEqualTo("Best Local Honey");
        assertThat(result.getParentId()).isNull();
        then(divisionCategoryRepository).should().save(any(DivisionCategory.class));
    }

    @Test
    void shouldAddCustomSubcategory() {
        var admin = createAdmin();
        var division = new Division(UUID.randomUUID(),
                "Home", "home", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var parent = new DivisionCategory(division.getId(), null,
                "M2E", "Other Fruit Melomel", "Other fruits", null, 0);
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionCategoryRepository.existsByDivisionIdAndCode(
                division.getId(), "M2E-T")).willReturn(false);
        given(divisionCategoryRepository.findById(parent.getId()))
                .willReturn(Optional.of(parent));
        given(divisionCategoryRepository.save(any(DivisionCategory.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addCustomCategory(
                division.getId(), "M2E-T", "Tropical",
                "Tropical fruit melomel", parent.getId(), admin.getId());

        assertThat(result.getParentId()).isEqualTo(parent.getId());
        assertThat(result.getCatalogCategoryId()).isNull();
        then(divisionCategoryRepository).should().save(any(DivisionCategory.class));
    }

    // --- updateDivisionCategory ---

    @Test
    void shouldUpdateDivisionCategory() {
        var admin = createAdmin();
        var division = new Division(UUID.randomUUID(),
                "Home", "home", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var catalogCategoryId = UUID.randomUUID();
        var dc = new DivisionCategory(division.getId(), catalogCategoryId,
                "M1A", "Traditional Mead", "A traditional mead", null, 0);
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionCategoryRepository.findById(dc.getId()))
                .willReturn(Optional.of(dc));
        given(divisionCategoryRepository.save(any(DivisionCategory.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.updateDivisionCategory(
                division.getId(), dc.getId(),
                "M1A-C", "Custom Trad Mead", "A custom description",
                admin.getId());

        assertThat(result.getCode()).isEqualTo("M1A-C");
        assertThat(result.getName()).isEqualTo("Custom Trad Mead");
        assertThat(result.getDescription()).isEqualTo("A custom description");
        assertThat(result.getCatalogCategoryId()).isNull();
    }

    // --- removeDivisionCategory ---

    @Test
    void shouldRemoveDivisionCategory() {
        var admin = createAdmin();
        var division = new Division(UUID.randomUUID(),
                "Home", "home", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var dc = new DivisionCategory(division.getId(), null,
                "M1A", "Traditional Mead", "A traditional mead", null, 0);
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionCategoryRepository.findById(dc.getId()))
                .willReturn(Optional.of(dc));
        given(divisionCategoryRepository.findByParentId(dc.getId()))
                .willReturn(List.of());

        competitionService.removeDivisionCategory(
                division.getId(), dc.getId(), admin.getId());

        then(divisionCategoryRepository).should().delete(dc);
    }

    @Test
    void shouldRemoveDivisionCategoryWithSubcategories() {
        var admin = createAdmin();
        var division = new Division(UUID.randomUUID(),
                "Home", "home", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var parent = new DivisionCategory(division.getId(), null,
                "M2E", "Other Fruit Melomel", "Other fruits", null, 0);
        var child = new DivisionCategory(division.getId(), null,
                "M2E-T", "Tropical", "Tropical fruit melomel", parent.getId(), 1);
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionCategoryRepository.findById(parent.getId()))
                .willReturn(Optional.of(parent));
        given(divisionCategoryRepository.findByParentId(parent.getId()))
                .willReturn(List.of(child));

        competitionService.removeDivisionCategory(
                division.getId(), parent.getId(), admin.getId());

        then(divisionCategoryRepository).should().deleteAll(List.of(child));
        then(divisionCategoryRepository).should().delete(parent);
    }

    // --- status guard ---

    @Test
    void shouldRejectCategoryChangeAfterRegistrationClosed() {
        var admin = createAdmin();
        var division = new Division(UUID.randomUUID(),
                "Home", "home", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        // Advance to REGISTRATION_OPEN then REGISTRATION_CLOSED
        division.advanceStatus(); // DRAFT -> REGISTRATION_OPEN
        division.advanceStatus(); // REGISTRATION_OPEN -> REGISTRATION_CLOSED
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);

        assertThatThrownBy(() -> competitionService.addCustomCategory(
                division.getId(), "CUSTOM", "Custom",
                "A custom category", null, admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.category.cannot-modify-status");

        assertThatThrownBy(() -> competitionService.addCatalogCategory(
                division.getId(), UUID.randomUUID(), admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.category.cannot-modify-status");

        assertThatThrownBy(() -> competitionService.removeDivisionCategory(
                division.getId(), UUID.randomUUID(), admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.category.cannot-modify-status");

        then(divisionCategoryRepository).should(never()).save(any());
        then(divisionCategoryRepository).should(never()).delete(any());
    }

    // --- findAvailableCatalogCategories ---

    @Test
    void shouldFindAvailableCatalogCategories() {
        var cat1Id = UUID.randomUUID();
        var cat2Id = UUID.randomUUID();
        var cat3Id = UUID.randomUUID();
        var cat1 = org.mockito.Mockito.mock(Category.class);
        var cat2 = org.mockito.Mockito.mock(Category.class);
        var cat3 = org.mockito.Mockito.mock(Category.class);
        given(cat1.getId()).willReturn(cat1Id);
        given(cat2.getId()).willReturn(cat2Id);
        given(cat3.getId()).willReturn(cat3Id);
        var division = new Division(UUID.randomUUID(),
                "Home", "home", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(categoryRepository.findByScoringSystemOrderByCode(ScoringSystem.MJP))
                .willReturn(List.of(cat1, cat2, cat3));
        // cat1 already added, cat2 and cat3 not
        given(divisionCategoryRepository.existsByDivisionIdAndCatalogCategoryId(
                division.getId(), cat1Id)).willReturn(true);
        given(divisionCategoryRepository.existsByDivisionIdAndCatalogCategoryId(
                division.getId(), cat2Id)).willReturn(false);
        given(divisionCategoryRepository.existsByDivisionIdAndCatalogCategoryId(
                division.getId(), cat3Id)).willReturn(false);

        var result = competitionService.findAvailableCatalogCategories(division.getId());

        assertThat(result).hasSize(2);
        assertThat(result).contains(cat2, cat3);
        assertThat(result).doesNotContain(cat1);
    }

    // --- updateDivisionEntryLimits ---

    @Test
    void shouldUpdateDivisionEntryLimitsWhenRequestedByAdmin() {
        var admin = createAdmin();
        var division = new Division(UUID.randomUUID(),
                "Amadora", "amadora", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionRepository.save(any(Division.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.updateDivisionEntryLimits(
                division.getId(), 3, 5, 10, admin.getId());

        assertThat(result.getMaxEntriesPerSubcategory()).isEqualTo(3);
        assertThat(result.getMaxEntriesPerMainCategory()).isEqualTo(5);
        assertThat(result.getMaxEntriesTotal()).isEqualTo(10);
        then(divisionRepository).should().save(division);
    }

    @Test
    void shouldRejectUpdateDivisionEntryLimitsWhenNotDraft() {
        var admin = createAdmin();
        var division = new Division(UUID.randomUUID(),
                "Amadora", "amadora", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        division.advanceStatus(); // DRAFT → REGISTRATION_OPEN
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);

        assertThatThrownBy(() -> competitionService.updateDivisionEntryLimits(
                division.getId(), 3, 5, 10, admin.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
    }

    // --- updateDivisionMeaderyNameRequired ---

    @Test
    void shouldUpdateDivisionMeaderyNameRequired() {
        var admin = createAdmin();
        var division = new Division(UUID.randomUUID(),
                "Amadora", "amadora", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionRepository.save(any(Division.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.updateDivisionMeaderyNameRequired(
                division.getId(), true, admin.getId());

        assertThat(result.isMeaderyNameRequired()).isTrue();
        then(divisionRepository).should().save(division);
    }

    // --- Document methods ---

    @Test
    void shouldAddPdfDocumentWhenAuthorized() {
        var admin = createAdmin();
        var competition = createCompetition();
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(competitionDocumentRepository.existsByCompetitionIdAndName(
                competition.getId(), "Rules")).willReturn(false);
        given(competitionDocumentRepository.countByCompetitionId(competition.getId()))
                .willReturn(0);
        given(competitionDocumentRepository.save(any(CompetitionDocument.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addDocument(competition.getId(), "Rules",
                DocumentType.PDF, new byte[]{1, 2, 3}, "application/pdf", null, null, admin.getId());

        assertThat(result.getName()).isEqualTo("Rules");
        assertThat(result.getType()).isEqualTo(DocumentType.PDF);
        assertThat(result.getDisplayOrder()).isZero();
    }

    @Test
    void shouldAddLinkDocumentWhenAuthorized() {
        var admin = createAdmin();
        var competition = createCompetition();
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(competitionDocumentRepository.existsByCompetitionIdAndName(
                competition.getId(), "MJP Guide")).willReturn(false);
        given(competitionDocumentRepository.countByCompetitionId(competition.getId()))
                .willReturn(2);
        given(competitionDocumentRepository.save(any(CompetitionDocument.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addDocument(competition.getId(), "MJP Guide",
                DocumentType.LINK, null, null, "https://example.com/mjp", null, admin.getId());

        assertThat(result.getType()).isEqualTo(DocumentType.LINK);
        assertThat(result.getUrl()).isEqualTo("https://example.com/mjp");
        assertThat(result.getDisplayOrder()).isEqualTo(2);
    }

    @Test
    void shouldAddDocumentWithLanguage() {
        var admin = createAdmin();
        var competition = createCompetition();
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(competitionDocumentRepository.existsByCompetitionIdAndName(
                competition.getId(), "Regras")).willReturn(false);
        given(competitionDocumentRepository.countByCompetitionId(competition.getId()))
                .willReturn(0);
        given(competitionDocumentRepository.save(any(CompetitionDocument.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addDocument(competition.getId(), "Regras",
                DocumentType.LINK, null, null, "https://example.com/rules-pt", "pt", admin.getId());

        assertThat(result.getName()).isEqualTo("Regras");
        assertThat(result.getLanguage()).isEqualTo("pt");
    }

    @Test
    void shouldRejectDuplicateDocumentName() {
        var admin = createAdmin();
        var competition = createCompetition();
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(competitionDocumentRepository.existsByCompetitionIdAndName(
                competition.getId(), "Rules")).willReturn(true);

        assertThatThrownBy(() -> competitionService.addDocument(competition.getId(), "Rules",
                DocumentType.LINK, null, null, "https://example.com", null, admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.document.name-exists");
    }

    @Test
    void shouldRemoveDocumentWhenAuthorized() {
        var admin = createAdmin();
        var competition = createCompetition();
        var doc = CompetitionDocument.createLink(competition.getId(), "Rules", "https://example.com", 0, null);
        given(competitionDocumentRepository.findById(doc.getId()))
                .willReturn(Optional.of(doc));
        given(userService.findById(admin.getId())).willReturn(admin);

        competitionService.removeDocument(doc.getId(), admin.getId());

        then(competitionDocumentRepository).should().delete(doc);
    }

    @Test
    void shouldUpdateDocumentNameWhenAuthorized() {
        var admin = createAdmin();
        var competition = createCompetition();
        var doc = CompetitionDocument.createLink(competition.getId(), "Old Name", "https://example.com", 0, null);
        given(competitionDocumentRepository.findById(doc.getId()))
                .willReturn(Optional.of(doc));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionDocumentRepository.existsByCompetitionIdAndName(
                competition.getId(), "New Name")).willReturn(false);
        given(competitionDocumentRepository.save(any(CompetitionDocument.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.updateDocumentName(doc.getId(), "New Name", admin.getId());

        assertThat(result.getName()).isEqualTo("New Name");
    }

    @Test
    void shouldGetDocumentsOrderedByDisplayOrder() {
        var competition = createCompetition();
        var doc1 = CompetitionDocument.createLink(competition.getId(), "A", "https://a.com", 0, null);
        var doc2 = CompetitionDocument.createLink(competition.getId(), "B", "https://b.com", 1, null);
        given(competitionDocumentRepository.findByCompetitionIdOrderByDisplayOrder(competition.getId()))
                .willReturn(List.of(doc1, doc2));

        var result = competitionService.getDocuments(competition.getId());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("A");
    }

    @Test
    void shouldGetDocumentById() {
        var doc = CompetitionDocument.createLink(UUID.randomUUID(), "Rules", "https://example.com", 0, null);
        given(competitionDocumentRepository.findById(doc.getId()))
                .willReturn(Optional.of(doc));

        var result = competitionService.getDocument(doc.getId());

        assertThat(result.getName()).isEqualTo("Rules");
    }

    @Test
    void shouldThrowWhenDocumentNotFound() {
        var randomId = UUID.randomUUID();
        given(competitionDocumentRepository.findById(randomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> competitionService.getDocument(randomId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.document.not-found");
    }

    @Test
    void shouldReorderDocumentsWhenAuthorized() {
        var admin = createAdmin();
        var competition = createCompetition();
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        var doc1 = CompetitionDocument.createLink(competition.getId(), "A", "https://a.com", 0, null);
        var doc2 = CompetitionDocument.createLink(competition.getId(), "B", "https://b.com", 1, null);
        given(competitionDocumentRepository.findByCompetitionIdOrderByDisplayOrder(competition.getId()))
                .willReturn(List.of(doc1, doc2));

        // Reorder: B first, then A
        competitionService.reorderDocuments(competition.getId(),
                List.of(doc2.getId(), doc1.getId()), admin.getId());

        assertThat(doc2.getDisplayOrder()).isZero();
        assertThat(doc1.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void shouldReturnMatchingAndUniversalDocumentsForLocale() {
        var competition = createCompetition();
        var docEn = CompetitionDocument.createLink(competition.getId(), "Rules EN", "https://en.com", 0, "en");
        var docPt = CompetitionDocument.createLink(competition.getId(), "Rules PT", "https://pt.com", 1, "pt");
        var docAll = CompetitionDocument.createLink(competition.getId(), "Map", "https://map.com", 2, null);
        given(competitionDocumentRepository.findByCompetitionIdOrderByDisplayOrder(competition.getId()))
                .willReturn(List.of(docEn, docPt, docAll));

        var result = competitionService.getDocumentsForLocale(competition.getId(), java.util.Locale.ENGLISH);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CompetitionDocument::getName)
                .containsExactly("Rules EN", "Map");
    }

    // --- createDivision deadline ---

    @Test
    void shouldCreateDivisionWithDeadline() {
        var competition = createCompetition();
        var admin = createAdmin();
        var deadline = LocalDateTime.of(2026, 6, 15, 23, 59);
        var timezone = "Europe/Lisbon";
        given(competitionRepository.findById(competition.getId())).willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionRepository.save(any(Division.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.createDivision(
                competition.getId(), "Test", "test-div", ScoringSystem.MJP,
                deadline, timezone, admin.getId());

        assertThat(result.getRegistrationDeadline()).isEqualTo(deadline);
        assertThat(result.getRegistrationDeadlineTimezone()).isEqualTo(timezone);
    }

    // --- updateDivisionDeadline ---

    @Test
    void shouldUpdateDivisionDeadline() {
        var admin = createAdmin();
        var division = new Division(UUID.randomUUID(),
                "Home", "home", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        division.advanceStatus(); // DRAFT → REGISTRATION_OPEN
        given(divisionRepository.findById(division.getId())).willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionRepository.save(any())).willAnswer(i -> i.getArgument(0));

        var newDeadline = LocalDateTime.of(2026, 7, 1, 18, 0);
        var result = competitionService.updateDivisionDeadline(
                division.getId(), newDeadline, "Europe/Lisbon", admin.getId());

        assertThat(result.getRegistrationDeadline()).isEqualTo(newDeadline);
        assertThat(result.getRegistrationDeadlineTimezone()).isEqualTo("Europe/Lisbon");
    }

    @Test
    void shouldRejectInvalidTimezone() {
        var admin = createAdmin();
        var division = new Division(UUID.randomUUID(),
                "Home", "home", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        given(divisionRepository.findById(division.getId())).willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);

        assertThatThrownBy(() -> competitionService.updateDivisionDeadline(
                division.getId(), LocalDateTime.now(), "Invalid/Zone", admin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("error.division.invalid-timezone");
    }

    // --- findAdminEmailsByCompetitionId ---

    @Test
    void shouldFindAdminEmailsByCompetitionId() {
        var competition = createCompetition();
        var admin = createAdmin();
        var otherUser = mock(User.class);
        given(otherUser.getEmail()).willReturn("other-admin@test.com");
        var otherUserId = UUID.randomUUID();
        var adminParticipant = new Participant(competition.getId(), admin.getId());
        var otherParticipant = new Participant(competition.getId(), otherUserId);

        given(participantRepository.findByCompetitionId(competition.getId()))
                .willReturn(List.of(adminParticipant, otherParticipant));
        given(participantRoleRepository.existsByParticipantIdAndRole(
                adminParticipant.getId(), CompetitionRole.ADMIN)).willReturn(true);
        given(participantRoleRepository.existsByParticipantIdAndRole(
                otherParticipant.getId(), CompetitionRole.ADMIN)).willReturn(true);
        given(userService.findById(admin.getId())).willReturn(admin);
        given(userService.findById(otherUserId)).willReturn(otherUser);

        var emails = competitionService.findAdminEmailsByCompetitionId(competition.getId());

        assertThat(emails).containsExactlyInAnyOrder(admin.getEmail(), "other-admin@test.com");
    }

    @Test
    void shouldDeleteDocumentsWhenDeletingCompetition() {
        var admin = createAdmin();
        var competition = createCompetition();
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(divisionRepository.findByCompetitionId(competition.getId()))
                .willReturn(List.of());
        given(competitionDocumentRepository.findByCompetitionIdOrderByDisplayOrder(competition.getId()))
                .willReturn(List.of());

        competitionService.deleteCompetition(competition.getId(), admin.getId());

        then(competitionDocumentRepository).should().deleteAll(List.of());
        then(competitionRepository).should().delete(competition);
    }

}
