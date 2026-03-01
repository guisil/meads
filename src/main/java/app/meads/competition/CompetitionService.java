package app.meads.competition;

import app.meads.competition.internal.CategoryRepository;
import app.meads.competition.internal.CompetitionParticipantRepository;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.EventParticipantRepository;
import app.meads.competition.internal.MeadEventRepository;
import app.meads.identity.Role;
import app.meads.identity.UserService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.security.SecureRandom;
import java.time.LocalDate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Validated
public class CompetitionService {

    private static final String ACCESS_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int ACCESS_CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CompetitionRepository competitionRepository;
    private final CompetitionParticipantRepository participantRepository;
    private final EventParticipantRepository eventParticipantRepository;
    private final CategoryRepository categoryRepository;
    private final MeadEventRepository meadEventRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    CompetitionService(CompetitionRepository competitionRepository,
                       CompetitionParticipantRepository participantRepository,
                       EventParticipantRepository eventParticipantRepository,
                       CategoryRepository categoryRepository,
                       MeadEventRepository meadEventRepository,
                       UserService userService,
                       ApplicationEventPublisher eventPublisher) {
        this.competitionRepository = competitionRepository;
        this.participantRepository = participantRepository;
        this.eventParticipantRepository = eventParticipantRepository;
        this.categoryRepository = categoryRepository;
        this.meadEventRepository = meadEventRepository;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
    }

    public MeadEvent createEvent(@NotBlank String name,
                             @NotNull LocalDate startDate,
                             @NotNull LocalDate endDate,
                             String location,
                             @NotNull UUID requestingUserId) {
        requireSystemAdmin(requestingUserId);
        var event = new MeadEvent(name, startDate, endDate, location);
        return meadEventRepository.save(event);
    }

    public MeadEvent findEventById(@NotNull UUID eventId) {
        return meadEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
    }

    public List<MeadEvent> findAllEvents() {
        return meadEventRepository.findAll();
    }

    public MeadEvent updateEvent(@NotNull UUID eventId,
                              @NotBlank String name,
                              @NotNull LocalDate startDate,
                              @NotNull LocalDate endDate,
                              String location,
                              @NotNull UUID requestingUserId) {
        var event = meadEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        requireSystemAdmin(requestingUserId);
        event.updateDetails(name, startDate, endDate, location);
        return meadEventRepository.save(event);
    }

    public MeadEvent updateEventLogo(@NotNull UUID eventId,
                                  byte[] logo,
                                  String contentType,
                                  @NotNull UUID requestingUserId) {
        var event = meadEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        requireSystemAdmin(requestingUserId);
        event.updateLogo(logo, contentType);
        return meadEventRepository.save(event);
    }

    public void deleteEvent(@NotNull UUID eventId,
                             @NotNull UUID requestingUserId) {
        var event = meadEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        requireSystemAdmin(requestingUserId);
        var competitions = competitionRepository.findByEventId(eventId);
        if (!competitions.isEmpty()) {
            throw new IllegalArgumentException("Cannot delete event with competitions");
        }
        meadEventRepository.delete(event);
    }

    public Competition createCompetition(@NotNull UUID eventId,
                                         @NotBlank String name,
                                         @NotNull ScoringSystem scoringSystem,
                                         @NotNull UUID requestingUserId) {
        meadEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        requireSystemAdmin(requestingUserId);
        var competition = new Competition(eventId, name, scoringSystem);
        return competitionRepository.save(competition);
    }

    public Competition findById(@NotNull UUID competitionId) {
        return competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
    }

    public Competition advanceStatus(@NotNull UUID competitionId,
                                      @NotNull UUID requestingUserId) {
        var competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
        requireAuthorized(competition.getId(), requestingUserId);
        var previousStatus = competition.getStatus();
        competition.advanceStatus();
        var saved = competitionRepository.save(competition);
        eventPublisher.publishEvent(new CompetitionStatusAdvancedEvent(
                competitionId, previousStatus, saved.getStatus()));
        return saved;
    }

    public Competition updateCompetition(@NotNull UUID competitionId,
                                          @NotBlank String name,
                                          @NotNull ScoringSystem scoringSystem,
                                          @NotNull UUID requestingUserId) {
        var competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
        requireAuthorized(competition.getId(), requestingUserId);
        competition.updateDetails(name, scoringSystem);
        return competitionRepository.save(competition);
    }

    public List<Competition> findByEvent(@NotNull UUID eventId) {
        return competitionRepository.findByEventId(eventId);
    }

    public List<CompetitionParticipant> findParticipantsByCompetition(@NotNull UUID competitionId) {
        return participantRepository.findByCompetitionId(competitionId);
    }

    public List<Category> findCategoriesByScoringSystem(@NotNull ScoringSystem scoringSystem) {
        return categoryRepository.findByScoringSystem(scoringSystem);
    }

    public CompetitionParticipant addParticipant(@NotNull UUID competitionId,
                                                  @NotNull UUID userId,
                                                  @NotNull CompetitionRole role,
                                                  @NotNull UUID requestingUserId) {
        var competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
        requireAuthorized(competition.getId(), requestingUserId);
        userService.findById(userId);

        var eventParticipant = eventParticipantRepository
                .findByEventIdAndUserId(competition.getEventId(), userId)
                .orElseGet(() -> {
                    var ep = new EventParticipant(competition.getEventId(), userId);
                    if (role.requiresAccessCode()) {
                        ep.assignAccessCode(generateAccessCode());
                    }
                    return eventParticipantRepository.save(ep);
                });

        if (participantRepository.existsByCompetitionIdAndEventParticipantIdAndRole(
                competitionId, eventParticipant.getId(), role)) {
            throw new IllegalArgumentException(
                    "User already has the " + role.name() + " role in this competition");
        }

        var cp = new CompetitionParticipant(competitionId, eventParticipant.getId(), role);
        return participantRepository.save(cp);
    }

    public CompetitionParticipant addParticipantByEmail(@NotNull UUID competitionId,
                                                         @NotBlank @Email String email,
                                                         @NotNull CompetitionRole role,
                                                         @NotNull UUID requestingUserId) {
        var user = userService.findOrCreateByEmail(email);
        return addParticipant(competitionId, user.getId(), role, requestingUserId);
    }

    public void withdrawParticipant(@NotNull UUID competitionId,
                                     @NotNull UUID eventParticipantId,
                                     @NotNull UUID requestingUserId) {
        competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
        requireAuthorized(competitionId, requestingUserId);
        var eventParticipant = eventParticipantRepository.findById(eventParticipantId)
                .orElseThrow(() -> new IllegalArgumentException("Event participant not found"));
        eventParticipant.withdraw();
        eventParticipantRepository.save(eventParticipant);
    }

    public List<CompetitionParticipant> addParticipantToAllCompetitions(
            @NotNull UUID eventId,
            @NotNull UUID userId,
            @NotNull CompetitionRole role,
            @NotNull UUID requestingUserId) {
        requireSystemAdmin(requestingUserId);
        userService.findById(userId);

        var eventParticipant = eventParticipantRepository
                .findByEventIdAndUserId(eventId, userId)
                .orElseGet(() -> {
                    var ep = new EventParticipant(eventId, userId);
                    if (role.requiresAccessCode()) {
                        ep.assignAccessCode(generateAccessCode());
                    }
                    return eventParticipantRepository.save(ep);
                });

        var competitions = competitionRepository.findByEventId(eventId);
        var added = new java.util.ArrayList<CompetitionParticipant>();
        for (var competition : competitions) {
            if (participantRepository.existsByCompetitionIdAndEventParticipantIdAndRole(
                    competition.getId(), eventParticipant.getId(), role)) {
                continue;
            }
            var cp = new CompetitionParticipant(
                    competition.getId(), eventParticipant.getId(), role);
            added.add(participantRepository.save(cp));
        }
        return added;
    }

    public List<EventParticipant> findEventParticipantsByEvent(@NotNull UUID eventId) {
        return eventParticipantRepository.findByEventId(eventId);
    }

    public List<Competition> findAuthorizedCompetitions(@NotNull UUID eventId,
                                                         @NotNull UUID userId) {
        var user = userService.findById(userId);
        if (user.getRole() == Role.SYSTEM_ADMIN) {
            return competitionRepository.findByEventId(eventId);
        }
        var ep = eventParticipantRepository.findByEventIdAndUserId(eventId, userId);
        if (ep.isEmpty()) {
            return List.of();
        }
        var adminParticipants = participantRepository
                .findByEventParticipantIdAndRole(ep.get().getId(), CompetitionRole.COMPETITION_ADMIN);
        return adminParticipants.stream()
                .map(cp -> competitionRepository.findById(cp.getCompetitionId()))
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    public boolean isAuthorizedForCompetition(@NotNull UUID competitionId,
                                                @NotNull UUID userId) {
        try {
            requireAuthorized(competitionId, userId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String generateAccessCode() {
        var sb = new StringBuilder(ACCESS_CODE_LENGTH);
        for (int i = 0; i < ACCESS_CODE_LENGTH; i++) {
            sb.append(ACCESS_CODE_CHARS.charAt(RANDOM.nextInt(ACCESS_CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private void requireSystemAdmin(UUID userId) {
        var user = userService.findById(userId);
        if (user.getRole() != Role.SYSTEM_ADMIN) {
            throw new IllegalArgumentException("User is not authorized to perform this action");
        }
    }

    private void requireAuthorized(UUID competitionId, UUID userId) {
        var user = userService.findById(userId);
        if (user.getRole() == Role.SYSTEM_ADMIN) {
            return;
        }
        // Check if user is a COMPETITION_ADMIN for this competition
        var competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
        var ep = eventParticipantRepository
                .findByEventIdAndUserId(competition.getEventId(), userId);
        if (ep.isPresent()) {
            var roles = participantRepository.findByCompetitionIdAndEventParticipantId(
                    competitionId, ep.get().getId());
            if (roles.stream().anyMatch(
                    cp -> cp.getRole() == CompetitionRole.COMPETITION_ADMIN)) {
                return;
            }
        }
        throw new IllegalArgumentException("User is not authorized to perform this action");
    }
}
