package app.meads.competition;

import app.meads.competition.internal.CategoryRepository;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.DivisionCategoryRepository;
import app.meads.competition.internal.DivisionRepository;
import app.meads.competition.internal.ParticipantRepository;
import app.meads.competition.internal.ParticipantRoleRepository;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserService;
import app.meads.identity.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
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
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CompetitionServiceTest {

    @InjectMocks
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
    UserService userService;

    @Mock
    ApplicationEventPublisher eventPublisher;

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
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authorized");

        then(competitionRepository).should(never()).save(any());
    }

    // --- findAllCompetitions ---

    @Test
    void shouldFindAllCompetitions() {
        var competition = createCompetition();
        given(competitionRepository.findAll()).willReturn(List.of(competition));

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
    void shouldRejectUpdateCompetitionWhenUserNotSystemAdmin() {
        var user = createRegularUser();
        var competition = createCompetition();
        given(competitionRepository.findById(competition.getId())).willReturn(Optional.of(competition));
        given(userService.findById(user.getId())).willReturn(user);

        assertThatThrownBy(() -> competitionService.updateCompetition(
                competition.getId(), "Updated", "updated", LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 3), "Lisbon", user.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authorized");

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

    // --- deleteCompetition ---

    @Test
    void shouldDeleteCompetitionWhenNoDivisionsExist() {
        var admin = createAdmin();
        var competition = createCompetition();
        given(competitionRepository.findById(competition.getId())).willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionRepository.findByCompetitionId(competition.getId())).willReturn(List.of());

        competitionService.deleteCompetition(competition.getId(), admin.getId());

        then(competitionRepository).should().delete(competition);
    }

    @Test
    void shouldRejectDeleteCompetitionWhenDivisionsExist() {
        var admin = createAdmin();
        var competition = createCompetition();
        var division = new Division(competition.getId(),
                "Home", "home", ScoringSystem.MJP);
        given(competitionRepository.findById(competition.getId())).willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionRepository.findByCompetitionId(competition.getId()))
                .willReturn(List.of(division));

        assertThatThrownBy(() -> competitionService.deleteCompetition(
                competition.getId(), admin.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("divisions");

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
                competition.getId(), "Home", "home", ScoringSystem.MJP, admin.getId());

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
        var cat1 = org.mockito.Mockito.mock(Category.class);
        var cat2 = org.mockito.Mockito.mock(Category.class);
        given(cat1.getId()).willReturn(UUID.randomUUID());
        given(cat1.getCode()).willReturn("M1A");
        given(cat1.getName()).willReturn("Traditional Mead");
        given(cat1.getDescription()).willReturn("A traditional mead");
        given(cat2.getId()).willReturn(UUID.randomUUID());
        given(cat2.getCode()).willReturn("M1B");
        given(cat2.getName()).willReturn("Semi-Sweet Mead");
        given(cat2.getDescription()).willReturn("A semi-sweet mead");
        given(competitionRepository.findById(competition.getId())).willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionRepository.save(any(Division.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(categoryRepository.findByScoringSystem(ScoringSystem.MJP))
                .willReturn(List.of(cat1, cat2));
        given(divisionCategoryRepository.save(any(DivisionCategory.class)))
                .willAnswer(inv -> inv.getArgument(0));

        competitionService.createDivision(
                competition.getId(), "Home", "home", ScoringSystem.MJP, admin.getId());

        then(divisionCategoryRepository).should(org.mockito.Mockito.times(2))
                .save(any(DivisionCategory.class));
    }

    @Test
    void shouldRejectCreateDivisionWhenCompetitionNotFound() {
        var competitionId = UUID.randomUUID();
        var admin = createAdmin();
        given(competitionRepository.findById(competitionId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> competitionService.createDivision(
                competitionId, "Home", "home", ScoringSystem.MJP, admin.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Competition not found");

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
                competition.getId(), "Home", "home", ScoringSystem.MJP, user.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authorized");

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
                competition.getId(), "Home", "home", ScoringSystem.MJP, compAdmin.getId());

        assertThat(result.getName()).isEqualTo("Home");
    }

    // --- advanceDivisionStatus ---

    @Test
    void shouldAdvanceDivisionStatusAndPublishEvent() {
        var admin = createAdmin();
        var division = new Division(UUID.randomUUID(),
                "Home", "home", ScoringSystem.MJP);
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
                "Home", "home", ScoringSystem.MJP);
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.findByCompetitionIdAndUserId(
                division.getCompetitionId(), user.getId()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> competitionService.advanceDivisionStatus(
                division.getId(), user.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authorized");

        then(divisionRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    void shouldAllowCompetitionAdminToAdvanceDivisionStatus() {
        var compAdmin = createRegularUser();
        var competitionId = UUID.randomUUID();
        var division = new Division(competitionId, "Home", "home", ScoringSystem.MJP);
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

    // --- updateDivision ---

    @Test
    void shouldUpdateDivisionWhenInDraftAndRequestedByAdmin() {
        var admin = createAdmin();
        var division = new Division(UUID.randomUUID(),
                "Home", "home", ScoringSystem.MJP);
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionRepository.save(any(Division.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.updateDivision(
                division.getId(), "Updated Name", "updated-name", ScoringSystem.MJP, admin.getId());

        assertThat(result.getName()).isEqualTo("Updated Name");
        then(divisionRepository).should().save(division);
    }

    @Test
    void shouldRejectUpdateDivisionWhenUserNotAuthorized() {
        var user = createRegularUser();
        var division = new Division(UUID.randomUUID(),
                "Home", "home", ScoringSystem.MJP);
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.findByCompetitionIdAndUserId(
                division.getCompetitionId(), user.getId()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> competitionService.updateDivision(
                division.getId(), "Updated", "updated", ScoringSystem.MJP, user.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authorized");

        then(divisionRepository).should(never()).save(any());
    }

    @Test
    void shouldAllowCompetitionAdminToUpdateDivision() {
        var compAdmin = createRegularUser();
        var competitionId = UUID.randomUUID();
        var division = new Division(competitionId, "Home", "home", ScoringSystem.MJP);
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
                division.getId(), "Updated", "updated", ScoringSystem.MJP, compAdmin.getId());

        assertThat(result.getName()).isEqualTo("Updated");
        then(divisionRepository).should().save(division);
    }

    // --- deleteDivision ---

    @Test
    void shouldDeleteDivisionAndCleanUpCategories() {
        var admin = createAdmin();
        var division = new Division(UUID.randomUUID(),
                "Home", "home", ScoringSystem.MJP);
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
        given(participantRoleRepository.existsByParticipantIdAndRole(
                existingParticipant.getId(), CompetitionRole.JUDGE)).willReturn(false);
        given(participantRoleRepository.save(any(ParticipantRole.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addParticipant(
                competition.getId(), user.getId(), CompetitionRole.JUDGE, admin.getId());

        assertThat(result.getParticipantId()).isEqualTo(existingParticipant.getId());
        then(participantRepository).should(never()).save(any());
    }

    @Test
    void shouldAllowMultipleRolesForSameParticipant() {
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
        given(participantRoleRepository.save(any(ParticipantRole.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addParticipant(
                competition.getId(), user.getId(), CompetitionRole.ENTRANT, admin.getId());

        assertThat(result.getRole()).isEqualTo(CompetitionRole.ENTRANT);
        then(participantRoleRepository).should().save(any(ParticipantRole.class));
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
        given(participantRoleRepository.existsByParticipantIdAndRole(
                existingParticipant.getId(), CompetitionRole.JUDGE)).willReturn(true);

        assertThatThrownBy(() -> competitionService.addParticipant(
                competition.getId(), user.getId(), CompetitionRole.JUDGE, admin.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JUDGE");

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
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authorized");

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
        var div1 = new Division(competitionId, "Home", "home", ScoringSystem.MJP);
        var div2 = new Division(competitionId, "Pro", "pro", ScoringSystem.MJP);
        given(divisionRepository.findByCompetitionId(competitionId))
                .willReturn(List.of(div1, div2));

        var result = competitionService.findDivisionsByCompetition(competitionId);

        assertThat(result).hasSize(2);
        then(divisionRepository).should().findByCompetitionId(competitionId);
    }

    // --- isAuthorizedForDivision ---

    @Test
    void shouldReturnTrueWhenUserIsAdminForDivisionCompetition() {
        var admin = createAdmin();
        var division = new Division(UUID.randomUUID(), "Home", "home", ScoringSystem.MJP);
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
        var division = new Division(UUID.randomUUID(), "Home", "home", ScoringSystem.MJP);
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
        var div1 = new Division(competitionId, "Home", "home", ScoringSystem.MJP);
        var div2 = new Division(competitionId, "Pro", "pro", ScoringSystem.MJP);
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionRepository.findByCompetitionId(competitionId))
                .willReturn(List.of(div1, div2));

        var result = competitionService.findAuthorizedDivisions(
                competitionId, admin.getId());

        assertThat(result).hasSize(2);
    }

    @Test
    void shouldFindAuthorizedDivisionsForCompetitionAdmin() {
        var compAdmin = createRegularUser();
        var competitionId = UUID.randomUUID();
        var div1 = new Division(competitionId, "Home", "home", ScoringSystem.MJP);
        var div2 = new Division(competitionId, "Pro", "pro", ScoringSystem.MJP);
        var participant = new Participant(competitionId, compAdmin.getId());
        given(userService.findById(compAdmin.getId())).willReturn(compAdmin);
        given(participantRepository.findByCompetitionIdAndUserId(competitionId, compAdmin.getId()))
                .willReturn(Optional.of(participant));
        given(participantRoleRepository.existsByParticipantIdAndRole(
                participant.getId(), CompetitionRole.ADMIN))
                .willReturn(true);
        given(divisionRepository.findByCompetitionId(competitionId))
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
                "Home", "home", ScoringSystem.MJP);
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
                "Home", "home", ScoringSystem.MJP);
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
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already added");

        then(divisionCategoryRepository).should(never()).save(any());
    }

    // --- addCustomCategory ---

    @Test
    void shouldAddCustomCategory() {
        var admin = createAdmin();
        var division = new Division(UUID.randomUUID(),
                "Home", "home", ScoringSystem.MJP);
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
                "Home", "home", ScoringSystem.MJP);
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
                "Home", "home", ScoringSystem.MJP);
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
                "Home", "home", ScoringSystem.MJP);
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
                "Home", "home", ScoringSystem.MJP);
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
                "Home", "home", ScoringSystem.MJP);
        // Advance to REGISTRATION_OPEN then REGISTRATION_CLOSED
        division.advanceStatus(); // DRAFT -> REGISTRATION_OPEN
        division.advanceStatus(); // REGISTRATION_OPEN -> REGISTRATION_CLOSED
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);

        assertThatThrownBy(() -> competitionService.addCustomCategory(
                division.getId(), "CUSTOM", "Custom",
                "A custom category", null, admin.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be modified");

        assertThatThrownBy(() -> competitionService.addCatalogCategory(
                division.getId(), UUID.randomUUID(), admin.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be modified");

        assertThatThrownBy(() -> competitionService.removeDivisionCategory(
                division.getId(), UUID.randomUUID(), admin.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be modified");

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
                "Home", "home", ScoringSystem.MJP);
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(categoryRepository.findByScoringSystem(ScoringSystem.MJP))
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
                "Amadora", "amadora", ScoringSystem.MJP);
        given(divisionRepository.findById(division.getId()))
                .willReturn(Optional.of(division));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(divisionRepository.save(any(Division.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.updateDivisionEntryLimits(
                division.getId(), 3, 5, admin.getId());

        assertThat(result.getMaxEntriesPerSubcategory()).isEqualTo(3);
        assertThat(result.getMaxEntriesPerMainCategory()).isEqualTo(5);
        then(divisionRepository).should().save(division);
    }

}
