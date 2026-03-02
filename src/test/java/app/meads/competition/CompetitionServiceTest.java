package app.meads.competition;

import app.meads.competition.internal.CategoryRepository;
import app.meads.competition.internal.CompetitionCategoryRepository;
import app.meads.competition.internal.CompetitionParticipantRepository;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.EventParticipantRepository;
import app.meads.competition.internal.MeadEventRepository;
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
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CompetitionServiceTest {

    @InjectMocks
    CompetitionService competitionService;

    @Mock
    CompetitionRepository competitionRepository;

    @Mock
    CompetitionParticipantRepository participantRepository;

    @Mock
    CompetitionCategoryRepository competitionCategoryRepository;

    @Mock
    CategoryRepository categoryRepository;

    @Mock
    EventParticipantRepository eventParticipantRepository;

    @Mock
    MeadEventRepository meadEventRepository;

    @Mock
    UserService userService;

    @Mock
    ApplicationEventPublisher eventPublisher;

    private MeadEvent createMeadEvent() {
        return new MeadEvent("Test Event",
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
        var meadEvent = createMeadEvent();
        var admin = createAdmin();
        given(meadEventRepository.findById(meadEvent.getId())).willReturn(Optional.of(meadEvent));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionRepository.save(any(Competition.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.createCompetition(
                meadEvent.getId(), "Home", ScoringSystem.MJP, admin.getId());

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Home");
        assertThat(result.getEventId()).isEqualTo(meadEvent.getId());
        assertThat(result.getStatus()).isEqualTo(CompetitionStatus.DRAFT);
        assertThat(result.getScoringSystem()).isEqualTo(ScoringSystem.MJP);
        then(competitionRepository).should().save(any(Competition.class));
    }

    @Test
    void shouldInitializeCategoriesOnCompetitionCreation() {
        var meadEvent = createMeadEvent();
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
        given(meadEventRepository.findById(meadEvent.getId())).willReturn(Optional.of(meadEvent));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionRepository.save(any(Competition.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(categoryRepository.findByScoringSystem(ScoringSystem.MJP))
                .willReturn(List.of(cat1, cat2));
        given(competitionCategoryRepository.save(any(CompetitionCategory.class)))
                .willAnswer(inv -> inv.getArgument(0));

        competitionService.createCompetition(
                meadEvent.getId(), "Home", ScoringSystem.MJP, admin.getId());

        then(competitionCategoryRepository).should(org.mockito.Mockito.times(2))
                .save(any(CompetitionCategory.class));
    }

    @Test
    void shouldRejectCreateCompetitionWhenEventNotFound() {
        var eventId = UUID.randomUUID();
        var admin = createAdmin();
        given(meadEventRepository.findById(eventId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> competitionService.createCompetition(
                eventId, "Home", ScoringSystem.MJP, admin.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event not found");

        then(competitionRepository).should(never()).save(any());
    }

    @Test
    void shouldRejectCreateCompetitionWhenUserNotSystemAdmin() {
        var meadEvent = createMeadEvent();
        var user = createRegularUser();
        given(meadEventRepository.findById(meadEvent.getId())).willReturn(Optional.of(meadEvent));
        given(userService.findById(user.getId())).willReturn(user);

        assertThatThrownBy(() -> competitionService.createCompetition(
                meadEvent.getId(), "Home", ScoringSystem.MJP, user.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authorized");

        then(competitionRepository).should(never()).save(any());
    }

    // --- advanceStatus ---

    @Test
    void shouldAdvanceStatusAndPublishEvent() {
        var admin = createAdmin();
        var competition = new Competition(UUID.randomUUID(),
                "Home", ScoringSystem.MJP);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionRepository.save(any(Competition.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.advanceStatus(competition.getId(), admin.getId());

        assertThat(result.getStatus()).isEqualTo(CompetitionStatus.REGISTRATION_OPEN);
        then(competitionRepository).should().save(competition);
        then(eventPublisher).should().publishEvent(any(CompetitionStatusAdvancedEvent.class));
    }

    @Test
    void shouldRejectAdvanceStatusWhenUserNotAuthorized() {
        var user = createRegularUser();
        var competition = new Competition(UUID.randomUUID(),
                "Home", ScoringSystem.MJP);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(user.getId())).willReturn(user);

        assertThatThrownBy(() -> competitionService.advanceStatus(
                competition.getId(), user.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authorized");

        then(competitionRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any());
    }

    // --- addParticipant ---

    @Test
    void shouldAddParticipantAndCreateEventParticipant() {
        var admin = createAdmin();
        var user = createRegularUser();
        var eventId = UUID.randomUUID();
        var competition = new Competition(eventId, "Home", ScoringSystem.MJP);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(userService.findById(user.getId())).willReturn(user);
        given(eventParticipantRepository.findByEventIdAndUserId(eventId, user.getId()))
                .willReturn(Optional.empty());
        given(eventParticipantRepository.save(any(EventParticipant.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(participantRepository.existsByCompetitionIdAndEventParticipantIdAndRole(
                any(), any(), any())).willReturn(false);
        given(participantRepository.save(any(CompetitionParticipant.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addParticipant(
                competition.getId(), user.getId(), CompetitionRole.JUDGE, admin.getId());

        assertThat(result.getCompetitionId()).isEqualTo(competition.getId());
        assertThat(result.getRole()).isEqualTo(CompetitionRole.JUDGE);
        then(eventParticipantRepository).should().save(any(EventParticipant.class));
        then(participantRepository).should().save(any(CompetitionParticipant.class));
    }

    @Test
    void shouldReuseExistingEventParticipantWhenAdding() {
        var admin = createAdmin();
        var user = createRegularUser();
        var eventId = UUID.randomUUID();
        var competition = new Competition(eventId, "Home", ScoringSystem.MJP);
        var existingEp = new EventParticipant(eventId, user.getId());
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(userService.findById(user.getId())).willReturn(user);
        given(eventParticipantRepository.findByEventIdAndUserId(eventId, user.getId()))
                .willReturn(Optional.of(existingEp));
        given(participantRepository.existsByCompetitionIdAndEventParticipantIdAndRole(
                competition.getId(), existingEp.getId(), CompetitionRole.JUDGE)).willReturn(false);
        given(participantRepository.save(any(CompetitionParticipant.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addParticipant(
                competition.getId(), user.getId(), CompetitionRole.JUDGE, admin.getId());

        assertThat(result.getEventParticipantId()).isEqualTo(existingEp.getId());
        then(eventParticipantRepository).should(never()).save(any());
    }

    @Test
    void shouldAllowMultipleRolesForSameUserInCompetition() {
        var admin = createAdmin();
        var user = createRegularUser();
        var eventId = UUID.randomUUID();
        var competition = new Competition(eventId, "Home", ScoringSystem.MJP);
        var existingEp = new EventParticipant(eventId, user.getId());
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(userService.findById(user.getId())).willReturn(user);
        given(eventParticipantRepository.findByEventIdAndUserId(eventId, user.getId()))
                .willReturn(Optional.of(existingEp));
        given(participantRepository.existsByCompetitionIdAndEventParticipantIdAndRole(
                competition.getId(), existingEp.getId(), CompetitionRole.ENTRANT)).willReturn(false);
        given(participantRepository.save(any(CompetitionParticipant.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addParticipant(
                competition.getId(), user.getId(), CompetitionRole.ENTRANT, admin.getId());

        assertThat(result.getRole()).isEqualTo(CompetitionRole.ENTRANT);
        then(participantRepository).should().save(any(CompetitionParticipant.class));
    }

    @Test
    void shouldRejectDuplicateRoleForSameParticipant() {
        var admin = createAdmin();
        var user = createRegularUser();
        var eventId = UUID.randomUUID();
        var competition = new Competition(eventId, "Home", ScoringSystem.MJP);
        var existingEp = new EventParticipant(eventId, user.getId());
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(userService.findById(user.getId())).willReturn(user);
        given(eventParticipantRepository.findByEventIdAndUserId(eventId, user.getId()))
                .willReturn(Optional.of(existingEp));
        given(participantRepository.existsByCompetitionIdAndEventParticipantIdAndRole(
                competition.getId(), existingEp.getId(), CompetitionRole.JUDGE)).willReturn(true);

        assertThatThrownBy(() -> competitionService.addParticipant(
                competition.getId(), user.getId(), CompetitionRole.JUDGE, admin.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JUDGE");

        then(participantRepository).should(never()).save(any());
    }

    @Test
    void shouldNotAssignAccessCodeForEntrant() {
        var admin = createAdmin();
        var user = createRegularUser();
        var eventId = UUID.randomUUID();
        var competition = new Competition(eventId, "Home", ScoringSystem.MJP);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(userService.findById(user.getId())).willReturn(user);
        given(eventParticipantRepository.findByEventIdAndUserId(eventId, user.getId()))
                .willReturn(Optional.empty());
        given(eventParticipantRepository.save(any(EventParticipant.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(participantRepository.existsByCompetitionIdAndEventParticipantIdAndRole(
                any(), any(), any())).willReturn(false);
        given(participantRepository.save(any(CompetitionParticipant.class)))
                .willAnswer(inv -> inv.getArgument(0));

        competitionService.addParticipant(
                competition.getId(), user.getId(), CompetitionRole.ENTRANT, admin.getId());

        then(eventParticipantRepository).should().save(argThat(
                (EventParticipant ep) -> ep.getAccessCode() == null));
    }

    @Test
    void shouldAllowCompetitionAdminToAddParticipant() {
        var compAdmin = createRegularUser();
        var user = new User("target@example.com", "Target",
                UserStatus.ACTIVE, Role.USER);
        var eventId = UUID.randomUUID();
        var competition = new Competition(eventId, "Home", ScoringSystem.MJP);
        var compAdminEp = new EventParticipant(eventId, compAdmin.getId());
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(compAdmin.getId())).willReturn(compAdmin);
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.findByCompetitionIdAndEventParticipantId(
                competition.getId(), compAdminEp.getId()))
                .willReturn(List.of(new CompetitionParticipant(
                        competition.getId(), compAdminEp.getId(),
                        CompetitionRole.COMPETITION_ADMIN)));
        given(eventParticipantRepository.findByEventIdAndUserId(eventId, compAdmin.getId()))
                .willReturn(Optional.of(compAdminEp));
        given(eventParticipantRepository.findByEventIdAndUserId(eventId, user.getId()))
                .willReturn(Optional.empty());
        given(eventParticipantRepository.save(any(EventParticipant.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(participantRepository.existsByCompetitionIdAndEventParticipantIdAndRole(
                any(), any(), any())).willReturn(false);
        given(participantRepository.save(any(CompetitionParticipant.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addParticipant(
                competition.getId(), user.getId(), CompetitionRole.JUDGE, compAdmin.getId());

        assertThat(result.getRole()).isEqualTo(CompetitionRole.JUDGE);
    }

    @Test
    void shouldRejectUnauthorizedUserForCompetitionOperations() {
        var user = createRegularUser();
        var eventId = UUID.randomUUID();
        var competition = new Competition(eventId, "Home", ScoringSystem.MJP);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(user.getId())).willReturn(user);
        given(eventParticipantRepository.findByEventIdAndUserId(eventId, user.getId()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> competitionService.addParticipant(
                competition.getId(), UUID.randomUUID(), CompetitionRole.JUDGE, user.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authorized");

        then(participantRepository).should(never()).save(any());
    }

    // --- removeParticipant ---

    @Test
    void shouldRemoveParticipantFromCompetition() {
        var admin = createAdmin();
        var eventId = UUID.randomUUID();
        var competition = new Competition(eventId, "Home", ScoringSystem.MJP);
        var ep = new EventParticipant(eventId, UUID.randomUUID());
        var cp = new CompetitionParticipant(competition.getId(), ep.getId(), CompetitionRole.JUDGE);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(participantRepository.findByCompetitionIdAndEventParticipantId(
                competition.getId(), ep.getId()))
                .willReturn(List.of(cp));

        competitionService.removeParticipant(
                competition.getId(), ep.getId(), admin.getId());

        then(participantRepository).should().deleteAll(List.of(cp));
    }

    // --- addParticipantToAllCompetitions ---

    @Test
    void shouldAddParticipantToAllCompetitionsInEvent() {
        var admin = createAdmin();
        var user = createRegularUser();
        var eventId = UUID.randomUUID();
        var comp1 = new Competition(eventId, "Home", ScoringSystem.MJP);
        var comp2 = new Competition(eventId, "Pro", ScoringSystem.MJP);
        var ep = new EventParticipant(eventId, user.getId());
        given(userService.findById(admin.getId())).willReturn(admin);
        given(userService.findById(user.getId())).willReturn(user);
        given(eventParticipantRepository.findByEventIdAndUserId(eventId, user.getId()))
                .willReturn(Optional.of(ep));
        given(competitionRepository.findByEventId(eventId))
                .willReturn(List.of(comp1, comp2));
        given(participantRepository.existsByCompetitionIdAndEventParticipantIdAndRole(
                comp1.getId(), ep.getId(), CompetitionRole.JUDGE)).willReturn(false);
        given(participantRepository.existsByCompetitionIdAndEventParticipantIdAndRole(
                comp2.getId(), ep.getId(), CompetitionRole.JUDGE)).willReturn(false);
        given(participantRepository.save(any(CompetitionParticipant.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addParticipantToAllCompetitions(
                eventId, user.getId(), CompetitionRole.JUDGE, admin.getId());

        assertThat(result).hasSize(2);
    }

    // --- findEventParticipantsByEvent ---

    @Test
    void shouldFindEventParticipantsByEvent() {
        var eventId = UUID.randomUUID();
        var ep = new EventParticipant(eventId, UUID.randomUUID());
        given(eventParticipantRepository.findByEventId(eventId))
                .willReturn(List.of(ep));

        var result = competitionService.findEventParticipantsByEvent(eventId);

        assertThat(result).hasSize(1);
        then(eventParticipantRepository).should().findByEventId(eventId);
    }

    // --- addParticipantByEmail ---

    @Test
    void shouldAddParticipantByEmailWhenUserExists() {
        var admin = createAdmin();
        var user = createRegularUser();
        var eventId = UUID.randomUUID();
        var competition = new Competition(eventId, "Home", ScoringSystem.MJP);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(userService.findOrCreateByEmail("user@example.com")).willReturn(user);
        given(eventParticipantRepository.findByEventIdAndUserId(eventId, user.getId()))
                .willReturn(Optional.empty());
        given(eventParticipantRepository.save(any(EventParticipant.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(participantRepository.existsByCompetitionIdAndEventParticipantIdAndRole(
                any(), any(), any())).willReturn(false);
        given(participantRepository.save(any(CompetitionParticipant.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addParticipantByEmail(
                competition.getId(), "user@example.com", CompetitionRole.JUDGE, admin.getId());

        assertThat(result.getCompetitionId()).isEqualTo(competition.getId());
        assertThat(result.getRole()).isEqualTo(CompetitionRole.JUDGE);
        then(userService).should().findOrCreateByEmail("user@example.com");
    }

    // --- createMeadEvent ---

    @Test
    void shouldCreateMeadEventWhenRequestedBySystemAdmin() {
        var admin = createAdmin();
        given(userService.findById(admin.getId())).willReturn(admin);
        given(meadEventRepository.save(any(MeadEvent.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.createMeadEvent(
                "Regional 2026", LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 6, 17), "Porto", admin.getId());

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Regional 2026");
        assertThat(result.getStartDate()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(result.getEndDate()).isEqualTo(LocalDate.of(2026, 6, 17));
        assertThat(result.getLocation()).isEqualTo("Porto");
        then(meadEventRepository).should().save(any(MeadEvent.class));
    }

    @Test
    void shouldRejectCreateMeadEventWhenUserNotSystemAdmin() {
        var user = createRegularUser();
        given(userService.findById(user.getId())).willReturn(user);

        assertThatThrownBy(() -> competitionService.createMeadEvent(
                "Regional 2026", LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 6, 17), "Porto", user.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authorized");

        then(meadEventRepository).should(never()).save(any());
    }

    // --- findAllMeadEvents ---

    @Test
    void shouldFindAllMeadEvents() {
        var meadEvent = createMeadEvent();
        given(meadEventRepository.findAll()).willReturn(List.of(meadEvent));

        var result = competitionService.findAllMeadEvents();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo("Test Event");
    }

    // --- updateMeadEvent ---

    @Test
    void shouldUpdateMeadEventWhenRequestedBySystemAdmin() {
        var admin = createAdmin();
        var meadEvent = createMeadEvent();
        given(meadEventRepository.findById(meadEvent.getId())).willReturn(Optional.of(meadEvent));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(meadEventRepository.save(any(MeadEvent.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.updateMeadEvent(
                meadEvent.getId(), "Updated Name", LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 3), "Lisbon", admin.getId());

        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getStartDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(result.getLocation()).isEqualTo("Lisbon");
        then(meadEventRepository).should().save(meadEvent);
    }

    @Test
    void shouldRejectUpdateMeadEventWhenUserNotSystemAdmin() {
        var user = createRegularUser();
        var meadEvent = createMeadEvent();
        given(meadEventRepository.findById(meadEvent.getId())).willReturn(Optional.of(meadEvent));
        given(userService.findById(user.getId())).willReturn(user);

        assertThatThrownBy(() -> competitionService.updateMeadEvent(
                meadEvent.getId(), "Updated", LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 3), "Lisbon", user.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authorized");

        then(meadEventRepository).should(never()).save(any());
    }

    // --- updateMeadEventLogo ---

    @Test
    void shouldUpdateMeadEventLogoWhenRequestedBySystemAdmin() {
        var admin = createAdmin();
        var meadEvent = createMeadEvent();
        given(meadEventRepository.findById(meadEvent.getId())).willReturn(Optional.of(meadEvent));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(meadEventRepository.save(any(MeadEvent.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var logo = new byte[]{1, 2, 3};
        var result = competitionService.updateMeadEventLogo(
                meadEvent.getId(), logo, "image/png", admin.getId());

        assertThat(result.hasLogo()).isTrue();
        assertThat(result.getLogoContentType()).isEqualTo("image/png");
        then(meadEventRepository).should().save(meadEvent);
    }

    @Test
    void shouldClearMeadEventLogoWhenNullProvided() {
        var admin = createAdmin();
        var meadEvent = createMeadEvent();
        meadEvent.updateLogo(new byte[]{1, 2, 3}, "image/png");
        given(meadEventRepository.findById(meadEvent.getId())).willReturn(Optional.of(meadEvent));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(meadEventRepository.save(any(MeadEvent.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.updateMeadEventLogo(
                meadEvent.getId(), null, null, admin.getId());

        assertThat(result.hasLogo()).isFalse();
    }

    // --- deleteMeadEvent ---

    @Test
    void shouldDeleteMeadEventWhenNoCompetitionsExist() {
        var admin = createAdmin();
        var meadEvent = createMeadEvent();
        given(meadEventRepository.findById(meadEvent.getId())).willReturn(Optional.of(meadEvent));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionRepository.findByEventId(meadEvent.getId())).willReturn(List.of());

        competitionService.deleteMeadEvent(meadEvent.getId(), admin.getId());

        then(meadEventRepository).should().delete(meadEvent);
    }

    @Test
    void shouldRejectDeleteMeadEventWhenCompetitionsExist() {
        var admin = createAdmin();
        var meadEvent = createMeadEvent();
        var competition = new Competition(meadEvent.getId(),
                "Home", ScoringSystem.MJP);
        given(meadEventRepository.findById(meadEvent.getId())).willReturn(Optional.of(meadEvent));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionRepository.findByEventId(meadEvent.getId()))
                .willReturn(List.of(competition));

        assertThatThrownBy(() -> competitionService.deleteMeadEvent(
                meadEvent.getId(), admin.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("competitions");

        then(meadEventRepository).should(never()).delete(any());
    }

    // --- deleteCompetition ---

    @Test
    void shouldDeleteCompetitionAndCleanUpRelatedData() {
        var admin = createAdmin();
        var competition = new Competition(UUID.randomUUID(),
                "Home", ScoringSystem.MJP);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);

        var participant = new CompetitionParticipant(
                competition.getId(), UUID.randomUUID(), CompetitionRole.JUDGE);
        given(participantRepository.findByCompetitionId(competition.getId()))
                .willReturn(List.of(participant));

        var category = new CompetitionCategory(
                competition.getId(), UUID.randomUUID(), "T1", "Trad", "Trad mead", null, 0);
        given(competitionCategoryRepository.findByCompetitionIdOrderBySortOrder(competition.getId()))
                .willReturn(List.of(category));

        competitionService.deleteCompetition(competition.getId(), admin.getId());

        then(participantRepository).should().deleteAll(List.of(participant));
        then(competitionCategoryRepository).should().deleteAll(List.of(category));
        then(competitionRepository).should().delete(competition);
    }

    // --- authorization: updateCompetition / advanceStatus ---

    @Test
    void shouldAllowCompetitionAdminToUpdateCompetition() {
        var compAdmin = createRegularUser();
        var eventId = UUID.randomUUID();
        var competition = new Competition(eventId, "Home", ScoringSystem.MJP);
        var compAdminEp = new EventParticipant(eventId, compAdmin.getId());
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(compAdmin.getId())).willReturn(compAdmin);
        given(eventParticipantRepository.findByEventIdAndUserId(eventId, compAdmin.getId()))
                .willReturn(Optional.of(compAdminEp));
        given(participantRepository.findByCompetitionIdAndEventParticipantId(
                competition.getId(), compAdminEp.getId()))
                .willReturn(List.of(new CompetitionParticipant(
                        competition.getId(), compAdminEp.getId(),
                        CompetitionRole.COMPETITION_ADMIN)));
        given(competitionRepository.save(any(Competition.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.updateCompetition(
                competition.getId(), "Updated", ScoringSystem.MJP, compAdmin.getId());

        assertThat(result.getName()).isEqualTo("Updated");
        then(competitionRepository).should().save(competition);
    }

    @Test
    void shouldAllowCompetitionAdminToAdvanceStatus() {
        var compAdmin = createRegularUser();
        var eventId = UUID.randomUUID();
        var competition = new Competition(eventId, "Home", ScoringSystem.MJP);
        var compAdminEp = new EventParticipant(eventId, compAdmin.getId());
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(compAdmin.getId())).willReturn(compAdmin);
        given(eventParticipantRepository.findByEventIdAndUserId(eventId, compAdmin.getId()))
                .willReturn(Optional.of(compAdminEp));
        given(participantRepository.findByCompetitionIdAndEventParticipantId(
                competition.getId(), compAdminEp.getId()))
                .willReturn(List.of(new CompetitionParticipant(
                        competition.getId(), compAdminEp.getId(),
                        CompetitionRole.COMPETITION_ADMIN)));
        given(competitionRepository.save(any(Competition.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.advanceStatus(
                competition.getId(), compAdmin.getId());

        assertThat(result.getStatus()).isEqualTo(CompetitionStatus.REGISTRATION_OPEN);
        then(eventPublisher).should().publishEvent(any(CompetitionStatusAdvancedEvent.class));
    }

    // --- isAuthorizedForCompetition ---

    @Test
    void shouldReturnTrueWhenAuthorizedForCompetition() {
        var admin = createAdmin();
        var competition = new Competition(UUID.randomUUID(), "Home", ScoringSystem.MJP);
        given(userService.findById(admin.getId())).willReturn(admin);

        var result = competitionService.isAuthorizedForCompetition(
                competition.getId(), admin.getId());

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenNotAuthorizedForCompetition() {
        var user = createRegularUser();
        var eventId = UUID.randomUUID();
        var competition = new Competition(eventId, "Home", ScoringSystem.MJP);
        given(userService.findById(user.getId())).willReturn(user);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(eventParticipantRepository.findByEventIdAndUserId(eventId, user.getId()))
                .willReturn(Optional.empty());

        var result = competitionService.isAuthorizedForCompetition(
                competition.getId(), user.getId());

        assertThat(result).isFalse();
    }

    // --- findAuthorizedCompetitions ---

    @Test
    void shouldFindAuthorizedCompetitionsForSystemAdmin() {
        var admin = createAdmin();
        var eventId = UUID.randomUUID();
        var comp1 = new Competition(eventId, "Home", ScoringSystem.MJP);
        var comp2 = new Competition(eventId, "Pro", ScoringSystem.MJP);
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionRepository.findByEventId(eventId))
                .willReturn(List.of(comp1, comp2));

        var result = competitionService.findAuthorizedCompetitions(
                eventId, admin.getId());

        assertThat(result).hasSize(2);
    }

    @Test
    void shouldFindAuthorizedCompetitionsForCompetitionAdmin() {
        var compAdmin = createRegularUser();
        var eventId = UUID.randomUUID();
        var comp1 = new Competition(eventId, "Home", ScoringSystem.MJP);
        var comp2 = new Competition(eventId, "Pro", ScoringSystem.MJP);
        var compAdminEp = new EventParticipant(eventId, compAdmin.getId());
        var adminCp = new CompetitionParticipant(
                comp1.getId(), compAdminEp.getId(), CompetitionRole.COMPETITION_ADMIN);
        given(userService.findById(compAdmin.getId())).willReturn(compAdmin);
        given(eventParticipantRepository.findByEventIdAndUserId(eventId, compAdmin.getId()))
                .willReturn(Optional.of(compAdminEp));
        given(participantRepository.findByEventParticipantIdAndRole(
                compAdminEp.getId(), CompetitionRole.COMPETITION_ADMIN))
                .willReturn(List.of(adminCp));
        given(competitionRepository.findById(comp1.getId()))
                .willReturn(Optional.of(comp1));

        var result = competitionService.findAuthorizedCompetitions(
                eventId, compAdmin.getId());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(comp1.getId());
    }

    // --- findCategoriesByScoringSystem ---

    @Test
    void shouldFindCategoriesByScoringSystem() {
        var category = new Category();
        given(categoryRepository.findByScoringSystem(ScoringSystem.MJP))
                .willReturn(List.of(category));

        var result = competitionService.findCategoriesByScoringSystem(ScoringSystem.MJP);

        assertThat(result).hasSize(1);
        then(categoryRepository).should().findByScoringSystem(ScoringSystem.MJP);
    }

    // --- updateCompetition ---

    @Test
    void shouldUpdateCompetitionWhenInDraftAndRequestedByAdmin() {
        var admin = createAdmin();
        var competition = new Competition(UUID.randomUUID(),
                "Home", ScoringSystem.MJP);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionRepository.save(any(Competition.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.updateCompetition(
                competition.getId(), "Updated Name", ScoringSystem.MJP, admin.getId());

        assertThat(result.getName()).isEqualTo("Updated Name");
        then(competitionRepository).should().save(competition);
    }

    @Test
    void shouldRejectUpdateCompetitionWhenUserNotAuthorized() {
        var user = createRegularUser();
        var competition = new Competition(UUID.randomUUID(),
                "Home", ScoringSystem.MJP);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(user.getId())).willReturn(user);

        assertThatThrownBy(() -> competitionService.updateCompetition(
                competition.getId(), "Updated", ScoringSystem.MJP, user.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authorized");

        then(competitionRepository).should(never()).save(any());
    }

    // --- initializeCompetitionCategories ---

    // --- findCompetitionCategories ---

    @Test
    void shouldFindCompetitionCategories() {
        var competitionId = UUID.randomUUID();
        var cc1 = new CompetitionCategory(competitionId, null,
                "M1A", "Traditional Mead", "A traditional mead", null, 0);
        var cc2 = new CompetitionCategory(competitionId, null,
                "M1B", "Semi-Sweet Mead", "A semi-sweet mead", null, 1);
        given(competitionCategoryRepository.findByCompetitionIdOrderBySortOrder(competitionId))
                .willReturn(List.of(cc1, cc2));

        var result = competitionService.findCompetitionCategories(competitionId);

        assertThat(result).hasSize(2);
        then(competitionCategoryRepository).should()
                .findByCompetitionIdOrderBySortOrder(competitionId);
    }

    // --- addCatalogCategory ---

    @Test
    void shouldAddCatalogCategoryToCompetition() {
        var admin = createAdmin();
        var competition = new Competition(UUID.randomUUID(),
                "Home", ScoringSystem.MJP);
        var catalogCat = new Category();
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(categoryRepository.findById(catalogCat.getId()))
                .willReturn(Optional.of(catalogCat));
        given(competitionCategoryRepository.existsByCompetitionIdAndCatalogCategoryId(
                competition.getId(), catalogCat.getId())).willReturn(false);
        given(competitionCategoryRepository.save(any(CompetitionCategory.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addCatalogCategory(
                competition.getId(), catalogCat.getId(), admin.getId());

        assertThat(result).isNotNull();
        assertThat(result.getCompetitionId()).isEqualTo(competition.getId());
        assertThat(result.getCatalogCategoryId()).isEqualTo(catalogCat.getId());
        then(competitionCategoryRepository).should().save(any(CompetitionCategory.class));
    }

    @Test
    void shouldRejectDuplicateCatalogCategory() {
        var admin = createAdmin();
        var competition = new Competition(UUID.randomUUID(),
                "Home", ScoringSystem.MJP);
        var catalogCat = new Category();
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(categoryRepository.findById(catalogCat.getId()))
                .willReturn(Optional.of(catalogCat));
        given(competitionCategoryRepository.existsByCompetitionIdAndCatalogCategoryId(
                competition.getId(), catalogCat.getId())).willReturn(true);

        assertThatThrownBy(() -> competitionService.addCatalogCategory(
                competition.getId(), catalogCat.getId(), admin.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already added");

        then(competitionCategoryRepository).should(never()).save(any());
    }

    // --- addCustomCategory ---

    @Test
    void shouldAddCustomCategory() {
        var admin = createAdmin();
        var competition = new Competition(UUID.randomUUID(),
                "Home", ScoringSystem.MJP);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionCategoryRepository.existsByCompetitionIdAndCode(
                competition.getId(), "CUSTOM1")).willReturn(false);
        given(competitionCategoryRepository.save(any(CompetitionCategory.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addCustomCategory(
                competition.getId(), "CUSTOM1", "Best Local Honey",
                "Mead made with local honey", null, admin.getId());

        assertThat(result).isNotNull();
        assertThat(result.getCompetitionId()).isEqualTo(competition.getId());
        assertThat(result.getCatalogCategoryId()).isNull();
        assertThat(result.getCode()).isEqualTo("CUSTOM1");
        assertThat(result.getName()).isEqualTo("Best Local Honey");
        assertThat(result.getParentId()).isNull();
        then(competitionCategoryRepository).should().save(any(CompetitionCategory.class));
    }

    @Test
    void shouldAddCustomSubcategory() {
        var admin = createAdmin();
        var competition = new Competition(UUID.randomUUID(),
                "Home", ScoringSystem.MJP);
        var parent = new CompetitionCategory(competition.getId(), null,
                "M2E", "Other Fruit Melomel", "Other fruits", null, 0);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionCategoryRepository.existsByCompetitionIdAndCode(
                competition.getId(), "M2E-T")).willReturn(false);
        given(competitionCategoryRepository.findById(parent.getId()))
                .willReturn(Optional.of(parent));
        given(competitionCategoryRepository.save(any(CompetitionCategory.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addCustomCategory(
                competition.getId(), "M2E-T", "Tropical",
                "Tropical fruit melomel", parent.getId(), admin.getId());

        assertThat(result.getParentId()).isEqualTo(parent.getId());
        assertThat(result.getCatalogCategoryId()).isNull();
        then(competitionCategoryRepository).should().save(any(CompetitionCategory.class));
    }

    // --- removeCompetitionCategory ---

    @Test
    void shouldRemoveCompetitionCategory() {
        var admin = createAdmin();
        var competition = new Competition(UUID.randomUUID(),
                "Home", ScoringSystem.MJP);
        var cc = new CompetitionCategory(competition.getId(), null,
                "M1A", "Traditional Mead", "A traditional mead", null, 0);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionCategoryRepository.findById(cc.getId()))
                .willReturn(Optional.of(cc));
        given(competitionCategoryRepository.findByParentId(cc.getId()))
                .willReturn(List.of());

        competitionService.removeCompetitionCategory(
                competition.getId(), cc.getId(), admin.getId());

        then(competitionCategoryRepository).should().delete(cc);
    }

    @Test
    void shouldRemoveCompetitionCategoryWithSubcategories() {
        var admin = createAdmin();
        var competition = new Competition(UUID.randomUUID(),
                "Home", ScoringSystem.MJP);
        var parent = new CompetitionCategory(competition.getId(), null,
                "M2E", "Other Fruit Melomel", "Other fruits", null, 0);
        var child = new CompetitionCategory(competition.getId(), null,
                "M2E-T", "Tropical", "Tropical fruit melomel", parent.getId(), 1);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionCategoryRepository.findById(parent.getId()))
                .willReturn(Optional.of(parent));
        given(competitionCategoryRepository.findByParentId(parent.getId()))
                .willReturn(List.of(child));

        competitionService.removeCompetitionCategory(
                competition.getId(), parent.getId(), admin.getId());

        then(competitionCategoryRepository).should().deleteAll(List.of(child));
        then(competitionCategoryRepository).should().delete(parent);
    }

    // --- status guard ---

    @Test
    void shouldRejectCategoryChangeAfterRegistrationClosed() {
        var admin = createAdmin();
        var competition = new Competition(UUID.randomUUID(),
                "Home", ScoringSystem.MJP);
        // Advance to REGISTRATION_OPEN then REGISTRATION_CLOSED
        competition.advanceStatus(); // DRAFT -> REGISTRATION_OPEN
        competition.advanceStatus(); // REGISTRATION_OPEN -> REGISTRATION_CLOSED
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);

        assertThatThrownBy(() -> competitionService.addCustomCategory(
                competition.getId(), "CUSTOM", "Custom",
                "A custom category", null, admin.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be modified");

        assertThatThrownBy(() -> competitionService.addCatalogCategory(
                competition.getId(), UUID.randomUUID(), admin.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be modified");

        assertThatThrownBy(() -> competitionService.removeCompetitionCategory(
                competition.getId(), UUID.randomUUID(), admin.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be modified");

        then(competitionCategoryRepository).should(never()).save(any());
        then(competitionCategoryRepository).should(never()).delete(any());
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
        var competition = new Competition(UUID.randomUUID(),
                "Home", ScoringSystem.MJP);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(categoryRepository.findByScoringSystem(ScoringSystem.MJP))
                .willReturn(List.of(cat1, cat2, cat3));
        // cat1 already added, cat2 and cat3 not
        given(competitionCategoryRepository.existsByCompetitionIdAndCatalogCategoryId(
                competition.getId(), cat1Id)).willReturn(true);
        given(competitionCategoryRepository.existsByCompetitionIdAndCatalogCategoryId(
                competition.getId(), cat2Id)).willReturn(false);
        given(competitionCategoryRepository.existsByCompetitionIdAndCatalogCategoryId(
                competition.getId(), cat3Id)).willReturn(false);

        var result = competitionService.findAvailableCatalogCategories(competition.getId());

        assertThat(result).hasSize(2);
        assertThat(result).contains(cat2, cat3);
        assertThat(result).doesNotContain(cat1);
    }

}
