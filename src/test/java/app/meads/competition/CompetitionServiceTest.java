package app.meads.competition;

import app.meads.competition.internal.CategoryRepository;
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
    CategoryRepository categoryRepository;

    @Mock
    EventParticipantRepository eventParticipantRepository;

    @Mock
    MeadEventRepository meadEventRepository;

    @Mock
    UserService userService;

    @Mock
    ApplicationEventPublisher eventPublisher;

    private MeadEvent createEvent() {
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
        var event = createEvent();
        var admin = createAdmin();
        given(meadEventRepository.findById(event.getId())).willReturn(Optional.of(event));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionRepository.save(any(Competition.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.createCompetition(
                event.getId(), "Home", ScoringSystem.MJP, admin.getId());

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Home");
        assertThat(result.getEventId()).isEqualTo(event.getId());
        assertThat(result.getStatus()).isEqualTo(CompetitionStatus.DRAFT);
        assertThat(result.getScoringSystem()).isEqualTo(ScoringSystem.MJP);
        then(competitionRepository).should().save(any(Competition.class));
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
        var event = createEvent();
        var user = createRegularUser();
        given(meadEventRepository.findById(event.getId())).willReturn(Optional.of(event));
        given(userService.findById(user.getId())).willReturn(user);

        assertThatThrownBy(() -> competitionService.createCompetition(
                event.getId(), "Home", ScoringSystem.MJP, user.getId()))
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

    // --- withdrawParticipant ---

    @Test
    void shouldWithdrawEventParticipant() {
        var admin = createAdmin();
        var eventId = UUID.randomUUID();
        var competition = new Competition(eventId, "Home", ScoringSystem.MJP);
        var ep = new EventParticipant(eventId, UUID.randomUUID());
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(eventParticipantRepository.findById(ep.getId()))
                .willReturn(Optional.of(ep));
        given(eventParticipantRepository.save(any(EventParticipant.class)))
                .willAnswer(inv -> inv.getArgument(0));

        competitionService.withdrawParticipant(
                competition.getId(), ep.getId(), admin.getId());

        assertThat(ep.getStatus()).isEqualTo(CompetitionParticipantStatus.WITHDRAWN);
        then(eventParticipantRepository).should().save(ep);
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

    // --- createEvent ---

    @Test
    void shouldCreateEventWhenRequestedBySystemAdmin() {
        var admin = createAdmin();
        given(userService.findById(admin.getId())).willReturn(admin);
        given(meadEventRepository.save(any(MeadEvent.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.createEvent(
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
    void shouldRejectCreateEventWhenUserNotSystemAdmin() {
        var user = createRegularUser();
        given(userService.findById(user.getId())).willReturn(user);

        assertThatThrownBy(() -> competitionService.createEvent(
                "Regional 2026", LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 6, 17), "Porto", user.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authorized");

        then(meadEventRepository).should(never()).save(any());
    }

    // --- findAllEvents ---

    @Test
    void shouldFindAllEvents() {
        var event = createEvent();
        given(meadEventRepository.findAll()).willReturn(List.of(event));

        var result = competitionService.findAllEvents();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo("Test Event");
    }

    // --- updateEvent ---

    @Test
    void shouldUpdateEventWhenRequestedBySystemAdmin() {
        var admin = createAdmin();
        var event = createEvent();
        given(meadEventRepository.findById(event.getId())).willReturn(Optional.of(event));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(meadEventRepository.save(any(MeadEvent.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.updateEvent(
                event.getId(), "Updated Name", LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 3), "Lisbon", admin.getId());

        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getStartDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(result.getLocation()).isEqualTo("Lisbon");
        then(meadEventRepository).should().save(event);
    }

    @Test
    void shouldRejectUpdateEventWhenUserNotSystemAdmin() {
        var user = createRegularUser();
        var event = createEvent();
        given(meadEventRepository.findById(event.getId())).willReturn(Optional.of(event));
        given(userService.findById(user.getId())).willReturn(user);

        assertThatThrownBy(() -> competitionService.updateEvent(
                event.getId(), "Updated", LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 3), "Lisbon", user.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authorized");

        then(meadEventRepository).should(never()).save(any());
    }

    // --- updateEventLogo ---

    @Test
    void shouldUpdateEventLogoWhenRequestedBySystemAdmin() {
        var admin = createAdmin();
        var event = createEvent();
        given(meadEventRepository.findById(event.getId())).willReturn(Optional.of(event));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(meadEventRepository.save(any(MeadEvent.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var logo = new byte[]{1, 2, 3};
        var result = competitionService.updateEventLogo(
                event.getId(), logo, "image/png", admin.getId());

        assertThat(result.hasLogo()).isTrue();
        assertThat(result.getLogoContentType()).isEqualTo("image/png");
        then(meadEventRepository).should().save(event);
    }

    @Test
    void shouldClearEventLogoWhenNullProvided() {
        var admin = createAdmin();
        var event = createEvent();
        event.updateLogo(new byte[]{1, 2, 3}, "image/png");
        given(meadEventRepository.findById(event.getId())).willReturn(Optional.of(event));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(meadEventRepository.save(any(MeadEvent.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.updateEventLogo(
                event.getId(), null, null, admin.getId());

        assertThat(result.hasLogo()).isFalse();
    }

    // --- deleteEvent ---

    @Test
    void shouldDeleteEventWhenNoCompetitionsExist() {
        var admin = createAdmin();
        var event = createEvent();
        given(meadEventRepository.findById(event.getId())).willReturn(Optional.of(event));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionRepository.findByEventId(event.getId())).willReturn(List.of());

        competitionService.deleteEvent(event.getId(), admin.getId());

        then(meadEventRepository).should().delete(event);
    }

    @Test
    void shouldRejectDeleteEventWhenCompetitionsExist() {
        var admin = createAdmin();
        var event = createEvent();
        var competition = new Competition(event.getId(),
                "Home", ScoringSystem.MJP);
        given(meadEventRepository.findById(event.getId())).willReturn(Optional.of(event));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(competitionRepository.findByEventId(event.getId()))
                .willReturn(List.of(competition));

        assertThatThrownBy(() -> competitionService.deleteEvent(
                event.getId(), admin.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("competitions");

        then(meadEventRepository).should(never()).delete(any());
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
}
