package app.meads.judging.internal;

import app.meads.BusinessRuleException;
import app.meads.competition.CompetitionService;
import app.meads.entry.EntryStatus;
import app.meads.judging.CategoryJudgingConfig;
import app.meads.judging.Judging;
import app.meads.judging.JudgeProfileService;
import app.meads.judging.JudgingPhase;
import app.meads.judging.JudgingService;
import app.meads.judging.JudgingRound;
import app.meads.judging.JudgingRoundStatus;
import app.meads.judging.Medal;
import app.meads.judging.MedalAward;
import app.meads.judging.MedalRoundActivatedEvent;
import app.meads.judging.MedalRoundCompletedEvent;
import app.meads.judging.MedalRoundEntryRow;
import app.meads.judging.MedalRoundMode;
import app.meads.judging.MedalRoundScorePreview;
import app.meads.judging.MedalRoundReopenedEvent;
import app.meads.judging.MedalRoundResetEvent;
import app.meads.judging.MedalRoundStatus;
import app.meads.judging.BosCompletedEvent;
import app.meads.judging.BosPlacement;
import app.meads.judging.BosReopenedEvent;
import app.meads.judging.BosResetEvent;
import app.meads.judging.BosStartedEvent;
import app.meads.judging.CoiCheckService;
import app.meads.judging.ScoresheetService;
import app.meads.judging.ScoresheetStatus;
import app.meads.judging.RoundStartedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@Validated
public class JudgingServiceImpl implements JudgingService {

    private final JudgingRepository judgingRepository;
    private final JudgingRoundRepository judgingRoundRepository;
    private final PhysicalTableRepository physicalTableRepository;
    private final ScoresheetRepository scoresheetRepository;
    private final CategoryJudgingConfigRepository categoryConfigRepository;
    private final MedalAwardRepository medalAwardRepository;
    private final CompetitionService competitionService;
    private final JudgeProfileService judgeProfileService;
    private final ScoresheetService scoresheetService;
    private final BosPlacementRepository bosPlacementRepository;
    private final app.meads.entry.EntryService entryService;
    private final CoiCheckService coiCheckService;
    private final ApplicationEventPublisher eventPublisher;

    JudgingServiceImpl(JudgingRepository judgingRepository,
                       JudgingRoundRepository judgingRoundRepository,
                       PhysicalTableRepository physicalTableRepository,
                       ScoresheetRepository scoresheetRepository,
                       CategoryJudgingConfigRepository categoryConfigRepository,
                       MedalAwardRepository medalAwardRepository,
                       CompetitionService competitionService,
                       JudgeProfileService judgeProfileService,
                       ScoresheetService scoresheetService,
                       BosPlacementRepository bosPlacementRepository,
                       app.meads.entry.EntryService entryService,
                       CoiCheckService coiCheckService,
                       ApplicationEventPublisher eventPublisher) {
        this.judgingRepository = judgingRepository;
        this.judgingRoundRepository = judgingRoundRepository;
        this.physicalTableRepository = physicalTableRepository;
        this.scoresheetRepository = scoresheetRepository;
        this.categoryConfigRepository = categoryConfigRepository;
        this.medalAwardRepository = medalAwardRepository;
        this.competitionService = competitionService;
        this.judgeProfileService = judgeProfileService;
        this.scoresheetService = scoresheetService;
        this.bosPlacementRepository = bosPlacementRepository;
        this.entryService = entryService;
        this.coiCheckService = coiCheckService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public app.meads.judging.PhysicalTable createPhysicalTable(UUID divisionId, String label, UUID adminUserId) {
        if (!competitionService.isAuthorizedForDivision(divisionId, adminUserId)) {
            throw new BusinessRuleException("error.auth.unauthorized");
        }
        requireNotFrozen(divisionId);
        var trimmed = label == null ? "" : label.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessRuleException("error.physical-table.label-required");
        }
        if (physicalTableRepository.existsByDivisionIdAndLabel(divisionId, trimmed)) {
            throw new BusinessRuleException("error.physical-table.label-duplicate", trimmed);
        }
        var saved = physicalTableRepository.save(new app.meads.judging.PhysicalTable(divisionId, trimmed));
        log.info("Created PhysicalTable '{}' for division {}", trimmed, divisionId);
        return saved;
    }

    @Override
    public void updatePhysicalTableLabel(UUID physicalTableId, String label, UUID adminUserId) {
        var table = physicalTableRepository.findById(physicalTableId)
                .orElseThrow(() -> new BusinessRuleException("error.physical-table.not-found"));
        if (!competitionService.isAuthorizedForDivision(table.getDivisionId(), adminUserId)) {
            throw new BusinessRuleException("error.auth.unauthorized");
        }
        requireNotFrozen(table.getDivisionId());
        var trimmed = label == null ? "" : label.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessRuleException("error.physical-table.label-required");
        }
        if (!table.getLabel().equals(trimmed)
                && physicalTableRepository.existsByDivisionIdAndLabel(table.getDivisionId(), trimmed)) {
            throw new BusinessRuleException("error.physical-table.label-duplicate", trimmed);
        }
        table.updateLabel(trimmed);
        physicalTableRepository.save(table);
        log.info("Renamed PhysicalTable {} to '{}'", physicalTableId, trimmed);
    }

    @Override
    public void deletePhysicalTable(UUID physicalTableId, UUID adminUserId) {
        var table = physicalTableRepository.findById(physicalTableId)
                .orElseThrow(() -> new BusinessRuleException("error.physical-table.not-found"));
        if (!competitionService.isAuthorizedForDivision(table.getDivisionId(), adminUserId)) {
            throw new BusinessRuleException("error.auth.unauthorized");
        }
        requireNotFrozen(table.getDivisionId());
        boolean inUseByRound = judgingRoundRepository.findAll().stream()
                .anyMatch(r -> physicalTableId.equals(r.getPhysicalTableId()));
        if (inUseByRound) {
            throw new BusinessRuleException("error.physical-table.in-use-by-round");
        }
        boolean inUseByMedalRound = categoryConfigRepository.findAll().stream()
                .anyMatch(c -> physicalTableId.equals(c.getPhysicalTableId()));
        if (inUseByMedalRound) {
            throw new BusinessRuleException("error.physical-table.in-use-by-medal-round");
        }
        physicalTableRepository.delete(table);
        log.info("Deleted PhysicalTable {}", physicalTableId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<app.meads.judging.PhysicalTable> findPhysicalTablesByDivision(UUID divisionId) {
        return physicalTableRepository.findByDivisionIdOrderByLabel(divisionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<app.meads.judging.PhysicalTable> findPhysicalTableById(UUID physicalTableId) {
        return physicalTableRepository.findById(physicalTableId);
    }

    @Override
    public void assignRoundToPhysicalTable(UUID roundId, UUID physicalTableId, UUID adminUserId) {
        var round = requireTable(roundId);
        var judging = requireJudging(round.getJudgingId());
        if (!competitionService.isAuthorizedForDivision(judging.getDivisionId(), adminUserId)) {
            throw new BusinessRuleException("error.auth.unauthorized");
        }
        requireNotFrozen(judging.getDivisionId());
        if (round.getStatus() != JudgingRoundStatus.PENDING) {
            throw new BusinessRuleException("error.round.cannot-reassign-physical-table-after-start");
        }
        var table = physicalTableRepository.findById(physicalTableId)
                .orElseThrow(() -> new BusinessRuleException("error.physical-table.not-found"));
        if (!table.getDivisionId().equals(judging.getDivisionId())) {
            throw new BusinessRuleException("error.physical-table.wrong-division");
        }
        round.assignToPhysicalTable(physicalTableId);
        judgingRoundRepository.save(round);
        log.info("Assigned round {} to physical table {}", roundId, physicalTableId);
    }

    @Override
    public void assignMedalRoundToPhysicalTable(UUID divisionCategoryId, UUID physicalTableId, UUID adminUserId) {
        var divisionId = resolveDivisionIdFromCategory(divisionCategoryId);
        if (!competitionService.isAuthorizedForDivision(divisionId, adminUserId)) {
            throw new BusinessRuleException("error.auth.unauthorized");
        }
        requireNotFrozen(divisionId);
        var config = categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId)
                .orElseGet(() -> categoryConfigRepository.save(new CategoryJudgingConfig(divisionCategoryId)));
        if (physicalTableId != null) {
            var table = physicalTableRepository.findById(physicalTableId)
                    .orElseThrow(() -> new BusinessRuleException("error.physical-table.not-found"));
            if (!table.getDivisionId().equals(divisionId)) {
                throw new BusinessRuleException("error.physical-table.wrong-division");
            }
        }
        config.assignToPhysicalTable(physicalTableId);
        categoryConfigRepository.save(config);
        log.info("Assigned medal round for category {} to physical table {}", divisionCategoryId, physicalTableId);
    }

    @Override
    public Judging ensureJudgingExists(UUID divisionId) {
        return judgingRepository.findByDivisionId(divisionId)
                .orElseGet(() -> {
                    var judging = new Judging(divisionId);
                    var saved = judgingRepository.save(judging);
                    log.info("Created Judging row for division {}", divisionId);
                    return saved;
                });
    }

    @Override
    public JudgingRound createRound(UUID judgingId,
                                    String name,
                                    UUID divisionCategoryId,
                                    LocalDate scheduledDate,
                                    UUID adminUserId) {
        var judging = requireJudging(judgingId);
        requireAuthorizedForJudging(judging, adminUserId);
        requireNotFrozen(judging.getDivisionId());
        var table = new JudgingRound(judgingId, name, divisionCategoryId, scheduledDate);
        var saved = judgingRoundRepository.save(table);
        log.info("Created JudgingRound {} (name={}, category={})",
                saved.getId(), name, divisionCategoryId);
        return saved;
    }

    @Override
    public void assignEntryToRound(UUID roundId, UUID entryId, UUID adminUserId) {
        var round = requireTable(roundId);
        var judging = requireJudging(round.getJudgingId());
        requireAuthorizedForJudging(judging, adminUserId);
        requireNotFrozen(judging.getDivisionId());
        round.assignEntry(entryId);
        judgingRoundRepository.save(round);
        log.info("Assigned entry {} to round {}", entryId, roundId);
    }

    @Override
    public void unassignEntryFromRound(UUID roundId, UUID entryId, UUID adminUserId) {
        var round = requireTable(roundId);
        var judging = requireJudging(round.getJudgingId());
        requireAuthorizedForJudging(judging, adminUserId);
        requireNotFrozen(judging.getDivisionId());
        round.unassignEntry(entryId);
        judgingRoundRepository.save(round);
        log.info("Unassigned entry {} from round {}", entryId, roundId);
    }

    @Override
    public JudgingRound createMedalRound(UUID judgingId,
                                          UUID divisionCategoryId,
                                          UUID adminUserId) {
        var judging = requireJudging(judgingId);
        requireAuthorizedForJudging(judging, adminUserId);
        requireNotFrozen(judging.getDivisionId());
        var config = categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId)
                .orElseThrow(() -> new BusinessRuleException("error.medal-round.category-not-configured"));
        var round = new JudgingRound(judgingId, "Medal — " + divisionCategoryId, divisionCategoryId, null);
        round.convertToMedalRound(config.getMedalRoundMode());
        var saved = judgingRoundRepository.save(round);
        log.info("Created medal JudgingRound {} (category={}, mode={})",
                saved.getId(), divisionCategoryId, config.getMedalRoundMode());
        return saved;
    }

    @Override
    public void updateRoundName(UUID roundId, String name,
                                UUID adminUserId) {
        var table = requireTable(roundId);
        var judging = requireJudging(table.getJudgingId());
        requireAuthorizedForJudging(judging, adminUserId);
        requireNotFrozen(judging.getDivisionId());
        table.updateName(name);
        judgingRoundRepository.save(table);
        log.debug("Updated table name {} → '{}'", roundId, name);
    }

    @Override
    public void updateRoundScheduledDate(UUID roundId, LocalDate date,
                                          UUID adminUserId) {
        var table = requireTable(roundId);
        var judging = requireJudging(table.getJudgingId());
        requireAuthorizedForJudging(judging, adminUserId);
        requireNotFrozen(judging.getDivisionId());
        table.updateScheduledDate(date);
        judgingRoundRepository.save(table);
        log.debug("Updated table {} scheduled date → {}", roundId, date);
    }

    @Override
    public void deleteRound(UUID roundId, UUID adminUserId) {
        var table = requireTable(roundId);
        var judging = requireJudging(table.getJudgingId());
        requireAuthorizedForJudging(judging, adminUserId);
        requireNotFrozen(judging.getDivisionId());
        if (table.getStatus() != JudgingRoundStatus.PENDING) {
            throw new BusinessRuleException("error.judging-table.cannot-delete-started");
        }
        if (!table.getAssignments().isEmpty()) {
            throw new BusinessRuleException("error.judging-table.has-assignments");
        }
        judgingRoundRepository.delete(table);
        log.info("Deleted JudgingRound {}", roundId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JudgingRound> findRoundsByJudgingId(UUID judgingId) {
        return judgingRoundRepository.findByJudgingId(judgingId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findJudgeUserIdsForRound(UUID roundId) {
        return judgingRoundRepository.findById(roundId)
                .map(t -> t.getAssignments().stream()
                        .map(JudgeAssignment::getJudgeUserId)
                        .toList())
                .orElse(List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JudgingRound> findRoundById(UUID roundId) {
        return judgingRoundRepository.findById(roundId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JudgingRound> findRoundsByDivisionAndCategory(UUID divisionId, UUID divisionCategoryId) {
        return judgingRepository.findByDivisionId(divisionId)
                .map(j -> judgingRoundRepository.findByJudgingId(j.getId()).stream()
                        .filter(t -> t.getDivisionCategoryId().equals(divisionCategoryId))
                        .toList())
                .orElse(List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<JudgingRound> findRoundsByJudgeUserId(UUID judgeUserId) {
        return judgingRoundRepository.findByJudgeUserId(judgeUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAnyJudgeAssignment(UUID judgeUserId) {
        return judgingRoundRepository.existsAssignmentByJudgeUserId(judgeUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isJudgeAssignedToRound(UUID roundId, UUID judgeUserId) {
        return judgingRoundRepository.existsAssignmentByTableIdAndJudgeUserId(roundId, judgeUserId);
    }

    @Override
    public void assignJudge(UUID roundId, UUID judgeUserId,
                            UUID adminUserId) {
        var table = requireTable(roundId);
        var judging = requireJudging(table.getJudgingId());
        requireAuthorizedForJudging(judging, adminUserId);
        requireNotFrozen(judging.getDivisionId());
        // Assigning to an already-active round, the new judge must not be on
        // another active round elsewhere. (Pre-planning assignments to
        // NOT_STARTED rounds is fine — the conflict check fires again at
        // startRound time.)
        if (table.getStatus() == JudgingRoundStatus.ACTIVE) {
            boolean conflict = judgingRoundRepository.findAll().stream()
                    .filter(r -> !r.getId().equals(roundId))
                    .filter(r -> r.getStatus() == JudgingRoundStatus.ACTIVE)
                    .anyMatch(r -> r.getAssignments().stream()
                            .anyMatch(a -> a.getJudgeUserId().equals(judgeUserId)));
            if (conflict) {
                throw new BusinessRuleException("error.round.judge-active-conflict",
                        judgeUserId.toString());
            }
        }
        table.assignJudge(judgeUserId);
        judgingRoundRepository.save(table);
        judgeProfileService.ensureProfileForJudge(judgeUserId);
        log.info("Assigned judge {} to table {}", judgeUserId, roundId);
    }

    @Override
    public void removeJudge(UUID roundId, UUID judgeUserId,
                            UUID adminUserId) {
        var table = requireTable(roundId);
        var judging = requireJudging(table.getJudgingId());
        requireAuthorizedForJudging(judging, adminUserId);
        requireNotFrozen(judging.getDivisionId());
        if (table.getStatus() == JudgingRoundStatus.ACTIVE) {
            var division = competitionService.findDivisionById(judging.getDivisionId());
            int currentCount = table.getAssignments().size();
            boolean isAssigned = table.getAssignments().stream()
                    .anyMatch(a -> a.getJudgeUserId().equals(judgeUserId));
            int afterRemoval = isAssigned ? currentCount - 1 : currentCount;
            if (afterRemoval < division.getMinJudgesPerRound()) {
                throw new BusinessRuleException("error.judge-assignment.below-minimum",
                        String.valueOf(division.getMinJudgesPerRound()));
            }
        }
        table.removeJudge(judgeUserId);
        judgingRoundRepository.save(table);
        log.info("Removed judge {} from table {}", judgeUserId, roundId);
    }

    // === Table state transitions ===

    @Override
    public void startRound(UUID roundId, UUID adminUserId) {
        var table = requireTable(roundId);
        var judging = requireJudging(table.getJudgingId());
        requireAuthorizedForJudging(judging, adminUserId);
        var division = competitionService.findDivisionById(judging.getDivisionId());
        if (division.getStatus().isResultsFrozen()) {
            throw new BusinessRuleException("error.judging.results-published-frozen");
        }
        if (table.getPhysicalTableId() == null) {
            throw new BusinessRuleException("error.round.physical-table-required");
        }
        // Physical-table-busy check: no other active round at the same physical table.
        boolean physicalTableBusy = judgingRoundRepository.findByJudgingId(judging.getId()).stream()
                .filter(r -> !r.getId().equals(roundId))
                .filter(r -> table.getPhysicalTableId().equals(r.getPhysicalTableId()))
                .anyMatch(r -> r.getStatus() == JudgingRoundStatus.ACTIVE);
        if (physicalTableBusy) {
            throw new BusinessRuleException("error.round.physical-table-busy");
        }
        if (table.getAssignments().size() < division.getMinJudgesPerRound()) {
            throw new BusinessRuleException("error.judging-table.too-few-judges",
                    String.valueOf(division.getMinJudgesPerRound()));
        }
        // Judge active-conflict check: no assigned judge can be on another active round.
        var allActiveRounds = judgingRoundRepository.findAll().stream()
                .filter(r -> !r.getId().equals(roundId))
                .filter(r -> r.getStatus() == JudgingRoundStatus.ACTIVE)
                .toList();
        for (var assignment : table.getAssignments()) {
            var conflict = allActiveRounds.stream()
                    .anyMatch(r -> r.getAssignments().stream()
                            .anyMatch(a -> a.getJudgeUserId().equals(assignment.getJudgeUserId())));
            if (conflict) {
                throw new BusinessRuleException("error.round.judge-active-conflict",
                        assignment.getJudgeUserId().toString());
            }
        }
        try {
            table.start();
        } catch (IllegalStateException e) {
            throw new BusinessRuleException("error.judging-table.cannot-start", e.getMessage());
        }
        judgingRoundRepository.save(table);
        if (judging.getPhase() == JudgingPhase.NOT_STARTED) {
            judging.markActive();
            judgingRepository.save(judging);
        }
        // Ensure CategoryJudgingConfig exists (default COMPARATIVE) for the table's category
        categoryConfigRepository.findByDivisionCategoryId(table.getDivisionCategoryId())
                .orElseGet(() -> categoryConfigRepository.save(
                        new CategoryJudgingConfig(table.getDivisionCategoryId())));
        scoresheetService.createScoresheetsForTable(roundId);
        eventPublisher.publishEvent(new RoundStartedEvent(
                table.getId(), table.getDivisionCategoryId(),
                judging.getDivisionId(), Instant.now()));
        log.info("Started table {} in division {}", roundId, judging.getDivisionId());
    }

    // === Category medal-round configuration ===

    @Override
    public CategoryJudgingConfig configureCategoryMedalRound(UUID divisionCategoryId,
                                                              MedalRoundMode mode,
                                                              UUID adminUserId) {
        // Authorization: load division of the category via competitionService
        var divisionId = resolveDivisionIdFromCategory(divisionCategoryId);
        if (!competitionService.isAuthorizedForDivision(divisionId, adminUserId)) {
            throw new BusinessRuleException("error.auth.unauthorized");
        }
        requireNotFrozen(divisionId);
        var existing = categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId);
        CategoryJudgingConfig config;
        if (existing.isPresent()) {
            config = existing.get();
            try {
                config.updateMode(mode);
            } catch (IllegalStateException e) {
                throw new BusinessRuleException("error.category-config.mode-locked", e.getMessage());
            }
        } else {
            config = new CategoryJudgingConfig(divisionCategoryId, mode);
        }
        log.info("Configured medal round mode for category {} → {}", divisionCategoryId, mode);
        return categoryConfigRepository.save(config);
    }

    @Override
    public List<MedalAward> findMedalAwardsForCategory(UUID divisionCategoryId) {
        return medalAwardRepository.findByFinalCategoryId(divisionCategoryId);
    }

    @Override
    public List<MedalRoundEntryRow> findMedalRoundEntries(UUID divisionCategoryId,
                                                          MedalRoundMode mode) {
        var rows = new ArrayList<MedalRoundEntryRow>();
        for (var entry : entryService.findEntriesByFinalCategoryId(divisionCategoryId)) {
            // A withdrawn entry drops out of the medal round even if it was judged.
            if (entry.getStatus() != EntryStatus.RECEIVED) {
                continue;
            }
            var sheetOpt = scoresheetRepository.findByEntryId(entry.getId());
            if (sheetOpt.isEmpty() || sheetOpt.get().getStatus() != ScoresheetStatus.SUBMITTED) {
                continue;
            }
            var sheet = sheetOpt.get();
            if (mode == MedalRoundMode.COMPARATIVE && !sheet.isAdvancedToMedalRound()) {
                continue;
            }
            var medalOpt = medalAwardRepository.findByEntryId(entry.getId());
            rows.add(new MedalRoundEntryRow(
                    entry.getId(), entry.getEntryCode(), entry.getMeadName(),
                    entry.getUserId(), sheet.getTotalScore(), sheet.isAdvancedToMedalRound(),
                    medalOpt.map(MedalAward::getId).orElse(null),
                    medalOpt.map(MedalAward::getMedal).orElse(null)));
        }
        if (mode == MedalRoundMode.SCORE_BASED) {
            rows.sort(Comparator.comparing(MedalRoundEntryRow::round1Total,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        }
        return rows;
    }

    @Override
    public MedalRoundScorePreview recomputeScorePreview(UUID divisionCategoryId) {
        var rows = findMedalRoundEntries(divisionCategoryId, MedalRoundMode.SCORE_BASED);
        long medaled = rows.stream().filter(r -> r.currentMedal() != null).count();
        int openSlots = Math.max(0, Medal.values().length - (int) medaled);
        var unresolved = rows.stream()
                .filter(r -> r.medalAwardId() == null)
                .toList();
        if (openSlots == 0 || unresolved.isEmpty()) {
            return new MedalRoundScorePreview(0, Set.of());
        }
        var topScore = unresolved.get(0).round1Total();
        var tied = unresolved.stream()
                .filter(r -> Objects.equals(r.round1Total(), topScore))
                .toList();
        if (tied.size() > 1) {
            return new MedalRoundScorePreview(openSlots, tied.stream()
                    .map(MedalRoundEntryRow::entryId)
                    .collect(Collectors.toSet()));
        }
        return new MedalRoundScorePreview(0, Set.of());
    }

    @Override
    public List<MedalAward> findGoldMedalAwardsForDivision(UUID divisionId, UUID adminUserId) {
        requireAuthorizedForDivision(divisionId, adminUserId);
        return medalAwardRepository.findByDivisionId(divisionId).stream()
                .filter(a -> a.getMedal() == Medal.GOLD)
                .filter(MedalAward::isConfirmed)
                .toList();
    }

    @Override
    public List<BosPlacement> findBosPlacementsForDivision(UUID divisionId, UUID adminUserId) {
        requireAuthorizedForDivision(divisionId, adminUserId);
        return bosPlacementRepository.findByDivisionIdOrderByPlace(divisionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MedalAward> findMedalAwardByEntryId(UUID entryId) {
        return medalAwardRepository.findByEntryId(entryId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BosPlacement> findBosPlacementByEntryId(UUID entryId) {
        return bosPlacementRepository.findByEntryId(entryId);
    }

    @Override
    public List<CategoryJudgingConfig> findCategoryConfigsForDivision(UUID divisionId, UUID adminUserId) {
        if (!competitionService.isAuthorizedForDivision(divisionId, adminUserId)) {
            throw new BusinessRuleException("error.auth.unauthorized");
        }
        return competitionService.findJudgingCategories(divisionId).stream()
                .map(cat -> categoryConfigRepository.findByDivisionCategoryId(cat.getId())
                        .orElseGet(() -> categoryConfigRepository.save(
                                new CategoryJudgingConfig(cat.getId()))))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CategoryJudgingConfig> findCategoryConfigByDivisionCategoryId(UUID divisionCategoryId) {
        return categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryJudgingConfig> findActiveCategoryConfigsForJudge(UUID judgeUserId) {
        var tables = judgingRoundRepository.findByJudgeUserId(judgeUserId);
        return tables.stream()
                .map(JudgingRound::getDivisionCategoryId)
                .distinct()
                .flatMap(catId -> categoryConfigRepository.findByDivisionCategoryId(catId).stream())
                .filter(c -> c.getMedalRoundStatus() == MedalRoundStatus.ACTIVE)
                .toList();
    }

    // === Medal round transitions ===

    @Override
    public void startMedalRound(UUID divisionCategoryId, UUID adminUserId) {
        var config = requireConfig(divisionCategoryId);
        var divisionId = resolveDivisionIdFromCategory(divisionCategoryId);
        requireAuthorizedForDivision(divisionId, adminUserId);
        requireNotFrozen(divisionId);
        try {
            config.startMedalRound();
        } catch (IllegalStateException e) {
            throw new BusinessRuleException("error.medal-round.cannot-start", e.getMessage());
        }
        if (config.getMedalRoundMode() == MedalRoundMode.SCORE_BASED) {
            autoPopulateMedalsByScore(divisionCategoryId, divisionId, adminUserId);
        }
        categoryConfigRepository.save(config);
        eventPublisher.publishEvent(new MedalRoundActivatedEvent(
                divisionCategoryId, divisionId, config.getMedalRoundMode(), Instant.now()));
        log.info("Started medal round for category {} (mode={})",
                divisionCategoryId, config.getMedalRoundMode());
    }

    @Override
    public void completeMedalRound(UUID divisionCategoryId, UUID adminUserId) {
        var config = requireConfig(divisionCategoryId);
        var divisionId = resolveDivisionIdFromCategory(divisionCategoryId);
        requireAuthorizedForDivision(divisionId, adminUserId);
        requireNotFrozen(divisionId);
        try {
            config.completeMedalRound();
        } catch (IllegalStateException e) {
            throw new BusinessRuleException("error.medal-round.cannot-complete", e.getMessage());
        }
        categoryConfigRepository.save(config);
        eventPublisher.publishEvent(new MedalRoundCompletedEvent(
                divisionCategoryId, divisionId, Instant.now()));
        log.info("Completed medal round for category {}", divisionCategoryId);
    }

    @Override
    public void reopenMedalRound(UUID divisionCategoryId, UUID adminUserId) {
        var config = requireConfig(divisionCategoryId);
        var divisionId = resolveDivisionIdFromCategory(divisionCategoryId);
        requireAuthorizedForDivision(divisionId, adminUserId);
        requireNotFrozen(divisionId);
        var judging = judgingRepository.findByDivisionId(divisionId)
                .orElseThrow(() -> new BusinessRuleException("error.judging.not-found"));
        if (judging.getPhase() != JudgingPhase.ACTIVE) {
            throw new BusinessRuleException("error.medal-round.judging-not-active");
        }
        try {
            config.reopenMedalRound();
        } catch (IllegalStateException e) {
            throw new BusinessRuleException("error.medal-round.cannot-reopen", e.getMessage());
        }
        categoryConfigRepository.save(config);
        eventPublisher.publishEvent(new MedalRoundReopenedEvent(
                divisionCategoryId, divisionId, Instant.now()));
        log.info("Reopened medal round for category {}", divisionCategoryId);
    }

    @Override
    public void resetMedalRound(UUID divisionCategoryId, UUID adminUserId) {
        var config = requireConfig(divisionCategoryId);
        var divisionId = resolveDivisionIdFromCategory(divisionCategoryId);
        requireAuthorizedForDivision(divisionId, adminUserId);
        requireNotFrozen(divisionId);
        var judging = judgingRepository.findByDivisionId(divisionId)
                .orElseThrow(() -> new BusinessRuleException("error.judging.not-found"));
        if (judging.getPhase() != JudgingPhase.ACTIVE) {
            throw new BusinessRuleException("error.medal-round.judging-not-active");
        }
        var awards = medalAwardRepository.findByFinalCategoryId(divisionCategoryId);
        int wiped = awards.size();
        medalAwardRepository.deleteAll(awards);
        try {
            config.resetMedalRound();
        } catch (IllegalStateException e) {
            throw new BusinessRuleException("error.medal-round.cannot-reset", e.getMessage());
        }
        categoryConfigRepository.save(config);
        eventPublisher.publishEvent(new MedalRoundResetEvent(
                divisionCategoryId, divisionId, wiped, Instant.now()));
        log.info("Reset medal round for category {} (wiped {} awards)", divisionCategoryId, wiped);
    }

    // === Medal awards ===

    @Override
    public MedalAward recordMedal(UUID entryId, Medal medal,
                                   UUID judgeUserId) {
        var entry = entryService.findEntryById(entryId);
        var finalCategoryId = entry.getFinalCategoryId();
        if (finalCategoryId == null) {
            throw new BusinessRuleException("error.medal.no-final-category");
        }
        requireNotFrozen(entry.getDivisionId());
        var coi = coiCheckService.check(judgeUserId, entryId);
        if (coi.hardBlock()) {
            throw new BusinessRuleException("error.coi.self-entry");
        }
        var config = categoryConfigRepository.findByDivisionCategoryId(finalCategoryId)
                .orElseThrow(() -> new BusinessRuleException("error.category-config.not-found"));
        if (config.getMedalRoundStatus() != MedalRoundStatus.ACTIVE) {
            throw new BusinessRuleException("error.medal-round.not-active");
        }
        requireAuthorizedForMedalAction(entry.getDivisionId(), finalCategoryId, judgeUserId);
        var existing = medalAwardRepository.findByEntryId(entryId);
        MedalAward award;
        if (existing.isPresent()) {
            award = existing.get();
            award.updateMedal(medal, judgeUserId);
        } else {
            award = new MedalAward(entryId, entry.getDivisionId(), finalCategoryId,
                    medal, judgeUserId);
        }
        award.confirm(judgeUserId);
        var saved = medalAwardRepository.save(award);
        log.info("Recorded medal {} for entry {} by judge {}", medal, entryId, judgeUserId);
        return saved;
    }

    @Override
    public void updateMedal(UUID medalAwardId, Medal newValue,
                            UUID judgeUserId) {
        var award = medalAwardRepository.findById(medalAwardId)
                .orElseThrow(() -> new BusinessRuleException("error.medal.not-found"));
        requireNotFrozen(award.getDivisionId());
        var coi = coiCheckService.check(judgeUserId, award.getEntryId());
        if (coi.hardBlock()) {
            throw new BusinessRuleException("error.coi.self-entry");
        }
        var config = categoryConfigRepository.findByDivisionCategoryId(award.getFinalCategoryId())
                .orElseThrow(() -> new BusinessRuleException("error.category-config.not-found"));
        if (config.getMedalRoundStatus() != MedalRoundStatus.ACTIVE) {
            throw new BusinessRuleException("error.medal-round.not-active");
        }
        requireAuthorizedForMedalAction(award.getDivisionId(), award.getFinalCategoryId(), judgeUserId);
        award.updateMedal(newValue, judgeUserId);
        award.confirm(judgeUserId);
        medalAwardRepository.save(award);
        log.info("Updated medal {} → {} by judge {}", medalAwardId, newValue, judgeUserId);
    }

    @Override
    public void confirmMedalAward(UUID medalAwardId, UUID adminUserId) {
        var award = medalAwardRepository.findById(medalAwardId)
                .orElseThrow(() -> new BusinessRuleException("error.medal.not-found"));
        requireNotFrozen(award.getDivisionId());
        requireAuthorizedForDivision(award.getDivisionId(), adminUserId);
        award.confirm(adminUserId);
        medalAwardRepository.save(award);
        log.info("Confirmed medal award {} by {}", medalAwardId, adminUserId);
    }

    @Override
    public void deleteMedalAward(UUID medalAwardId, UUID judgeUserId) {
        var award = medalAwardRepository.findById(medalAwardId)
                .orElseThrow(() -> new BusinessRuleException("error.medal.not-found"));
        requireNotFrozen(award.getDivisionId());
        var config = categoryConfigRepository.findByDivisionCategoryId(award.getFinalCategoryId())
                .orElseThrow(() -> new BusinessRuleException("error.category-config.not-found"));
        if (config.getMedalRoundStatus() != MedalRoundStatus.ACTIVE) {
            throw new BusinessRuleException("error.medal-round.not-active");
        }
        requireAuthorizedForMedalAction(award.getDivisionId(), award.getFinalCategoryId(), judgeUserId);
        medalAwardRepository.delete(award);
        log.info("Deleted medal award {} by judge {}", medalAwardId, judgeUserId);
    }

    // === BOS lifecycle (admin-only per §Q15) ===

    @Override
    public void startBos(UUID divisionId, UUID adminUserId) {
        requireAuthorizedForDivision(divisionId, adminUserId);
        requireNotFrozen(divisionId);
        var judging = judgingRepository.findByDivisionId(divisionId)
                .orElseThrow(() -> new BusinessRuleException("error.judging.not-found"));
        // Guard: every CategoryJudgingConfig for this division must be COMPLETE
        var divCategories = competitionService.findDivisionCategories(divisionId);
        for (var cat : divCategories) {
            var config = categoryConfigRepository.findByDivisionCategoryId(cat.getId());
            if (config.isPresent() && config.get().getMedalRoundStatus() != MedalRoundStatus.COMPLETE) {
                throw new BusinessRuleException("error.bos.medal-rounds-incomplete");
            }
        }
        try {
            judging.startBos();
        } catch (IllegalStateException e) {
            throw new BusinessRuleException("error.bos.cannot-start", e.getMessage());
        }
        judgingRepository.save(judging);
        eventPublisher.publishEvent(new BosStartedEvent(divisionId, Instant.now()));
        log.info("Started BOS for division {}", divisionId);
    }

    @Override
    public void completeBos(UUID divisionId, UUID adminUserId) {
        requireAuthorizedForDivision(divisionId, adminUserId);
        requireNotFrozen(divisionId);
        var judging = judgingRepository.findByDivisionId(divisionId)
                .orElseThrow(() -> new BusinessRuleException("error.judging.not-found"));
        try {
            judging.completeBos();
        } catch (IllegalStateException e) {
            throw new BusinessRuleException("error.bos.cannot-complete", e.getMessage());
        }
        judgingRepository.save(judging);
        var placements = bosPlacementRepository.findByDivisionIdOrderByPlace(divisionId);
        eventPublisher.publishEvent(new BosCompletedEvent(
                divisionId, placements.size(), Instant.now()));
        log.info("Completed BOS for division {}", divisionId);
    }

    @Override
    public void reopenBos(UUID divisionId, UUID adminUserId) {
        requireAuthorizedForDivision(divisionId, adminUserId);
        requireNotFrozen(divisionId);
        var judging = judgingRepository.findByDivisionId(divisionId)
                .orElseThrow(() -> new BusinessRuleException("error.judging.not-found"));
        try {
            judging.reopenBos();
        } catch (IllegalStateException e) {
            throw new BusinessRuleException("error.bos.cannot-reopen", e.getMessage());
        }
        judgingRepository.save(judging);
        eventPublisher.publishEvent(new BosReopenedEvent(divisionId, Instant.now()));
        log.info("Reopened BOS for division {}", divisionId);
    }

    @Override
    public void resetBos(UUID divisionId, UUID adminUserId) {
        requireAuthorizedForDivision(divisionId, adminUserId);
        requireNotFrozen(divisionId);
        var judging = judgingRepository.findByDivisionId(divisionId)
                .orElseThrow(() -> new BusinessRuleException("error.judging.not-found"));
        var placements = bosPlacementRepository.findByDivisionIdOrderByPlace(divisionId);
        if (!placements.isEmpty()) {
            throw new BusinessRuleException("error.bos.placements-exist");
        }
        try {
            judging.resetBos();
        } catch (IllegalStateException e) {
            throw new BusinessRuleException("error.bos.cannot-reset", e.getMessage());
        }
        judgingRepository.save(judging);
        eventPublisher.publishEvent(new BosResetEvent(divisionId, Instant.now()));
        log.info("Reset BOS for division {}", divisionId);
    }

    // === BOS placements ===

    @Override
    public BosPlacement recordBosPlacement(UUID divisionId, UUID entryId,
                                            int place, UUID adminUserId) {
        requireAuthorizedForDivision(divisionId, adminUserId);
        var division = competitionService.findDivisionById(divisionId);
        if (division.getStatus().isResultsFrozen()) {
            throw new BusinessRuleException("error.judging.results-published-frozen");
        }
        var judging = judgingRepository.findByDivisionId(divisionId)
                .orElseThrow(() -> new BusinessRuleException("error.judging.not-found"));
        if (judging.getPhase() != JudgingPhase.BOS) {
            throw new BusinessRuleException("error.bos.not-active");
        }
        if (place < 1 || place > division.getBosPlaces()) {
            throw new BusinessRuleException("error.bos.invalid-place",
                    String.valueOf(place), String.valueOf(division.getBosPlaces()));
        }
        var medal = medalAwardRepository.findByEntryId(entryId);
        if (medal.isEmpty() || medal.get().getMedal() != Medal.GOLD) {
            throw new BusinessRuleException("error.bos.entry-not-gold");
        }
        var existingAtEntry = bosPlacementRepository.findByEntryId(entryId);
        BosPlacement placement;
        if (existingAtEntry.isPresent()) {
            placement = existingAtEntry.get();
            try {
                placement.updatePlace(place, adminUserId);
            } catch (IllegalArgumentException e) {
                throw new BusinessRuleException("error.bos.invalid-place",
                        String.valueOf(place), String.valueOf(division.getBosPlaces()));
            }
        } else {
            placement = new BosPlacement(divisionId, entryId, place, adminUserId);
        }
        var saved = bosPlacementRepository.save(placement);
        log.info("Recorded BOS placement {} for entry {} in division {}",
                place, entryId, divisionId);
        return saved;
    }

    @Override
    public void updateBosPlacement(UUID placementId, int place, UUID adminUserId) {
        var placement = bosPlacementRepository.findById(placementId)
                .orElseThrow(() -> new BusinessRuleException("error.bos.placement-not-found"));
        requireAuthorizedForDivision(placement.getDivisionId(), adminUserId);
        var division = competitionService.findDivisionById(placement.getDivisionId());
        if (division.getStatus().isResultsFrozen()) {
            throw new BusinessRuleException("error.judging.results-published-frozen");
        }
        if (place < 1 || place > division.getBosPlaces()) {
            throw new BusinessRuleException("error.bos.invalid-place",
                    String.valueOf(place), String.valueOf(division.getBosPlaces()));
        }
        placement.updatePlace(place, adminUserId);
        bosPlacementRepository.save(placement);
        log.info("Updated BOS placement {} → place {}", placementId, place);
    }

    @Override
    public void deleteBosPlacement(UUID placementId, UUID adminUserId) {
        var placement = bosPlacementRepository.findById(placementId)
                .orElseThrow(() -> new BusinessRuleException("error.bos.placement-not-found"));
        requireAuthorizedForDivision(placement.getDivisionId(), adminUserId);
        requireNotFrozen(placement.getDivisionId());
        bosPlacementRepository.delete(placement);
        log.info("Deleted BOS placement {}", placementId);
    }

    // --- score-based auto-population (§2.D D10) ---

    private void autoPopulateMedalsByScore(UUID divisionCategoryId, UUID divisionId,
                                            UUID adminUserId) {
        // Walk gold→silver→bronze; stop cascade on first tie within a slot
        var sheetsByEntry = new HashMap<UUID, Integer>();
        // Find SUBMITTED scoresheets for entries advancedToMedalRound = true and finalCategoryId matches
        var allTables = judgingRoundRepository.findByJudgingId(
                judgingRepository.findByDivisionId(divisionId)
                        .orElseThrow().getId()).stream()
                .filter(t -> t.getDivisionCategoryId().equals(divisionCategoryId))
                .toList();
        var allSheets = new ArrayList<app.meads.judging.Scoresheet>();
        for (var table : allTables) {
            allSheets.addAll(scoresheetRepository.findByRoundId(table.getId()));
        }
        for (var sheet : allSheets) {
            if (sheet.getStatus() == ScoresheetStatus.SUBMITTED
                    && sheet.isAdvancedToMedalRound()
                    && sheet.getTotalScore() != null) {
                sheetsByEntry.merge(sheet.getEntryId(), sheet.getTotalScore(), Integer::max);
            }
        }
        var ranked = sheetsByEntry.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .toList();
        var medalsToAssign = List.of(Medal.GOLD, Medal.SILVER, Medal.BRONZE);
        int rankIdx = 0;
        for (Medal medal : medalsToAssign) {
            if (rankIdx >= ranked.size()) break;
            var slot = ranked.get(rankIdx);
            int slotScore = slot.getValue();
            // Tie check: stop cascade if multiple entries share this score
            long tieCount = ranked.subList(rankIdx, ranked.size()).stream()
                    .takeWhile(e -> e.getValue() == slotScore)
                    .count();
            if (tieCount > 1) {
                break;
            }
            var existing = medalAwardRepository.findByEntryId(slot.getKey());
            if (existing.isEmpty()) {
                medalAwardRepository.save(new MedalAward(
                        slot.getKey(), divisionId, divisionCategoryId, medal, adminUserId));
            }
            rankIdx++;
        }
    }

    // --- helpers ---

    private CategoryJudgingConfig requireConfig(UUID divisionCategoryId) {
        return categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId)
                .orElseThrow(() -> new BusinessRuleException("error.category-config.not-found"));
    }

    private void requireAuthorizedForDivision(UUID divisionId, UUID userId) {
        if (!competitionService.isAuthorizedForDivision(divisionId, userId)) {
            throw new BusinessRuleException("error.auth.unauthorized");
        }
    }

    private void requireNotFrozen(UUID divisionId) {
        if (competitionService.findDivisionById(divisionId).getStatus().isResultsFrozen()) {
            throw new BusinessRuleException("error.judging.results-published-frozen");
        }
    }

    private void requireAuthorizedForMedalAction(UUID divisionId, UUID divisionCategoryId,
                                                  UUID userId) {
        if (competitionService.isAuthorizedForDivision(divisionId, userId)) {
            return;
        }
        // Otherwise must be a judge assigned to a table covering this category
        var assignedTables = judgingRoundRepository.findByJudgeUserId(userId);
        boolean coversCategory = assignedTables.stream()
                .anyMatch(t -> t.getDivisionCategoryId().equals(divisionCategoryId));
        if (!coversCategory) {
            throw new BusinessRuleException("error.auth.unauthorized");
        }
    }

    private UUID resolveDivisionIdFromCategory(UUID divisionCategoryId) {
        return competitionService.findDivisionCategoryById(divisionCategoryId).getDivisionId();
    }

    private Judging requireJudging(UUID judgingId) {
        return judgingRepository.findById(judgingId)
                .orElseThrow(() -> new BusinessRuleException("error.judging.not-found"));
    }

    private JudgingRound requireTable(UUID roundId) {
        return judgingRoundRepository.findById(roundId)
                .orElseThrow(() -> new BusinessRuleException("error.judging-table.not-found"));
    }

    private void requireAuthorizedForJudging(Judging judging, UUID userId) {
        if (!competitionService.isAuthorizedForDivision(judging.getDivisionId(), userId)) {
            throw new BusinessRuleException("error.auth.unauthorized");
        }
    }

    // Ensure JudgingPhase imported for future use
    @SuppressWarnings("unused")
    private static final JudgingPhase ANY = JudgingPhase.NOT_STARTED;
}
