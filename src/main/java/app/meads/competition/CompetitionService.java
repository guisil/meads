package app.meads.competition;

import app.meads.competition.internal.CompetitionParticipantRepository;
import app.meads.competition.internal.CompetitionRepository;
import app.meads.competition.internal.EventRepository;
import app.meads.identity.Role;
import app.meads.identity.UserService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.security.SecureRandom;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
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
    private final EventRepository eventRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    CompetitionService(CompetitionRepository competitionRepository,
                       CompetitionParticipantRepository participantRepository,
                       EventRepository eventRepository,
                       UserService userService,
                       ApplicationEventPublisher eventPublisher) {
        this.competitionRepository = competitionRepository;
        this.participantRepository = participantRepository;
        this.eventRepository = eventRepository;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
    }

    public Competition createCompetition(@NotNull UUID eventId,
                                         @NotBlank String name,
                                         @NotNull ScoringSystem scoringSystem,
                                         @NotNull UUID requestingUserId) {
        eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        requireSystemAdmin(requestingUserId);
        var competition = new Competition(UUID.randomUUID(), eventId, name, scoringSystem);
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
        requireSystemAdmin(requestingUserId);
        var previousStatus = competition.getStatus();
        competition.advanceStatus();
        var saved = competitionRepository.save(competition);
        eventPublisher.publishEvent(new CompetitionStatusAdvancedEvent(
                competitionId, previousStatus, saved.getStatus()));
        return saved;
    }

    public List<Competition> findByEvent(@NotNull UUID eventId) {
        return competitionRepository.findByEventId(eventId);
    }

    public CompetitionParticipant addParticipant(@NotNull UUID competitionId,
                                                  @NotNull UUID userId,
                                                  @NotNull CompetitionRole role,
                                                  @NotNull UUID requestingUserId) {
        competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
        requireSystemAdmin(requestingUserId);
        userService.findById(userId);
        if (participantRepository.existsByCompetitionIdAndUserId(competitionId, userId)) {
            throw new IllegalArgumentException("User is already a participant in this competition");
        }
        var participant = new CompetitionParticipant(UUID.randomUUID(), competitionId, userId, role);
        if (role.requiresAccessCode()) {
            participant.assignAccessCode(generateAccessCode());
        }
        return participantRepository.save(participant);
    }

    public List<CompetitionParticipant> copyParticipants(@NotNull UUID sourceCompetitionId,
                                                          @NotNull UUID targetCompetitionId,
                                                          @NotNull UUID requestingUserId) {
        var source = competitionRepository.findById(sourceCompetitionId)
                .orElseThrow(() -> new IllegalArgumentException("Source competition not found"));
        var target = competitionRepository.findById(targetCompetitionId)
                .orElseThrow(() -> new IllegalArgumentException("Target competition not found"));
        requireSystemAdmin(requestingUserId);
        if (!source.getEventId().equals(target.getEventId())) {
            throw new IllegalArgumentException("Competitions must belong to the same event");
        }
        var sourceParticipants = participantRepository.findByCompetitionId(sourceCompetitionId);
        var copied = new ArrayList<CompetitionParticipant>();
        for (var sp : sourceParticipants) {
            if (participantRepository.existsByCompetitionIdAndUserId(targetCompetitionId, sp.getUserId())) {
                continue;
            }
            var newParticipant = new CompetitionParticipant(
                    UUID.randomUUID(), targetCompetitionId, sp.getUserId(), sp.getRole());
            if (sp.getRole().requiresAccessCode()) {
                newParticipant.assignAccessCode(generateAccessCode());
            }
            copied.add(participantRepository.save(newParticipant));
        }
        return copied;
    }

    public CompetitionParticipant withdrawParticipant(@NotNull UUID competitionId,
                                                      @NotNull UUID participantId,
                                                      @NotNull UUID requestingUserId) {
        competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
        requireSystemAdmin(requestingUserId);
        var participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new IllegalArgumentException("Participant not found"));
        participant.withdraw();
        return participantRepository.save(participant);
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
}
