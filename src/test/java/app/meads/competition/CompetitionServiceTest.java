package app.meads.competition;

import app.meads.competition.internal.CompetitionParticipantRepository;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.EventRepository;
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
    EventRepository eventRepository;

    @Mock
    UserService userService;

    @Mock
    ApplicationEventPublisher eventPublisher;

    private Event createEvent() {
        return new Event(UUID.randomUUID(), "Test Event",
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 17), "Porto");
    }

    private User createAdmin() {
        return new User(UUID.randomUUID(), "admin@example.com", "Admin",
                UserStatus.ACTIVE, Role.SYSTEM_ADMIN);
    }

    private User createRegularUser() {
        return new User(UUID.randomUUID(), "user@example.com", "User",
                UserStatus.ACTIVE, Role.USER);
    }

    // --- createCompetition ---

    @Test
    void shouldCreateCompetitionWhenRequestedBySystemAdmin() {
        var event = createEvent();
        var admin = createAdmin();
        given(eventRepository.findById(event.getId())).willReturn(Optional.of(event));
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
        given(eventRepository.findById(eventId)).willReturn(Optional.empty());

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
        given(eventRepository.findById(event.getId())).willReturn(Optional.of(event));
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
        var competition = new Competition(UUID.randomUUID(), UUID.randomUUID(),
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
        var competition = new Competition(UUID.randomUUID(), UUID.randomUUID(),
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
    void shouldAddParticipantAndGenerateAccessCodeForJudge() {
        var admin = createAdmin();
        var user = createRegularUser();
        var competition = new Competition(UUID.randomUUID(), UUID.randomUUID(),
                "Home", ScoringSystem.MJP);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.existsByCompetitionIdAndUserId(
                competition.getId(), user.getId())).willReturn(false);
        given(participantRepository.save(any(CompetitionParticipant.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addParticipant(
                competition.getId(), user.getId(), CompetitionRole.JUDGE, admin.getId());

        assertThat(result.getCompetitionId()).isEqualTo(competition.getId());
        assertThat(result.getUserId()).isEqualTo(user.getId());
        assertThat(result.getRole()).isEqualTo(CompetitionRole.JUDGE);
        assertThat(result.getAccessCode()).isNotNull();
        assertThat(result.getAccessCode()).hasSize(8);
        then(participantRepository).should().save(any(CompetitionParticipant.class));
    }

    @Test
    void shouldAddParticipantWithoutAccessCodeForEntrant() {
        var admin = createAdmin();
        var user = createRegularUser();
        var competition = new Competition(UUID.randomUUID(), UUID.randomUUID(),
                "Home", ScoringSystem.MJP);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.existsByCompetitionIdAndUserId(
                competition.getId(), user.getId())).willReturn(false);
        given(participantRepository.save(any(CompetitionParticipant.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.addParticipant(
                competition.getId(), user.getId(), CompetitionRole.ENTRANT, admin.getId());

        assertThat(result.getRole()).isEqualTo(CompetitionRole.ENTRANT);
        assertThat(result.getAccessCode()).isNull();
    }

    @Test
    void shouldRejectAddParticipantWhenAlreadyExists() {
        var admin = createAdmin();
        var user = createRegularUser();
        var competition = new Competition(UUID.randomUUID(), UUID.randomUUID(),
                "Home", ScoringSystem.MJP);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(userService.findById(user.getId())).willReturn(user);
        given(participantRepository.existsByCompetitionIdAndUserId(
                competition.getId(), user.getId())).willReturn(true);

        assertThatThrownBy(() -> competitionService.addParticipant(
                competition.getId(), user.getId(), CompetitionRole.JUDGE, admin.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already a participant");

        then(participantRepository).should(never()).save(any());
    }

    @Test
    void shouldRejectAddParticipantWhenUserNotAuthorized() {
        var regularUser = createRegularUser();
        var targetUser = new User(UUID.randomUUID(), "target@example.com", "Target",
                UserStatus.ACTIVE, Role.USER);
        var competition = new Competition(UUID.randomUUID(), UUID.randomUUID(),
                "Home", ScoringSystem.MJP);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(regularUser.getId())).willReturn(regularUser);

        assertThatThrownBy(() -> competitionService.addParticipant(
                competition.getId(), targetUser.getId(), CompetitionRole.JUDGE,
                regularUser.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authorized");

        then(participantRepository).should(never()).save(any());
    }

    // --- withdrawParticipant ---

    @Test
    void shouldWithdrawParticipant() {
        var admin = createAdmin();
        var competition = new Competition(UUID.randomUUID(), UUID.randomUUID(),
                "Home", ScoringSystem.MJP);
        var participantId = UUID.randomUUID();
        var participant = new CompetitionParticipant(participantId,
                competition.getId(), UUID.randomUUID(), CompetitionRole.JUDGE);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(participantRepository.findById(participantId))
                .willReturn(Optional.of(participant));
        given(participantRepository.save(any(CompetitionParticipant.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.withdrawParticipant(
                competition.getId(), participantId, admin.getId());

        assertThat(result.getStatus()).isEqualTo(CompetitionParticipantStatus.WITHDRAWN);
        then(participantRepository).should().save(participant);
    }

    @Test
    void shouldWithdrawParticipantEvenAfterRegistrationClosed() {
        var admin = createAdmin();
        var competition = new Competition(UUID.randomUUID(), UUID.randomUUID(),
                "Home", ScoringSystem.MJP);
        competition.advanceStatus(); // REGISTRATION_OPEN
        competition.advanceStatus(); // REGISTRATION_CLOSED
        var participantId = UUID.randomUUID();
        var participant = new CompetitionParticipant(participantId,
                competition.getId(), UUID.randomUUID(), CompetitionRole.JUDGE);
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(participantRepository.findById(participantId))
                .willReturn(Optional.of(participant));
        given(participantRepository.save(any(CompetitionParticipant.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.withdrawParticipant(
                competition.getId(), participantId, admin.getId());

        assertThat(result.getStatus()).isEqualTo(CompetitionParticipantStatus.WITHDRAWN);
    }

    @Test
    void shouldRejectWithdrawParticipantWhenUserNotAuthorized() {
        var user = createRegularUser();
        var competition = new Competition(UUID.randomUUID(), UUID.randomUUID(),
                "Home", ScoringSystem.MJP);
        var participantId = UUID.randomUUID();
        given(competitionRepository.findById(competition.getId()))
                .willReturn(Optional.of(competition));
        given(userService.findById(user.getId())).willReturn(user);

        assertThatThrownBy(() -> competitionService.withdrawParticipant(
                competition.getId(), participantId, user.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authorized");

        then(participantRepository).should(never()).save(any());
    }

    // --- copyParticipants ---

    @Test
    void shouldCopyParticipantsBetweenCompetitionsInSameEvent() {
        var admin = createAdmin();
        var eventId = UUID.randomUUID();
        var source = new Competition(UUID.randomUUID(), eventId, "Home", ScoringSystem.MJP);
        var target = new Competition(UUID.randomUUID(), eventId, "Pro", ScoringSystem.MJP);
        var userId = UUID.randomUUID();
        var sourceParticipant = new CompetitionParticipant(UUID.randomUUID(),
                source.getId(), userId, CompetitionRole.JUDGE);
        given(competitionRepository.findById(source.getId()))
                .willReturn(Optional.of(source));
        given(competitionRepository.findById(target.getId()))
                .willReturn(Optional.of(target));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(participantRepository.findByCompetitionId(source.getId()))
                .willReturn(List.of(sourceParticipant));
        given(participantRepository.existsByCompetitionIdAndUserId(target.getId(), userId))
                .willReturn(false);
        given(participantRepository.save(any(CompetitionParticipant.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = competitionService.copyParticipants(
                source.getId(), target.getId(), admin.getId());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getCompetitionId()).isEqualTo(target.getId());
        assertThat(result.getFirst().getUserId()).isEqualTo(userId);
        assertThat(result.getFirst().getRole()).isEqualTo(CompetitionRole.JUDGE);
        assertThat(result.getFirst().getAccessCode()).isNotNull().hasSize(8);
    }

    @Test
    void shouldSkipExistingParticipantsWhenCopying() {
        var admin = createAdmin();
        var eventId = UUID.randomUUID();
        var source = new Competition(UUID.randomUUID(), eventId, "Home", ScoringSystem.MJP);
        var target = new Competition(UUID.randomUUID(), eventId, "Pro", ScoringSystem.MJP);
        var userId = UUID.randomUUID();
        var sourceParticipant = new CompetitionParticipant(UUID.randomUUID(),
                source.getId(), userId, CompetitionRole.JUDGE);
        given(competitionRepository.findById(source.getId()))
                .willReturn(Optional.of(source));
        given(competitionRepository.findById(target.getId()))
                .willReturn(Optional.of(target));
        given(userService.findById(admin.getId())).willReturn(admin);
        given(participantRepository.findByCompetitionId(source.getId()))
                .willReturn(List.of(sourceParticipant));
        given(participantRepository.existsByCompetitionIdAndUserId(target.getId(), userId))
                .willReturn(true);

        var result = competitionService.copyParticipants(
                source.getId(), target.getId(), admin.getId());

        assertThat(result).isEmpty();
        then(participantRepository).should(never()).save(any());
    }

    @Test
    void shouldRejectCopyParticipantsBetweenDifferentEvents() {
        var admin = createAdmin();
        var source = new Competition(UUID.randomUUID(), UUID.randomUUID(),
                "Home", ScoringSystem.MJP);
        var target = new Competition(UUID.randomUUID(), UUID.randomUUID(),
                "Pro", ScoringSystem.MJP);
        given(competitionRepository.findById(source.getId()))
                .willReturn(Optional.of(source));
        given(competitionRepository.findById(target.getId()))
                .willReturn(Optional.of(target));
        given(userService.findById(admin.getId())).willReturn(admin);

        assertThatThrownBy(() -> competitionService.copyParticipants(
                source.getId(), target.getId(), admin.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same event");

        then(participantRepository).should(never()).save(any());
    }
}
