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
    private final DivisionRepository divisionRepository;
    private final ParticipantRepository participantRepository;
    private final ParticipantRoleRepository participantRoleRepository;
    private final DivisionCategoryRepository divisionCategoryRepository;
    private final CategoryRepository categoryRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    CompetitionService(CompetitionRepository competitionRepository,
                       DivisionRepository divisionRepository,
                       ParticipantRepository participantRepository,
                       ParticipantRoleRepository participantRoleRepository,
                       DivisionCategoryRepository divisionCategoryRepository,
                       CategoryRepository categoryRepository,
                       UserService userService,
                       ApplicationEventPublisher eventPublisher) {
        this.competitionRepository = competitionRepository;
        this.divisionRepository = divisionRepository;
        this.participantRepository = participantRepository;
        this.participantRoleRepository = participantRoleRepository;
        this.divisionCategoryRepository = divisionCategoryRepository;
        this.categoryRepository = categoryRepository;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
    }

    // --- Competition methods (were MeadEvent methods) ---

    public Competition createCompetition(@NotBlank String name,
                                          @NotNull LocalDate startDate,
                                          @NotNull LocalDate endDate,
                                          String location,
                                          @NotNull UUID requestingUserId) {
        requireSystemAdmin(requestingUserId);
        var competition = new Competition(name, startDate, endDate, location);
        return competitionRepository.save(competition);
    }

    public Competition findCompetitionById(@NotNull UUID competitionId) {
        return competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
    }

    public List<Competition> findAllCompetitions() {
        return competitionRepository.findAll();
    }

    public Competition updateCompetition(@NotNull UUID competitionId,
                                          @NotBlank String name,
                                          @NotNull LocalDate startDate,
                                          @NotNull LocalDate endDate,
                                          String location,
                                          @NotNull UUID requestingUserId) {
        var competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
        requireSystemAdmin(requestingUserId);
        competition.updateDetails(name, startDate, endDate, location);
        return competitionRepository.save(competition);
    }

    public Competition updateCompetitionLogo(@NotNull UUID competitionId,
                                              byte[] logo,
                                              String contentType,
                                              @NotNull UUID requestingUserId) {
        var competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
        requireSystemAdmin(requestingUserId);
        competition.updateLogo(logo, contentType);
        return competitionRepository.save(competition);
    }

    public void deleteCompetition(@NotNull UUID competitionId,
                                   @NotNull UUID requestingUserId) {
        var competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
        requireSystemAdmin(requestingUserId);
        var divisions = divisionRepository.findByCompetitionId(competitionId);
        if (!divisions.isEmpty()) {
            throw new IllegalArgumentException("Cannot delete competition with divisions");
        }
        competitionRepository.delete(competition);
    }

    // --- Division methods (were Competition methods) ---

    public Division createDivision(@NotNull UUID competitionId,
                                    @NotBlank String name,
                                    @NotNull ScoringSystem scoringSystem,
                                    @NotNull UUID requestingUserId) {
        competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
        requireAuthorized(competitionId, requestingUserId);
        var division = new Division(competitionId, name, scoringSystem);
        var saved = divisionRepository.save(division);
        initializeCategories(saved);
        return saved;
    }

    public Division findDivisionById(@NotNull UUID divisionId) {
        return divisionRepository.findById(divisionId)
                .orElseThrow(() -> new IllegalArgumentException("Division not found"));
    }

    public Division advanceDivisionStatus(@NotNull UUID divisionId,
                                           @NotNull UUID requestingUserId) {
        var division = divisionRepository.findById(divisionId)
                .orElseThrow(() -> new IllegalArgumentException("Division not found"));
        requireAuthorized(division.getCompetitionId(), requestingUserId);
        var previousStatus = division.getStatus();
        division.advanceStatus();
        var saved = divisionRepository.save(division);
        eventPublisher.publishEvent(new DivisionStatusAdvancedEvent(
                divisionId, previousStatus, saved.getStatus()));
        return saved;
    }

    public Division updateDivision(@NotNull UUID divisionId,
                                    @NotBlank String name,
                                    @NotNull ScoringSystem scoringSystem,
                                    @NotNull UUID requestingUserId) {
        var division = divisionRepository.findById(divisionId)
                .orElseThrow(() -> new IllegalArgumentException("Division not found"));
        requireAuthorized(division.getCompetitionId(), requestingUserId);
        division.updateDetails(name, scoringSystem);
        return divisionRepository.save(division);
    }

    public void deleteDivision(@NotNull UUID divisionId,
                                @NotNull UUID requestingUserId) {
        var division = divisionRepository.findById(divisionId)
                .orElseThrow(() -> new IllegalArgumentException("Division not found"));
        requireAuthorized(division.getCompetitionId(), requestingUserId);
        divisionCategoryRepository.deleteAll(
                divisionCategoryRepository.findByDivisionIdOrderByCode(divisionId));
        divisionRepository.delete(division);
    }

    public List<Division> findDivisionsByCompetition(@NotNull UUID competitionId) {
        return divisionRepository.findByCompetitionId(competitionId);
    }

    public Division updateDivisionEntryLimits(@NotNull UUID divisionId,
                                              Integer maxEntriesPerSubcategory,
                                              Integer maxEntriesPerMainCategory,
                                              @NotNull UUID requestingUserId) {
        var division = findDivisionById(divisionId);
        requireAuthorized(division.getCompetitionId(), requestingUserId);
        division.updateEntryLimits(maxEntriesPerSubcategory, maxEntriesPerMainCategory);
        return divisionRepository.save(division);
    }

    // --- Division Category methods ---

    public List<DivisionCategory> findDivisionCategories(@NotNull UUID divisionId) {
        return divisionCategoryRepository.findByDivisionIdOrderByCode(divisionId);
    }

    public DivisionCategory addCatalogCategory(@NotNull UUID divisionId,
                                                @NotNull UUID catalogCategoryId,
                                                @NotNull UUID requestingUserId) {
        var division = divisionRepository.findById(divisionId)
                .orElseThrow(() -> new IllegalArgumentException("Division not found"));
        requireAuthorized(division.getCompetitionId(), requestingUserId);
        if (!division.getStatus().allowsCategoryModification()) {
            throw new IllegalArgumentException("Categories cannot be modified in status: "
                    + division.getStatus().getDisplayName());
        }
        var catalogCategory = categoryRepository.findById(catalogCategoryId)
                .orElseThrow(() -> new IllegalArgumentException("Catalog category not found"));
        if (divisionCategoryRepository.existsByDivisionIdAndCatalogCategoryId(
                divisionId, catalogCategoryId)) {
            throw new IllegalArgumentException("Catalog category already added to this division");
        }
        var dc = new DivisionCategory(divisionId, catalogCategory.getId(),
                catalogCategory.getCode(), catalogCategory.getName(),
                catalogCategory.getDescription(), null, 0);
        return divisionCategoryRepository.save(dc);
    }

    public DivisionCategory addCustomCategory(@NotNull UUID divisionId,
                                               @NotBlank String code,
                                               @NotBlank String name,
                                               @NotBlank String description,
                                               UUID parentId,
                                               @NotNull UUID requestingUserId) {
        var division = divisionRepository.findById(divisionId)
                .orElseThrow(() -> new IllegalArgumentException("Division not found"));
        requireAuthorized(division.getCompetitionId(), requestingUserId);
        if (!division.getStatus().allowsCategoryModification()) {
            throw new IllegalArgumentException("Categories cannot be modified in status: "
                    + division.getStatus().getDisplayName());
        }
        if (divisionCategoryRepository.existsByDivisionIdAndCode(divisionId, code)) {
            throw new IllegalArgumentException("Category code already exists in this division: " + code);
        }
        if (parentId != null) {
            divisionCategoryRepository.findById(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("Parent category not found"));
        }
        var dc = new DivisionCategory(divisionId, null, code, name, description, parentId, 0);
        return divisionCategoryRepository.save(dc);
    }

    public DivisionCategory updateDivisionCategory(@NotNull UUID divisionId,
                                                     @NotNull UUID categoryId,
                                                     @NotBlank String code,
                                                     @NotBlank String name,
                                                     @NotBlank String description,
                                                     @NotNull UUID requestingUserId) {
        var division = divisionRepository.findById(divisionId)
                .orElseThrow(() -> new IllegalArgumentException("Division not found"));
        requireAuthorized(division.getCompetitionId(), requestingUserId);
        if (!division.getStatus().allowsCategoryModification()) {
            throw new IllegalArgumentException("Categories cannot be modified in status: "
                    + division.getStatus().getDisplayName());
        }
        var category = divisionCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Division category not found"));
        category.updateDetails(code, name, description);
        return divisionCategoryRepository.save(category);
    }

    public void removeDivisionCategory(@NotNull UUID divisionId,
                                        @NotNull UUID categoryId,
                                        @NotNull UUID requestingUserId) {
        var division = divisionRepository.findById(divisionId)
                .orElseThrow(() -> new IllegalArgumentException("Division not found"));
        requireAuthorized(division.getCompetitionId(), requestingUserId);
        if (!division.getStatus().allowsCategoryModification()) {
            throw new IllegalArgumentException("Categories cannot be modified in status: "
                    + division.getStatus().getDisplayName());
        }
        var category = divisionCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Division category not found"));
        var children = divisionCategoryRepository.findByParentId(categoryId);
        if (!children.isEmpty()) {
            divisionCategoryRepository.deleteAll(children);
        }
        divisionCategoryRepository.delete(category);
    }

    public List<Category> findAvailableCatalogCategories(@NotNull UUID divisionId) {
        var division = divisionRepository.findById(divisionId)
                .orElseThrow(() -> new IllegalArgumentException("Division not found"));
        var allCatalog = categoryRepository.findByScoringSystem(division.getScoringSystem());
        return allCatalog.stream()
                .filter(cat -> !divisionCategoryRepository
                        .existsByDivisionIdAndCatalogCategoryId(divisionId, cat.getId()))
                .toList();
    }

    // --- Participant methods ---

    ParticipantRole addParticipant(@NotNull UUID competitionId,
                                    @NotNull UUID userId,
                                    @NotNull CompetitionRole role,
                                    @NotNull UUID requestingUserId) {
        competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
        requireAuthorized(competitionId, requestingUserId);
        userService.findById(userId);

        var participant = findOrCreateParticipant(competitionId, userId, role);

        if (participantRoleRepository.existsByParticipantIdAndRole(participant.getId(), role)) {
            throw new IllegalArgumentException(
                    "User already has the " + role.name() + " role in this competition");
        }

        var pr = new ParticipantRole(participant.getId(), role);
        return participantRoleRepository.save(pr);
    }

    public ParticipantRole addParticipantByEmail(@NotNull UUID competitionId,
                                                   @NotBlank @Email String email,
                                                   @NotNull CompetitionRole role,
                                                   @NotNull UUID requestingUserId) {
        var user = userService.findOrCreateByEmail(email);
        return addParticipant(competitionId, user.getId(), role, requestingUserId);
    }

    public void removeParticipant(@NotNull UUID competitionId,
                                   @NotNull UUID participantId,
                                   @NotNull UUID requestingUserId) {
        requireAuthorized(competitionId, requestingUserId);
        var participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new IllegalArgumentException("Participant not found"));
        if (!participant.getCompetitionId().equals(competitionId)) {
            throw new IllegalArgumentException("Participant does not belong to this competition");
        }
        var roles = participantRoleRepository.findByParticipantId(participantId);
        participantRoleRepository.deleteAll(roles);
    }

    public List<Participant> findParticipantsByCompetition(@NotNull UUID competitionId) {
        return participantRepository.findByCompetitionId(competitionId);
    }

    public List<ParticipantRole> findRolesByCompetition(@NotNull UUID competitionId) {
        var participants = participantRepository.findByCompetitionId(competitionId);
        return participants.stream()
                .flatMap(p -> participantRoleRepository.findByParticipantId(p.getId()).stream())
                .toList();
    }

    // --- Authorization methods ---

    public List<Division> findAuthorizedDivisions(@NotNull UUID competitionId,
                                                    @NotNull UUID userId) {
        if (isAuthorized(competitionId, userId)) {
            return divisionRepository.findByCompetitionId(competitionId);
        }
        return List.of();
    }

    public boolean isAuthorizedForCompetition(@NotNull UUID competitionId,
                                                @NotNull UUID userId) {
        return isAuthorized(competitionId, userId);
    }

    public boolean isAuthorizedForDivision(@NotNull UUID divisionId,
                                            @NotNull UUID userId) {
        var division = divisionRepository.findById(divisionId).orElse(null);
        if (division == null) {
            return false;
        }
        return isAuthorized(division.getCompetitionId(), userId);
    }

    // --- Private helpers ---

    private String generateUniqueAccessCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            var sb = new StringBuilder(ACCESS_CODE_LENGTH);
            for (int i = 0; i < ACCESS_CODE_LENGTH; i++) {
                sb.append(ACCESS_CODE_CHARS.charAt(RANDOM.nextInt(ACCESS_CODE_CHARS.length())));
            }
            var code = sb.toString();
            if (!participantRepository.existsByAccessCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Unable to generate a unique access code");
    }

    private void requireSystemAdmin(UUID userId) {
        var user = userService.findById(userId);
        if (user.getRole() != Role.SYSTEM_ADMIN) {
            throw new IllegalArgumentException("User is not authorized to perform this action");
        }
    }

    private Participant findOrCreateParticipant(UUID competitionId, UUID userId,
                                                  CompetitionRole role) {
        return participantRepository
                .findByCompetitionIdAndUserId(competitionId, userId)
                .orElseGet(() -> {
                    var p = new Participant(competitionId, userId);
                    if (role.requiresAccessCode()) {
                        p.assignAccessCode(generateUniqueAccessCode());
                    }
                    return participantRepository.save(p);
                });
    }

    private void initializeCategories(Division division) {
        var catalogCategories = categoryRepository.findByScoringSystem(
                division.getScoringSystem());
        int sortOrder = 0;
        for (var cat : catalogCategories) {
            var dc = new DivisionCategory(division.getId(), cat.getId(),
                    cat.getCode(), cat.getName(), cat.getDescription(), null, sortOrder++);
            divisionCategoryRepository.save(dc);
        }
    }

    private void requireAuthorized(UUID competitionId, UUID userId) {
        if (!isAuthorized(competitionId, userId)) {
            throw new IllegalArgumentException("User is not authorized to perform this action");
        }
    }

    private boolean isAuthorized(UUID competitionId, UUID userId) {
        var user = userService.findById(userId);
        if (user.getRole() == Role.SYSTEM_ADMIN) {
            return true;
        }
        return participantRepository.findByCompetitionIdAndUserId(competitionId, userId)
                .map(participant -> participantRoleRepository.existsByParticipantIdAndRole(
                        participant.getId(), CompetitionRole.ADMIN))
                .orElse(false);
    }
}
