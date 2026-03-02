package app.meads.competition;

import app.meads.competition.internal.*;
import app.meads.identity.Role;
import app.meads.identity.UserService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.security.SecureRandom;
import java.time.LocalDate;
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
    private final CompetitionCategoryRepository competitionCategoryRepository;
    private final EventParticipantRepository eventParticipantRepository;
    private final CategoryRepository categoryRepository;
    private final MeadEventRepository meadEventRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    CompetitionService(CompetitionRepository competitionRepository,
                       CompetitionParticipantRepository participantRepository,
                       CompetitionCategoryRepository competitionCategoryRepository,
                       EventParticipantRepository eventParticipantRepository,
                       CategoryRepository categoryRepository,
                       MeadEventRepository meadEventRepository,
                       UserService userService,
                       ApplicationEventPublisher eventPublisher) {
        this.competitionRepository = competitionRepository;
        this.participantRepository = participantRepository;
        this.competitionCategoryRepository = competitionCategoryRepository;
        this.eventParticipantRepository = eventParticipantRepository;
        this.categoryRepository = categoryRepository;
        this.meadEventRepository = meadEventRepository;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
    }

    public MeadEvent createMeadEvent(@NotBlank String name,
                             @NotNull LocalDate startDate,
                             @NotNull LocalDate endDate,
                             String location,
                             @NotNull UUID requestingUserId) {
        requireSystemAdmin(requestingUserId);
        var meadEvent = new MeadEvent(name, startDate, endDate, location);
        return meadEventRepository.save(meadEvent);
    }

    public MeadEvent findMeadEventById(@NotNull UUID eventId) {
        return meadEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
    }

    public List<MeadEvent> findAllMeadEvents() {
        return meadEventRepository.findAll();
    }

    public MeadEvent updateMeadEvent(@NotNull UUID eventId,
                              @NotBlank String name,
                              @NotNull LocalDate startDate,
                              @NotNull LocalDate endDate,
                              String location,
                              @NotNull UUID requestingUserId) {
        var meadEvent = meadEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        requireSystemAdmin(requestingUserId);
        meadEvent.updateDetails(name, startDate, endDate, location);
        return meadEventRepository.save(meadEvent);
    }

    public MeadEvent updateMeadEventLogo(@NotNull UUID eventId,
                                  byte[] logo,
                                  String contentType,
                                  @NotNull UUID requestingUserId) {
        var meadEvent = meadEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        requireSystemAdmin(requestingUserId);
        meadEvent.updateLogo(logo, contentType);
        return meadEventRepository.save(meadEvent);
    }

    public void deleteMeadEvent(@NotNull UUID eventId,
                             @NotNull UUID requestingUserId) {
        var meadEvent = meadEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        requireSystemAdmin(requestingUserId);
        var competitions = competitionRepository.findByEventId(eventId);
        if (!competitions.isEmpty()) {
            throw new IllegalArgumentException("Cannot delete event with competitions");
        }
        meadEventRepository.delete(meadEvent);
    }

    public Competition createCompetition(@NotNull UUID eventId,
                                         @NotBlank String name,
                                         @NotNull ScoringSystem scoringSystem,
                                         @NotNull UUID requestingUserId) {
        meadEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        requireSystemAdmin(requestingUserId);
        var competition = new Competition(eventId, name, scoringSystem);
        var saved = competitionRepository.save(competition);
        initializeCategories(saved);
        return saved;
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

    public void deleteCompetition(@NotNull UUID competitionId,
                                    @NotNull UUID requestingUserId) {
        var competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
        requireAuthorized(competition.getId(), requestingUserId);
        participantRepository.deleteAll(
                participantRepository.findByCompetitionId(competitionId));
        competitionCategoryRepository.deleteAll(
                competitionCategoryRepository.findByCompetitionIdOrderBySortOrder(competitionId));
        competitionRepository.delete(competition);
    }

    public List<CompetitionParticipant> findParticipantsByCompetition(@NotNull UUID competitionId) {
        return participantRepository.findByCompetitionId(competitionId);
    }

    public List<Category> findCategoriesByScoringSystem(@NotNull ScoringSystem scoringSystem) {
        return categoryRepository.findByScoringSystem(scoringSystem);
    }

    public List<CompetitionCategory> findCompetitionCategories(@NotNull UUID competitionId) {
        return competitionCategoryRepository.findByCompetitionIdOrderBySortOrder(competitionId);
    }

    public CompetitionCategory addCatalogCategory(@NotNull UUID competitionId,
                                                     @NotNull UUID catalogCategoryId,
                                                     @NotNull UUID requestingUserId) {
        var competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
        requireAuthorized(competition.getId(), requestingUserId);
        if (!competition.getStatus().allowsCategoryModification()) {
            throw new IllegalArgumentException("Categories cannot be modified in status: "
                    + competition.getStatus().getDisplayName());
        }
        var catalogCategory = categoryRepository.findById(catalogCategoryId)
                .orElseThrow(() -> new IllegalArgumentException("Catalog category not found"));
        if (competitionCategoryRepository.existsByCompetitionIdAndCatalogCategoryId(
                competitionId, catalogCategoryId)) {
            throw new IllegalArgumentException("Catalog category already added to this competition");
        }
        var cc = new CompetitionCategory(competitionId, catalogCategory.getId(),
                catalogCategory.getCode(), catalogCategory.getName(),
                catalogCategory.getDescription(), null, 0);
        return competitionCategoryRepository.save(cc);
    }

    public CompetitionCategory addCustomCategory(@NotNull UUID competitionId,
                                                    @NotBlank String code,
                                                    @NotBlank String name,
                                                    @NotBlank String description,
                                                    UUID parentId,
                                                    @NotNull UUID requestingUserId) {
        var competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
        requireAuthorized(competition.getId(), requestingUserId);
        if (!competition.getStatus().allowsCategoryModification()) {
            throw new IllegalArgumentException("Categories cannot be modified in status: "
                    + competition.getStatus().getDisplayName());
        }
        if (competitionCategoryRepository.existsByCompetitionIdAndCode(competitionId, code)) {
            throw new IllegalArgumentException("Category code already exists in this competition: " + code);
        }
        if (parentId != null) {
            competitionCategoryRepository.findById(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("Parent category not found"));
        }
        var cc = new CompetitionCategory(competitionId, null, code, name, description, parentId, 0);
        return competitionCategoryRepository.save(cc);
    }

    public void removeCompetitionCategory(@NotNull UUID competitionId,
                                            @NotNull UUID categoryId,
                                            @NotNull UUID requestingUserId) {
        var competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
        requireAuthorized(competition.getId(), requestingUserId);
        if (!competition.getStatus().allowsCategoryModification()) {
            throw new IllegalArgumentException("Categories cannot be modified in status: "
                    + competition.getStatus().getDisplayName());
        }
        var category = competitionCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Competition category not found"));
        var children = competitionCategoryRepository.findByParentId(categoryId);
        if (!children.isEmpty()) {
            competitionCategoryRepository.deleteAll(children);
        }
        competitionCategoryRepository.delete(category);
    }

    public List<Category> findAvailableCatalogCategories(@NotNull UUID competitionId) {
        var competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
        var allCatalog = categoryRepository.findByScoringSystem(competition.getScoringSystem());
        return allCatalog.stream()
                .filter(cat -> !competitionCategoryRepository
                        .existsByCompetitionIdAndCatalogCategoryId(competitionId, cat.getId()))
                .toList();
    }

    private void initializeCategories(Competition competition) {
        var catalogCategories = categoryRepository.findByScoringSystem(
                competition.getScoringSystem());
        int sortOrder = 0;
        for (var cat : catalogCategories) {
            var cc = new CompetitionCategory(competition.getId(), cat.getId(),
                    cat.getCode(), cat.getName(), cat.getDescription(), null, sortOrder++);
            competitionCategoryRepository.save(cc);
        }
    }

    CompetitionParticipant addParticipant(@NotNull UUID competitionId,
                                          @NotNull UUID userId,
                                          @NotNull CompetitionRole role,
                                          @NotNull UUID requestingUserId) {
        var competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
        requireAuthorized(competition.getId(), requestingUserId);
        userService.findById(userId);

        var eventParticipant = findOrCreateEventParticipant(
                competition.getEventId(), userId, role);

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

        var eventParticipant = findOrCreateEventParticipant(eventId, userId, role);

        var competitions = competitionRepository.findByEventId(eventId);
        var added = new ArrayList<CompetitionParticipant>();
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

    private EventParticipant findOrCreateEventParticipant(UUID eventId, UUID userId,
                                                            CompetitionRole role) {
        return eventParticipantRepository
                .findByEventIdAndUserId(eventId, userId)
                .orElseGet(() -> {
                    var ep = new EventParticipant(eventId, userId);
                    if (role.requiresAccessCode()) {
                        ep.assignAccessCode(generateAccessCode());
                    }
                    return eventParticipantRepository.save(ep);
                });
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
