package app.meads.competition;

import app.meads.BusinessRuleException;
import app.meads.competition.internal.*;
import app.meads.identity.Role;
import app.meads.identity.UserService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.security.SecureRandom;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
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
    private final CompetitionDocumentRepository competitionDocumentRepository;
    private final List<DivisionRevertGuard> revertGuards;
    private final List<DivisionDeletionGuard> deletionGuards;
    private final List<ParticipantRemovalCleanup> removalCleanups;
    private final List<JudgingCategoryDeletionGuard> judgingCategoryDeletionGuards;
    private final List<MinJudgesPerTableLockGuard> minJudgesPerTableLockGuards;

    CompetitionService(CompetitionRepository competitionRepository,
                       DivisionRepository divisionRepository,
                       ParticipantRepository participantRepository,
                       ParticipantRoleRepository participantRoleRepository,
                       DivisionCategoryRepository divisionCategoryRepository,
                       CategoryRepository categoryRepository,
                       CompetitionDocumentRepository competitionDocumentRepository,
                       UserService userService,
                       ApplicationEventPublisher eventPublisher,
                       List<DivisionRevertGuard> revertGuards,
                       List<DivisionDeletionGuard> deletionGuards,
                       List<ParticipantRemovalCleanup> removalCleanups,
                       List<JudgingCategoryDeletionGuard> judgingCategoryDeletionGuards,
                       List<MinJudgesPerTableLockGuard> minJudgesPerTableLockGuards) {
        this.competitionRepository = competitionRepository;
        this.divisionRepository = divisionRepository;
        this.participantRepository = participantRepository;
        this.participantRoleRepository = participantRoleRepository;
        this.divisionCategoryRepository = divisionCategoryRepository;
        this.categoryRepository = categoryRepository;
        this.competitionDocumentRepository = competitionDocumentRepository;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
        this.revertGuards = revertGuards;
        this.deletionGuards = deletionGuards;
        this.removalCleanups = removalCleanups;
        this.judgingCategoryDeletionGuards = judgingCategoryDeletionGuards;
        this.minJudgesPerTableLockGuards = minJudgesPerTableLockGuards;
    }

    // --- Competition methods (were MeadEvent methods) ---

    public Competition createCompetition(@NotBlank String name,
                                          @NotBlank String shortName,
                                          @NotNull LocalDate startDate,
                                          @NotNull LocalDate endDate,
                                          String location,
                                          @NotNull UUID requestingUserId) {
        requireSystemAdmin(requestingUserId);
        if (competitionRepository.existsByShortName(shortName)) {
            throw new BusinessRuleException("error.competition.shortname-exists");
        }
        var competition = new Competition(name, shortName, startDate, endDate, location);
        var saved = competitionRepository.save(competition);
        log.info("Created competition: {} (shortName={})", saved.getId(), shortName);
        return saved;
    }

    public Competition findCompetitionById(@NotNull UUID competitionId) {
        return competitionRepository.findById(competitionId)
                .orElseThrow(() -> new BusinessRuleException("error.competition.not-found"));
    }

    public Competition findCompetitionByShortName(@NotBlank String shortName) {
        return competitionRepository.findByShortName(shortName)
                .orElseThrow(() -> new BusinessRuleException("error.competition.not-found"));
    }

    public List<Competition> findAllCompetitions() {
        return competitionRepository.findAll(Sort.by("name"));
    }

    public List<Competition> findCompetitionsByAdmin(@NotNull UUID userId) {
        return participantRepository.findByUserId(userId).stream()
                .filter(p -> participantRoleRepository.existsByParticipantIdAndRole(
                        p.getId(), CompetitionRole.ADMIN))
                .map(p -> competitionRepository.findById(p.getCompetitionId()))
                .flatMap(Optional::stream)
                .toList();
    }

    public Competition updateCompetition(@NotNull UUID competitionId,
                                          @NotBlank String name,
                                          @NotBlank String shortName,
                                          @NotNull LocalDate startDate,
                                          @NotNull LocalDate endDate,
                                          String location,
                                          @NotNull UUID requestingUserId) {
        var competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new BusinessRuleException("error.competition.not-found"));
        requireAuthorized(competitionId, requestingUserId);
        if (!competition.getShortName().equals(shortName)
                && competitionRepository.existsByShortName(shortName)) {
            throw new BusinessRuleException("error.competition.shortname-exists");
        }
        competition.updateDetails(name, shortName, startDate, endDate, location);
        log.info("Updated competition: {} (shortName={})", competitionId, shortName);
        return competitionRepository.save(competition);
    }

    public Competition updateCompetitionLogo(@NotNull UUID competitionId,
                                              byte[] logo,
                                              String contentType,
                                              @NotNull UUID requestingUserId) {
        var competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new BusinessRuleException("error.competition.not-found"));
        requireAuthorized(competitionId, requestingUserId);
        competition.updateLogo(logo, contentType);
        log.info("Updated logo for competition: {}", competitionId);
        return competitionRepository.save(competition);
    }

    public Competition updateCompetitionContactEmail(@NotNull UUID competitionId,
                                                       @Email String contactEmail,
                                                       @NotNull UUID requestingUserId) {
        var competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new BusinessRuleException("error.competition.not-found"));
        requireAuthorized(competitionId, requestingUserId);
        competition.updateContactEmail(contactEmail);
        log.info("Updated contact email for competition: {}", competitionId);
        return competitionRepository.save(competition);
    }

    public Competition updateCommentLanguages(@NotNull UUID competitionId,
                                              @NotNull Set<String> languageCodes,
                                              @NotNull UUID requestingUserId) {
        var competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new BusinessRuleException("error.competition.not-found"));
        requireAuthorized(competitionId, requestingUserId);
        try {
            competition.updateCommentLanguages(languageCodes);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("error.competition.invalid-language-code", e.getMessage());
        }
        log.info("Updated comment languages for competition {}: {}", competitionId, languageCodes);
        return competitionRepository.save(competition);
    }

    public Competition updateCompetitionShippingDetails(@NotNull UUID competitionId,
                                                           String shippingAddress,
                                                           String phoneNumber,
                                                           String website,
                                                           @NotNull UUID requestingUserId) {
        var competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new BusinessRuleException("error.competition.not-found"));
        requireAuthorized(competitionId, requestingUserId);
        competition.updateShippingDetails(shippingAddress, phoneNumber, website);
        log.info("Updated shipping details for competition: {}", competitionId);
        return competitionRepository.save(competition);
    }

    public void deleteCompetition(@NotNull UUID competitionId,
                                   @NotNull UUID requestingUserId) {
        var competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new BusinessRuleException("error.competition.not-found"));
        requireSystemAdmin(requestingUserId);
        var divisions = divisionRepository.findByCompetitionId(competitionId);
        if (!divisions.isEmpty()) {
            throw new BusinessRuleException("error.competition.has-divisions");
        }
        var documents = competitionDocumentRepository.findByCompetitionIdOrderByDisplayOrder(competitionId);
        competitionDocumentRepository.deleteAll(documents);
        var participants = participantRepository.findByCompetitionId(competitionId);
        for (var participant : participants) {
            var roles = participantRoleRepository.findByParticipantId(participant.getId());
            participantRoleRepository.deleteAll(roles);
        }
        participantRepository.deleteAll(participants);
        competitionRepository.delete(competition);
        log.info("Deleted competition: {} ({})", competitionId, competition.getShortName());
    }

    // --- Division methods (were Competition methods) ---

    public Division createDivision(@NotNull UUID competitionId,
                                    @NotBlank String name,
                                    @NotBlank String shortName,
                                    @NotNull ScoringSystem scoringSystem,
                                    @NotNull LocalDateTime registrationDeadline,
                                    @NotBlank String registrationDeadlineTimezone,
                                    @NotNull UUID requestingUserId) {
        competitionRepository.findById(competitionId)
                .orElseThrow(() -> new BusinessRuleException("error.competition.not-found"));
        requireAuthorized(competitionId, requestingUserId);
        if (divisionRepository.existsByCompetitionIdAndShortName(competitionId, shortName)) {
            throw new BusinessRuleException("error.division.shortname-exists");
        }
        try {
            ZoneId.of(registrationDeadlineTimezone);
        } catch (DateTimeException e) {
            throw new BusinessRuleException("error.division.invalid-timezone", registrationDeadlineTimezone);
        }
        var division = new Division(competitionId, name, shortName, scoringSystem,
                registrationDeadline, registrationDeadlineTimezone);
        var saved = divisionRepository.save(division);
        initializeCategories(saved);
        log.info("Created division: {} (shortName={}, competition={})", saved.getId(), shortName, competitionId);
        return saved;
    }

    public Division findDivisionById(@NotNull UUID divisionId) {
        return divisionRepository.findById(divisionId)
                .orElseThrow(() -> new BusinessRuleException("error.division.not-found"));
    }

    public Division findDivisionByShortName(@NotNull UUID competitionId,
                                             @NotBlank String shortName) {
        return divisionRepository.findByCompetitionIdAndShortName(competitionId, shortName)
                .orElseThrow(() -> new BusinessRuleException("error.division.not-found"));
    }

    public Division advanceDivisionStatus(@NotNull UUID divisionId,
                                           @NotNull UUID requestingUserId) {
        var division = divisionRepository.findById(divisionId)
                .orElseThrow(() -> new BusinessRuleException("error.division.not-found"));
        requireAuthorized(division.getCompetitionId(), requestingUserId);
        var previousStatus = division.getStatus();
        division.advanceStatus();
        var saved = divisionRepository.save(division);
        log.info("Advanced division status: {} ({} → {})", divisionId, previousStatus, saved.getStatus());
        eventPublisher.publishEvent(new DivisionStatusAdvancedEvent(
                divisionId, previousStatus, saved.getStatus()));
        return saved;
    }

    public Division revertDivisionStatus(@NotNull UUID divisionId,
                                          @NotNull UUID requestingUserId) {
        var division = divisionRepository.findById(divisionId)
                .orElseThrow(() -> new BusinessRuleException("error.division.not-found"));
        requireAuthorized(division.getCompetitionId(), requestingUserId);
        var previousStatus = division.getStatus();
        var targetStatus = previousStatus.previous()
                .orElseThrow(() -> new BusinessRuleException("error.division.cannot-revert-from-draft"));
        revertGuards.forEach(guard ->
                guard.checkRevertAllowed(divisionId, previousStatus, targetStatus));
        division.revertStatus();
        log.info("Reverted division status: {} ({} → {})", divisionId, previousStatus, targetStatus);
        return divisionRepository.save(division);
    }

    public Division updateDivision(@NotNull UUID divisionId,
                                    @NotBlank String name,
                                    @NotBlank String shortName,
                                    @NotNull ScoringSystem scoringSystem,
                                    String entryPrefix,
                                    @NotNull UUID requestingUserId) {
        var division = divisionRepository.findById(divisionId)
                .orElseThrow(() -> new BusinessRuleException("error.division.not-found"));
        requireAuthorized(division.getCompetitionId(), requestingUserId);
        if (!division.getShortName().equals(shortName)
                && divisionRepository.existsByCompetitionIdAndShortName(
                        division.getCompetitionId(), shortName)) {
            throw new BusinessRuleException("error.division.shortname-exists");
        }
        division.updateDetails(name, shortName, scoringSystem, entryPrefix);
        log.debug("Updated division settings: {} (shortName={})", divisionId, shortName);
        return divisionRepository.save(division);
    }

    public void deleteDivision(@NotNull UUID divisionId,
                                @NotNull UUID requestingUserId) {
        var division = divisionRepository.findById(divisionId)
                .orElseThrow(() -> new BusinessRuleException("error.division.not-found"));
        requireAuthorized(division.getCompetitionId(), requestingUserId);
        deletionGuards.forEach(guard -> guard.checkDeletionAllowed(divisionId));
        var categories = divisionCategoryRepository.findByDivisionIdOrderByCode(divisionId);
        var children = categories.stream().filter(c -> c.getParentId() != null).toList();
        var parents = categories.stream().filter(c -> c.getParentId() == null).toList();
        divisionCategoryRepository.deleteAll(children);
        divisionCategoryRepository.deleteAll(parents);
        divisionRepository.delete(division);
        log.info("Deleted division: {} ({})", divisionId, division.getShortName());
    }

    public List<Division> findDivisionsByCompetition(@NotNull UUID competitionId) {
        return divisionRepository.findByCompetitionIdOrderByName(competitionId);
    }

    public Division updateDivisionEntryLimits(@NotNull UUID divisionId,
                                              Integer maxEntriesPerSubcategory,
                                              Integer maxEntriesPerMainCategory,
                                              Integer maxEntriesTotal,
                                              @NotNull UUID requestingUserId) {
        var division = findDivisionById(divisionId);
        requireAuthorized(division.getCompetitionId(), requestingUserId);
        division.updateEntryLimits(maxEntriesPerSubcategory, maxEntriesPerMainCategory,
                maxEntriesTotal);
        log.debug("Updated entry limits for division: {} (sub={}, main={}, total={})",
                divisionId, maxEntriesPerSubcategory, maxEntriesPerMainCategory, maxEntriesTotal);
        return divisionRepository.save(division);
    }

    public Division updateDivisionDeadline(@NotNull UUID divisionId,
                                          @NotNull LocalDateTime deadline,
                                          @NotBlank String timezone,
                                          @NotNull UUID requestingUserId) {
        var division = findDivisionById(divisionId);
        requireAuthorized(division.getCompetitionId(), requestingUserId);
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw new BusinessRuleException("error.division.invalid-timezone", timezone);
        }
        division.updateRegistrationDeadline(deadline, timezone);
        log.debug("Updated registration deadline for division: {} ({} {})",
                divisionId, deadline, timezone);
        return divisionRepository.save(division);
    }

    public Division updateDivisionBosPlaces(@NotNull UUID divisionId,
                                            int bosPlaces,
                                            @NotNull UUID requestingUserId) {
        var division = findDivisionById(divisionId);
        requireAuthorized(division.getCompetitionId(), requestingUserId);
        try {
            division.updateBosPlaces(bosPlaces);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("error.division.invalid-bos-places", String.valueOf(bosPlaces));
        } catch (IllegalStateException e) {
            throw new BusinessRuleException("error.division.bos-places-locked", division.getStatus().getDisplayName());
        }
        log.info("Updated bosPlaces for division {} → {}", divisionId, bosPlaces);
        return divisionRepository.save(division);
    }

    public Division updateDivisionMinJudgesPerTable(@NotNull UUID divisionId,
                                                     int minJudgesPerTable,
                                                     @NotNull UUID requestingUserId) {
        var division = findDivisionById(divisionId);
        requireAuthorized(division.getCompetitionId(), requestingUserId);
        if (isMinJudgesPerTableLocked(divisionId)) {
            throw new BusinessRuleException("error.division.min-judges-locked");
        }
        try {
            division.updateMinJudgesPerTable(minJudgesPerTable);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("error.division.invalid-min-judges",
                    String.valueOf(minJudgesPerTable));
        } catch (IllegalStateException e) {
            throw new BusinessRuleException("error.division.min-judges-status-locked",
                    division.getStatus().getDisplayName());
        }
        log.info("Updated minJudgesPerTable for division {} → {}", divisionId, minJudgesPerTable);
        return divisionRepository.save(division);
    }

    public boolean isMinJudgesPerTableLocked(@NotNull UUID divisionId) {
        return minJudgesPerTableLockGuards.stream()
                .anyMatch(g -> g.isLocked(divisionId));
    }

    public Division updateDivisionMeaderyNameRequired(@NotNull UUID divisionId,
                                                       boolean meaderyNameRequired,
                                                       @NotNull UUID requestingUserId) {
        var division = findDivisionById(divisionId);
        requireAuthorized(division.getCompetitionId(), requestingUserId);
        division.updateMeaderyNameRequired(meaderyNameRequired);
        log.debug("Division {} meaderyNameRequired set to {}", divisionId, meaderyNameRequired);
        return divisionRepository.save(division);
    }

    // --- Division Category methods ---

    public List<DivisionCategory> findDivisionCategories(@NotNull UUID divisionId) {
        return divisionCategoryRepository.findByDivisionIdOrderByCode(divisionId);
    }

    public DivisionCategory findDivisionCategoryById(@NotNull UUID categoryId) {
        return divisionCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessRuleException("error.category.not-found"));
    }

    public DivisionCategory addCatalogCategory(@NotNull UUID divisionId,
                                                @NotNull UUID catalogCategoryId,
                                                @NotNull UUID requestingUserId) {
        var division = divisionRepository.findById(divisionId)
                .orElseThrow(() -> new BusinessRuleException("error.division.not-found"));
        requireAuthorized(division.getCompetitionId(), requestingUserId);
        if (!division.getStatus().allowsCategoryModification()) {
            throw new BusinessRuleException("error.category.cannot-modify-status", division.getStatus().getDisplayName());
        }
        var catalogCategory = categoryRepository.findById(catalogCategoryId)
                .orElseThrow(() -> new BusinessRuleException("error.category.not-found"));
        if (divisionCategoryRepository.existsByDivisionIdAndCatalogCategoryId(
                divisionId, catalogCategoryId)) {
            throw new BusinessRuleException("error.category.already-added");
        }
        UUID parentId = null;
        if (catalogCategory.getParentCode() != null) {
            parentId = divisionCategoryRepository.findByDivisionIdOrderByCode(divisionId).stream()
                    .filter(dc -> dc.getCode().equals(catalogCategory.getParentCode()))
                    .map(DivisionCategory::getId)
                    .findFirst()
                    .orElse(null);
        }
        var dc = new DivisionCategory(divisionId, catalogCategory.getId(),
                catalogCategory.getCode(), catalogCategory.getName(),
                catalogCategory.getDescription(), parentId, 0);
        log.debug("Added catalog category {} to division {}", catalogCategory.getCode(), divisionId);
        return divisionCategoryRepository.save(dc);
    }

    public DivisionCategory addCustomCategory(@NotNull UUID divisionId,
                                               @NotBlank String code,
                                               @NotBlank String name,
                                               @NotBlank String description,
                                               UUID parentId,
                                               @NotNull UUID requestingUserId) {
        var division = divisionRepository.findById(divisionId)
                .orElseThrow(() -> new BusinessRuleException("error.division.not-found"));
        requireAuthorized(division.getCompetitionId(), requestingUserId);
        if (!division.getStatus().allowsCategoryModification()) {
            throw new BusinessRuleException("error.category.cannot-modify-status", division.getStatus().getDisplayName());
        }
        if (divisionCategoryRepository.existsByDivisionIdAndCode(divisionId, code)) {
            throw new BusinessRuleException("error.category.code-exists", code);
        }
        if (parentId != null) {
            divisionCategoryRepository.findById(parentId)
                    .orElseThrow(() -> new BusinessRuleException("error.category.parent-not-found"));
        }
        var dc = new DivisionCategory(divisionId, null, code, name, description, parentId, 0);
        log.debug("Added custom category {} to division {}", code, divisionId);
        return divisionCategoryRepository.save(dc);
    }

    public DivisionCategory updateDivisionCategory(@NotNull UUID divisionId,
                                                     @NotNull UUID categoryId,
                                                     @NotBlank String code,
                                                     @NotBlank String name,
                                                     @NotBlank String description,
                                                     @NotNull UUID requestingUserId) {
        var division = divisionRepository.findById(divisionId)
                .orElseThrow(() -> new BusinessRuleException("error.division.not-found"));
        requireAuthorized(division.getCompetitionId(), requestingUserId);
        if (!division.getStatus().allowsCategoryModification()) {
            throw new BusinessRuleException("error.category.cannot-modify-status", division.getStatus().getDisplayName());
        }
        var category = divisionCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessRuleException("error.category.not-found"));
        category.updateDetails(code, name, description);
        return divisionCategoryRepository.save(category);
    }

    public void removeDivisionCategory(@NotNull UUID divisionId,
                                        @NotNull UUID categoryId,
                                        @NotNull UUID requestingUserId) {
        var division = divisionRepository.findById(divisionId)
                .orElseThrow(() -> new BusinessRuleException("error.division.not-found"));
        requireAuthorized(division.getCompetitionId(), requestingUserId);
        if (!division.getStatus().allowsCategoryModification()) {
            throw new BusinessRuleException("error.category.cannot-modify-status", division.getStatus().getDisplayName());
        }
        var category = divisionCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessRuleException("error.category.not-found"));
        var children = divisionCategoryRepository.findByParentId(categoryId);
        if (!children.isEmpty()) {
            divisionCategoryRepository.deleteAll(children);
        }
        divisionCategoryRepository.delete(category);
        log.debug("Removed category {} from division {}", category.getCode(), divisionId);
    }

    public List<DivisionCategory> findJudgingCategories(@NotNull UUID divisionId) {
        return divisionCategoryRepository.findByDivisionIdAndScopeOrderByCode(divisionId, CategoryScope.JUDGING);
    }

    public List<DivisionCategory> initializeJudgingCategories(@NotNull UUID divisionId,
                                                                @NotNull UUID requestingUserId) {
        var division = divisionRepository.findById(divisionId)
                .orElseThrow(() -> new BusinessRuleException("error.division.not-found"));
        requireAuthorized(division.getCompetitionId(), requestingUserId);
        if (!division.getStatus().allowsJudgingCategoryManagement()) {
            throw new BusinessRuleException("error.category.judging-not-allowed-status",
                    division.getStatus().getDisplayName());
        }
        var existingJudging = divisionCategoryRepository.findByDivisionIdAndScopeOrderByCode(
                divisionId, CategoryScope.JUDGING);
        if (!existingJudging.isEmpty()) {
            throw new BusinessRuleException("error.category.judging-already-initialized");
        }
        var registrationCats = divisionCategoryRepository.findByDivisionIdAndScopeOrderByCode(
                divisionId, CategoryScope.REGISTRATION);

        // First pass: clone main categories (no parent), build old-id → new-id map
        var idMap = new java.util.HashMap<UUID, UUID>();
        var result = new java.util.ArrayList<DivisionCategory>();
        int sortOrder = 0;
        for (var cat : registrationCats) {
            if (cat.getParentId() == null) {
                var clone = new DivisionCategory(divisionId, null,
                        cat.getCode(), cat.getName(), cat.getDescription(),
                        null, sortOrder++, CategoryScope.JUDGING);
                var saved = divisionCategoryRepository.save(clone);
                idMap.put(cat.getId(), saved.getId());
                result.add(saved);
            }
        }

        // Second pass: clone subcategories with mapped parent IDs
        for (var cat : registrationCats) {
            if (cat.getParentId() != null) {
                UUID mappedParentId = idMap.get(cat.getParentId());
                var clone = new DivisionCategory(divisionId, null,
                        cat.getCode(), cat.getName(), cat.getDescription(),
                        mappedParentId, sortOrder++, CategoryScope.JUDGING);
                result.add(divisionCategoryRepository.save(clone));
            }
        }
        log.info("Initialized {} judging categories for division {}", result.size(), divisionId);
        return result;
    }

    public DivisionCategory addJudgingCategory(@NotNull UUID divisionId,
                                                @NotBlank String code,
                                                @NotBlank String name,
                                                @NotBlank String description,
                                                UUID parentId,
                                                @NotNull UUID requestingUserId) {
        var division = divisionRepository.findById(divisionId)
                .orElseThrow(() -> new BusinessRuleException("error.division.not-found"));
        requireAuthorized(division.getCompetitionId(), requestingUserId);
        if (!division.getStatus().allowsJudgingCategoryManagement()) {
            throw new BusinessRuleException("error.category.judging-not-allowed-status",
                    division.getStatus().getDisplayName());
        }
        if (divisionCategoryRepository.existsByDivisionIdAndCodeAndScope(divisionId, code, CategoryScope.JUDGING)) {
            throw new BusinessRuleException("error.category.code-exists", code);
        }
        if (parentId != null) {
            divisionCategoryRepository.findById(parentId)
                    .orElseThrow(() -> new BusinessRuleException("error.category.parent-not-found"));
        }
        var dc = new DivisionCategory(divisionId, null, code, name, description, parentId, 0,
                CategoryScope.JUDGING);
        log.debug("Added judging category {} to division {}", code, divisionId);
        return divisionCategoryRepository.save(dc);
    }

    public DivisionCategory updateJudgingCategory(@NotNull UUID divisionId,
                                                    @NotNull UUID categoryId,
                                                    @NotBlank String code,
                                                    @NotBlank String name,
                                                    @NotBlank String description,
                                                    @NotNull UUID requestingUserId) {
        var division = divisionRepository.findById(divisionId)
                .orElseThrow(() -> new BusinessRuleException("error.division.not-found"));
        requireAuthorized(division.getCompetitionId(), requestingUserId);
        if (!division.getStatus().allowsJudgingCategoryManagement()) {
            throw new BusinessRuleException("error.category.judging-not-allowed-status",
                    division.getStatus().getDisplayName());
        }
        var category = divisionCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessRuleException("error.category.not-found"));
        category.updateDetails(code, name, description);
        log.debug("Updated judging category {} in division {}", code, divisionId);
        return divisionCategoryRepository.save(category);
    }

    public void removeJudgingCategory(@NotNull UUID divisionId,
                                       @NotNull UUID categoryId,
                                       @NotNull UUID requestingUserId) {
        var division = divisionRepository.findById(divisionId)
                .orElseThrow(() -> new BusinessRuleException("error.division.not-found"));
        requireAuthorized(division.getCompetitionId(), requestingUserId);
        if (!division.getStatus().allowsJudgingCategoryManagement()) {
            throw new BusinessRuleException("error.category.judging-not-allowed-status",
                    division.getStatus().getDisplayName());
        }
        var category = divisionCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessRuleException("error.category.not-found"));
        judgingCategoryDeletionGuards.forEach(guard -> guard.checkDeletionAllowed(categoryId));
        var children = divisionCategoryRepository.findByParentId(categoryId);
        if (!children.isEmpty()) {
            divisionCategoryRepository.deleteAll(children);
        }
        divisionCategoryRepository.delete(category);
        log.debug("Removed judging category {} from division {}", category.getCode(), divisionId);
    }

    public List<Category> findAvailableCatalogCategories(@NotNull UUID divisionId) {
        var division = divisionRepository.findById(divisionId)
                .orElseThrow(() -> new BusinessRuleException("error.division.not-found"));
        var allCatalog = categoryRepository.findByScoringSystemOrderByCode(division.getScoringSystem());
        return allCatalog.stream()
                .filter(cat -> !divisionCategoryRepository
                        .existsByDivisionIdAndCatalogCategoryId(divisionId, cat.getId()))
                .toList();
    }

    // --- Document methods ---

    public CompetitionDocument addDocument(@NotNull UUID competitionId,
                                            @NotBlank String name,
                                            @NotNull DocumentType type,
                                            byte[] data,
                                            String contentType,
                                            String url,
                                            String language,
                                            @NotNull UUID requestingUserId) {
        competitionRepository.findById(competitionId)
                .orElseThrow(() -> new BusinessRuleException("error.competition.not-found"));
        requireAuthorized(competitionId, requestingUserId);
        if (competitionDocumentRepository.existsByCompetitionIdAndName(competitionId, name)) {
            throw new BusinessRuleException("error.document.name-exists");
        }
        int nextOrder = competitionDocumentRepository.countByCompetitionId(competitionId);
        var doc = switch (type) {
            case PDF -> CompetitionDocument.createPdf(competitionId, name, data, contentType, nextOrder, language);
            case LINK -> CompetitionDocument.createLink(competitionId, name, url, nextOrder, language);
        };
        log.info("Added document '{}' (type={}) to competition {}", name, type, competitionId);
        return competitionDocumentRepository.save(doc);
    }

    public void removeDocument(@NotNull UUID documentId, @NotNull UUID requestingUserId) {
        var doc = competitionDocumentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessRuleException("error.document.not-found"));
        requireAuthorized(doc.getCompetitionId(), requestingUserId);
        competitionDocumentRepository.delete(doc);
        log.info("Removed document '{}' from competition {}", doc.getName(), doc.getCompetitionId());
    }

    public CompetitionDocument updateDocumentName(@NotNull UUID documentId,
                                                    @NotBlank String name,
                                                    @NotNull UUID requestingUserId) {
        var doc = competitionDocumentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessRuleException("error.document.not-found"));
        requireAuthorized(doc.getCompetitionId(), requestingUserId);
        if (competitionDocumentRepository.existsByCompetitionIdAndName(doc.getCompetitionId(), name)) {
            throw new BusinessRuleException("error.document.name-exists");
        }
        doc.updateName(name);
        log.debug("Updated document name: {} → '{}'", documentId, name);
        return competitionDocumentRepository.save(doc);
    }

    public void reorderDocuments(@NotNull UUID competitionId,
                                  @NotNull List<UUID> orderedIds,
                                  @NotNull UUID requestingUserId) {
        competitionRepository.findById(competitionId)
                .orElseThrow(() -> new BusinessRuleException("error.competition.not-found"));
        requireAuthorized(competitionId, requestingUserId);
        var docs = competitionDocumentRepository.findByCompetitionIdOrderByDisplayOrder(competitionId);
        var docMap = docs.stream()
                .collect(java.util.stream.Collectors.toMap(CompetitionDocument::getId,
                        java.util.function.Function.identity()));
        for (int i = 0; i < orderedIds.size(); i++) {
            var doc = docMap.get(orderedIds.get(i));
            if (doc != null) {
                doc.updateDisplayOrder(i);
            }
        }
        competitionDocumentRepository.saveAll(docs);
        log.debug("Reordered {} documents for competition {}", orderedIds.size(), competitionId);
    }

    public List<CompetitionDocument> getDocuments(@NotNull UUID competitionId) {
        return competitionDocumentRepository.findByCompetitionIdOrderByDisplayOrder(competitionId);
    }

    public List<CompetitionDocument> getDocumentsForLocale(@NotNull UUID competitionId,
                                                            @NotNull java.util.Locale locale) {
        var allDocs = competitionDocumentRepository.findByCompetitionIdOrderByDisplayOrder(competitionId);
        var lang = locale.getLanguage();
        return allDocs.stream()
                .filter(doc -> doc.getLanguage() == null || doc.getLanguage().equals(lang))
                .toList();
    }

    public CompetitionDocument getDocument(@NotNull UUID documentId) {
        return competitionDocumentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessRuleException("error.document.not-found"));
    }

    // --- Participant methods ---

    ParticipantRole addParticipant(@NotNull UUID competitionId,
                                    @NotNull UUID userId,
                                    @NotNull CompetitionRole role,
                                    @NotNull UUID requestingUserId) {
        competitionRepository.findById(competitionId)
                .orElseThrow(() -> new BusinessRuleException("error.competition.not-found"));
        requireAuthorized(competitionId, requestingUserId);
        userService.findById(userId);

        var participant = findOrCreateParticipant(competitionId, userId, role);

        if (participantRoleRepository.existsByParticipantIdAndRole(participant.getId(), role)) {
            throw new BusinessRuleException("error.participant.role-exists", role.name());
        }

        validateRoleCombination(participant.getId(), role);

        var pr = new ParticipantRole(participant.getId(), role);
        log.info("Added participant role: userId={}, role={}, competition={}", userId, role, competitionId);
        return participantRoleRepository.save(pr);
    }

    /**
     * Ensures a user has the ENTRANT role in a competition. No authorization required —
     * intended for system-initiated flows (e.g. webhook order processing).
     * Idempotent: does nothing if the user already has the ENTRANT role.
     */
    public void ensureEntrantParticipant(@NotNull UUID competitionId, @NotNull UUID userId) {
        competitionRepository.findById(competitionId)
                .orElseThrow(() -> new BusinessRuleException("error.competition.not-found"));
        var participant = findOrCreateParticipant(competitionId, userId, CompetitionRole.ENTRANT);
        if (!participantRoleRepository.existsByParticipantIdAndRole(
                participant.getId(), CompetitionRole.ENTRANT)) {
            validateRoleCombination(participant.getId(), CompetitionRole.ENTRANT);
            participantRoleRepository.save(new ParticipantRole(participant.getId(), CompetitionRole.ENTRANT));
            log.debug("Auto-added ENTRANT role: userId={}, competition={}", userId, competitionId);
        }
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
                .orElseThrow(() -> new BusinessRuleException("error.participant.not-found"));
        if (!participant.getCompetitionId().equals(competitionId)) {
            throw new BusinessRuleException("error.participant.wrong-competition");
        }
        removalCleanups.forEach(c -> c.cleanupForParticipant(competitionId, participant.getUserId()));
        var roles = participantRoleRepository.findByParticipantId(participantId);
        participantRoleRepository.deleteAll(roles);
        participantRepository.delete(participant);
        log.info("Removed participant: participantId={}, competition={}", participantId, competitionId);
    }

    public void removeParticipantRole(@NotNull UUID competitionId,
                                       @NotNull UUID participantId,
                                       @NotNull CompetitionRole role,
                                       @NotNull UUID requestingUserId) {
        requireAuthorized(competitionId, requestingUserId);
        var participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new BusinessRuleException("error.participant.not-found"));
        if (!participant.getCompetitionId().equals(competitionId)) {
            throw new BusinessRuleException("error.participant.wrong-competition");
        }
        var roles = participantRoleRepository.findByParticipantId(participantId);
        var roleToRemove = roles.stream()
                .filter(r -> r.getRole() == role)
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("error.participant.role-not-found", role.name()));
        participantRoleRepository.delete(roleToRemove);
        if (roles.size() == 1) {
            removalCleanups.forEach(c -> c.cleanupForParticipant(competitionId, participant.getUserId()));
            participantRepository.delete(participant);
            log.info("Removed participant (last role): participantId={}, role={}, competition={}",
                    participantId, role, competitionId);
        } else {
            log.info("Removed role from participant: participantId={}, role={}, competition={}",
                    participantId, role, competitionId);
        }
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

    public List<ParticipantRole> findRolesForParticipant(@NotNull UUID participantId) {
        return participantRoleRepository.findByParticipantId(participantId);
    }

    public boolean hasIncompatibleRolesForEntrant(@NotNull UUID competitionId, @NotNull UUID userId) {
        var participant = participantRepository.findByCompetitionIdAndUserId(competitionId, userId)
                .orElse(null);
        if (participant == null) return false;
        var existingRoles = participantRoleRepository.findByParticipantId(participant.getId())
                .stream().map(ParticipantRole::getRole).collect(Collectors.toSet());
        if (existingRoles.isEmpty() || existingRoles.contains(CompetitionRole.ENTRANT)) return false;
        // Only JUDGE is compatible with ENTRANT
        return existingRoles.stream().anyMatch(r -> r != CompetitionRole.JUDGE);
    }

    private void validateRoleCombination(UUID participantId, CompetitionRole newRole) {
        var existingRoles = participantRoleRepository.findByParticipantId(participantId)
                .stream().map(ParticipantRole::getRole).collect(Collectors.toSet());
        if (!existingRoles.isEmpty()) {
            var combined = new HashSet<>(existingRoles);
            combined.add(newRole);
            var allowedCombination = Set.of(CompetitionRole.JUDGE, CompetitionRole.ENTRANT);
            if (!allowedCombination.containsAll(combined)) {
                throw new BusinessRuleException("error.participant.incompatible-role", newRole.getDisplayName());
            }
        }
    }

    // --- Authorization methods ---

    public List<Division> findAuthorizedDivisions(@NotNull UUID competitionId,
                                                    @NotNull UUID userId) {
        if (isAuthorized(competitionId, userId)) {
            return divisionRepository.findByCompetitionIdOrderByName(competitionId);
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
        throw new BusinessRuleException("error.accesscode.generation-failed");
    }

    private void requireSystemAdmin(UUID userId) {
        var user = userService.findById(userId);
        if (user.getRole() != Role.SYSTEM_ADMIN) {
            throw new BusinessRuleException("error.auth.unauthorized");
        }
    }

    private Participant findOrCreateParticipant(UUID competitionId, UUID userId,
                                                  CompetitionRole role) {
        var existing = participantRepository
                .findByCompetitionIdAndUserId(competitionId, userId);
        if (existing.isPresent()) {
            var participant = existing.get();
            if (role.requiresAccessCode() && participant.getAccessCode() == null) {
                participant.assignAccessCode(generateUniqueAccessCode());
                return participantRepository.save(participant);
            }
            return participant;
        }
        var p = new Participant(competitionId, userId);
        if (role.requiresAccessCode()) {
            p.assignAccessCode(generateUniqueAccessCode());
        }
        return participantRepository.save(p);
    }

    private void initializeCategories(Division division) {
        var catalogCategories = categoryRepository.findByScoringSystemOrderByCode(
                division.getScoringSystem());

        // First pass: create main categories (no parent)
        var codeToId = new java.util.HashMap<String, UUID>();
        int sortOrder = 0;
        for (var cat : catalogCategories) {
            if (cat.getParentCode() == null) {
                var dc = new DivisionCategory(division.getId(), cat.getId(),
                        cat.getCode(), cat.getName(), cat.getDescription(), null, sortOrder++);
                var saved = divisionCategoryRepository.save(dc);
                codeToId.put(cat.getCode(), saved.getId());
            }
        }

        // Second pass: create subcategories with parent references
        for (var cat : catalogCategories) {
            if (cat.getParentCode() != null) {
                UUID parentId = codeToId.get(cat.getParentCode());
                var dc = new DivisionCategory(division.getId(), cat.getId(),
                        cat.getCode(), cat.getName(), cat.getDescription(), parentId, sortOrder++);
                divisionCategoryRepository.save(dc);
            }
        }
    }

    private void requireAuthorized(UUID competitionId, UUID userId) {
        if (!isAuthorized(competitionId, userId)) {
            throw new BusinessRuleException("error.auth.unauthorized");
        }
    }

    public List<String> findAdminEmailsByCompetitionId(@NotNull UUID competitionId) {
        return participantRepository.findByCompetitionId(competitionId).stream()
                .filter(p -> participantRoleRepository.existsByParticipantIdAndRole(
                        p.getId(), CompetitionRole.ADMIN))
                .map(p -> userService.findById(p.getUserId()).getEmail())
                .toList();
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
