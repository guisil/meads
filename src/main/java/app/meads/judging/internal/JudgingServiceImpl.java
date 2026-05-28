package app.meads.judging.internal;

import app.meads.BusinessRuleException;
import app.meads.competition.CompetitionService;
import app.meads.competition.DivisionStatus;
import app.meads.competition.DivisionStatusAdvancedEvent;
import app.meads.judging.PhysicalTable;
import app.meads.entry.Entry;
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
import app.meads.judging.RoundType;
import app.meads.judging.BosCompletedEvent;
import app.meads.judging.BosPlacement;
import app.meads.judging.BosReopenedEvent;
import app.meads.judging.BosResetEvent;
import app.meads.judging.BosStartedEvent;
import app.meads.judging.CoiCheckService;
import app.meads.judging.Scoresheet;
import app.meads.judging.ScoresheetService;
import app.meads.judging.ScoresheetStatus;
import app.meads.judging.RoundStartedEvent;
import app.meads.judging.ScoresheetSubmittedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
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
    private final app.meads.identity.UserService userService;
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
                       app.meads.identity.UserService userService,
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
        this.userService = userService;
        this.judgeProfileService = judgeProfileService;
        this.scoresheetService = scoresheetService;
        this.bosPlacementRepository = bosPlacementRepository;
        this.entryService = entryService;
        this.coiCheckService = coiCheckService;
        this.eventPublisher = eventPublisher;
    }

    private String judgeNameForError(UUID judgeUserId) {
        try {
            var user = userService.findById(judgeUserId);
            return user.getName();
        } catch (Exception ex) {
            return judgeUserId.toString();
        }
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
        if (round.getStatus() != JudgingRoundStatus.PENDING
                && round.getStatus() != JudgingRoundStatus.READY) {
            throw new BusinessRuleException("error.round.cannot-reassign-physical-table-after-start");
        }
        var table = physicalTableRepository.findById(physicalTableId)
                .orElseThrow(() -> new BusinessRuleException("error.physical-table.not-found"));
        if (!table.getDivisionId().equals(judging.getDivisionId())) {
            throw new BusinessRuleException("error.physical-table.wrong-division");
        }
        round.assignToPhysicalTable(physicalTableId);
        recomputeScoringRoundReadiness(round);
        judgingRoundRepository.save(round);
        log.info("Assigned round {} to physical table {}", roundId, physicalTableId);
    }

    /**
     * Auto-toggles a SCORING round between PENDING and READY based on whether it
     * is fully configured (physical table + ≥ minJudgesPerRound judges + ≥ 1 entry)
     * AND the division has advanced to JUDGING. Skips rounds whose status isn't one
     * of {PENDING, READY} (ACTIVE / COMPLETE rounds are owned by their own
     * transitions). Medal rounds are skipped — their READY is cascade-driven.
     */
    private void recomputeScoringRoundReadiness(JudgingRound round) {
        var judging = judgingRepository.findById(round.getJudgingId()).orElse(null);
        if (judging == null) {
            return;
        }
        var division = competitionService.findDivisionById(judging.getDivisionId());
        recomputeScoringRoundReadiness(round, division);
    }

    private void recomputeScoringRoundReadiness(JudgingRound round,
                                                 app.meads.competition.Division division) {
        // Applies to SCORING rounds and to SCORE_BASED medal rounds (small-
        // category flow — the medal round acts as a scoring panel and uses the
        // same configuration-driven readiness rule). COMPARATIVE medal rounds
        // remain on the cascade-driven readiness model (READY when every prelim
        // scoring round in the category COMPLETEs).
        boolean medalRoundActsAsScoringPanel = round.getType() == RoundType.MEDAL
                && round.getMedalMode() == MedalRoundMode.SCORE_BASED;
        if (round.getType() != RoundType.SCORING && !medalRoundActsAsScoringPanel) {
            return;
        }
        if (round.getStatus() != JudgingRoundStatus.PENDING
                && round.getStatus() != JudgingRoundStatus.READY) {
            return;
        }
        boolean shouldBeReady = isScoringRoundReadyToStart(round, division);
        if (shouldBeReady && round.getStatus() == JudgingRoundStatus.PENDING) {
            round.markReady();
        } else if (!shouldBeReady && round.getStatus() == JudgingRoundStatus.READY) {
            round.markPending();
        }
    }

    private boolean isScoringRoundReadyToStart(JudgingRound round,
                                                app.meads.competition.Division division) {
        if (division.getStatus().ordinal() < DivisionStatus.JUDGING.ordinal()) {
            return false;
        }
        if (round.getPhysicalTableId() == null) {
            return false;
        }
        if (round.getAssignments().size() < division.getMinJudgesPerRound()) {
            return false;
        }
        return !round.getEntries().isEmpty();
    }

    @Override
    public void recomputeReadinessForDivision(UUID divisionId) {
        var judging = judgingRepository.findByDivisionId(divisionId).orElse(null);
        if (judging == null) {
            return;
        }
        var division = competitionService.findDivisionById(divisionId);
        for (var round : judgingRoundRepository.findByJudgingId(judging.getId())) {
            var statusBefore = round.getStatus();
            recomputeScoringRoundReadiness(round, division);
            if (round.getStatus() != statusBefore) {
                judgingRoundRepository.save(round);
            }
        }
    }

    /**
     * Re-runs medal auto-populate once every sheet on a SCORE_BASED medal round
     * is SUBMITTED. The small-category flow (medal round owns its sheets, no
     * preceding scoring round) doesn't have any SUBMITTED sheets at
     * {@code startRound} time, so the auto-populate call there is a no-op;
     * this listener fills in once the round's judges finish. Idempotent — the
     * {@code autoPopulateMedalsByScore} write check skips entries that already
     * have a MedalAward, so repeated firings are safe. Visibility is public
     * so the unit tests can invoke it directly.
     */
    @EventListener
    public void onScoresheetSubmitted(ScoresheetSubmittedEvent event) {
        var round = judgingRoundRepository.findById(event.roundId()).orElse(null);
        if (round == null
                || round.getType() != RoundType.MEDAL
                || round.getMedalMode() != MedalRoundMode.SCORE_BASED) {
            return;
        }
        long pending = scoresheetService.countByRoundIdAndStatusNot(
                round.getId(), ScoresheetStatus.SUBMITTED);
        if (pending > 0) {
            return;
        }
        var sheet = scoresheetService.findById(event.scoresheetId()).orElse(null);
        if (sheet == null) {
            return;
        }
        var judging = judgingRepository.findById(round.getJudgingId()).orElse(null);
        if (judging == null) {
            return;
        }
        autoPopulateMedalsByScore(round.getDivisionCategoryId(),
                judging.getDivisionId(), sheet.getFilledByJudgeUserId());
    }

    @EventListener
    void onDivisionStatusAdvanced(DivisionStatusAdvancedEvent event) {
        // Recompute on any cross-JUDGING transition: forward (to JUDGING+) makes
        // configured rounds eligible for READY; the listener short-circuits when
        // the new status is < JUDGING (predicate fails for all rounds anyway).
        if (event.newStatus().ordinal() < DivisionStatus.JUDGING.ordinal()) {
            return;
        }
        recomputeReadinessForDivision(event.divisionId());
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
        var trimmedName = name == null ? "" : name.trim();
        // Round names must be unique within a judging (= within a division) so
        // grids + error messages refer to rounds unambiguously.
        boolean nameTaken = judgingRoundRepository.findByJudgingId(judgingId).stream()
                .anyMatch(r -> trimmedName.equalsIgnoreCase(r.getName()));
        if (nameTaken) {
            throw new BusinessRuleException("error.round.name-duplicate", trimmedName);
        }
        var table = new JudgingRound(judgingId, trimmedName, divisionCategoryId, scheduledDate);
        var saved = judgingRoundRepository.save(table);
        log.info("Created JudgingRound {} (name={}, category={})",
                saved.getId(), trimmedName, divisionCategoryId);
        return saved;
    }

    @Override
    public void assignEntryToRound(UUID roundId, UUID entryId, UUID adminUserId) {
        var round = requireTable(roundId);
        var judging = requireJudging(round.getJudgingId());
        requireAuthorizedForJudging(judging, adminUserId);
        requireNotFrozen(judging.getDivisionId());
        if (round.getStatus() == JudgingRoundStatus.COMPLETE) {
            throw new BusinessRuleException("error.entry.cannot-change-on-complete-round");
        }
        if (round.getType() == RoundType.SCORING) {
            // Enforce 1:1 entry-to-scoring-round (redesign decision #1, also DB
            // UNIQUE on judging_round_entries.entry_id). Throw a helpful error
            // before the DB constraint fires.
            var existingAssignment = judgingRoundRepository.findByJudgingId(judging.getId()).stream()
                    .filter(r -> r.getType() == RoundType.SCORING)
                    .filter(r -> !r.getId().equals(roundId))
                    .filter(r -> r.getEntries().contains(entryId))
                    .findFirst();
            if (existingAssignment.isPresent()) {
                throw new BusinessRuleException("error.entry.already-on-round",
                        existingAssignment.get().getName());
            }
        }
        round.assignEntry(entryId);
        recomputeScoringRoundReadiness(round);
        judgingRoundRepository.save(round);
        // Mid-round add: create the BLANK scoresheet so the round's judges can
        // start scoring this entry immediately. SCORING rounds delegate the
        // round-lookup-by-category to ensureScoresheetForEntry; SCORE_BASED
        // medal rounds (small-category flow with no preceding scoring round)
        // pin the round explicitly via ensureScoresheetForRound so the sheet
        // lands at this medal round even when no ACTIVE scoring round exists
        // for the category. COMPARATIVE medal rounds own no sheets.
        if (round.getStatus() == JudgingRoundStatus.ACTIVE) {
            if (round.getType() == RoundType.SCORING) {
                scoresheetService.ensureScoresheetForEntry(entryId);
            } else if (round.getMedalMode() == MedalRoundMode.SCORE_BASED) {
                scoresheetService.ensureScoresheetForRound(entryId, roundId);
            }
        }
        log.info("Assigned entry {} to round {}", entryId, roundId);
    }

    @Override
    public void unassignEntryFromRound(UUID roundId, UUID entryId, UUID adminUserId) {
        var round = requireTable(roundId);
        var judging = requireJudging(round.getJudgingId());
        requireAuthorizedForJudging(judging, adminUserId);
        requireNotFrozen(judging.getDivisionId());
        if (round.getStatus() == JudgingRoundStatus.COMPLETE) {
            throw new BusinessRuleException("error.entry.cannot-change-on-complete-round");
        }
        // Force-all invariant: SCORE_BASED medal rounds mirror "all RECEIVED
        // entries in the category". A RECEIVED entry can't be manually
        // unassigned (admin must withdraw the entry or change its final
        // category instead). Non-RECEIVED entries (withdrawn, reverted, etc.)
        // are zombies — allowing admin to clean them off the round is the
        // escape hatch that keeps the round consistent with reality.
        if (round.getType() == RoundType.MEDAL
                && round.getMedalMode() == MedalRoundMode.SCORE_BASED) {
            var entryStatus = entryService.findById(entryId)
                    .map(Entry::getStatus)
                    .orElse(null);
            if (entryStatus == EntryStatus.RECEIVED) {
                throw new BusinessRuleException("error.entry.cannot-unassign-from-score-based");
            }
        }
        // Mid-round removal: handle the scoresheet attached to this entry. Block
        // when SUBMITTED (commits would be lost). Delete when still BLANK/DRAFT.
        // Applies to SCORING rounds and to SCORE_BASED medal rounds running
        // without a preceding scoring round (small-category flow — the medal
        // round owns the scoresheet directly). COMPARATIVE medal rounds own no
        // sheets so nothing to clean up here.
        boolean roundOwnsScoresheets = round.getType() == RoundType.SCORING
                || round.getMedalMode() == MedalRoundMode.SCORE_BASED;
        if (round.getStatus() == JudgingRoundStatus.ACTIVE && roundOwnsScoresheets) {
            for (var sheet : scoresheetService.findByEntryIdOrderBySubmittedAtAsc(entryId)) {
                if (!sheet.getRoundId().equals(roundId)) {
                    continue;
                }
                if (sheet.getStatus() == ScoresheetStatus.SUBMITTED) {
                    throw new BusinessRuleException("error.entry.cannot-unassign-submitted");
                }
                scoresheetService.deleteScoresheet(sheet.getId(), adminUserId);
            }
        }
        round.unassignEntry(entryId);
        recomputeScoringRoundReadiness(round);
        judgingRoundRepository.save(round);
        log.info("Unassigned entry {} from round {}", entryId, roundId);
    }

    @Override
    public void syncScoreBasedMedalRoundEntries(UUID roundId, UUID adminUserId) {
        var round = requireTable(roundId);
        var judging = requireJudging(round.getJudgingId());
        requireAuthorizedForJudging(judging, adminUserId);
        requireNotFrozen(judging.getDivisionId());
        if (round.getType() != RoundType.MEDAL
                || round.getMedalMode() != MedalRoundMode.SCORE_BASED) {
            throw new BusinessRuleException("error.medal-round.sync-score-based-only");
        }
        var receivedEntryIds = entryService
                .findEntriesByFinalCategoryId(round.getDivisionCategoryId()).stream()
                .filter(e -> e.getStatus() == EntryStatus.RECEIVED)
                .map(Entry::getId)
                .collect(java.util.stream.Collectors.toSet());
        int added = 0;
        for (var entryId : receivedEntryIds) {
            if (!round.getEntries().contains(entryId)) {
                assignEntryToRound(roundId, entryId, adminUserId);
                added++;
            }
        }
        // Zombie cleanup: anything on the round that's no longer eligible
        // (withdrawn, reverted, moved category) is removed. The standard
        // unassign path is used — its refined SCORE_BASED check permits
        // removal of non-RECEIVED entries; SUBMITTED-sheet block still
        // applies (committed work isn't silently dropped), surfaced as a
        // warning so the rest of the cleanup proceeds.
        int removed = 0;
        var zombieIds = new java.util.ArrayList<>(round.getEntries());
        zombieIds.removeAll(receivedEntryIds);
        for (var entryId : zombieIds) {
            try {
                unassignEntryFromRound(roundId, entryId, adminUserId);
                removed++;
            } catch (BusinessRuleException ex) {
                log.warn("Sync skipped removing entry {} from medal round {}: {}",
                        entryId, roundId, ex.getMessageKey());
            }
        }
        log.info("Synced SCORE_BASED medal round {}: +{} added, -{} removed (total now {})",
                roundId, added, removed, round.getEntries().size());
    }

    @Override
    public JudgingRound createMedalRound(UUID judgingId,
                                          UUID divisionCategoryId,
                                          UUID adminUserId) {
        var judging = requireJudging(judgingId);
        requireAuthorizedForJudging(judging, adminUserId);
        requireNotFrozen(judging.getDivisionId());
        // One medal round per category (redesign decision #5).
        boolean alreadyExists = judgingRoundRepository.findByJudgingId(judgingId).stream()
                .anyMatch(r -> r.getType() == RoundType.MEDAL
                        && divisionCategoryId.equals(r.getDivisionCategoryId()));
        if (alreadyExists) {
            throw new BusinessRuleException("error.medal-round.already-exists");
        }
        // Auto-create the CategoryJudgingConfig with default mode (COMPARATIVE)
        // when it's missing — covers the small-category flow where the admin
        // creates the medal round before any scoring round runs in the category.
        // Admin can switch mode to SCORE_BASED via MedalRoundView's header.
        // Mirrors the same auto-create in startRound's SCORING branch.
        var config = categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId)
                .orElseGet(() -> categoryConfigRepository.save(
                        new CategoryJudgingConfig(divisionCategoryId)));
        var category = competitionService.findDivisionCategoryById(divisionCategoryId);
        var round = new JudgingRound(judgingId, "Medal — " + category.getCode(),
                divisionCategoryId, null);
        round.convertToMedalRound(config.getMedalRoundMode());
        var saved = judgingRoundRepository.save(round);
        log.info("Created medal JudgingRound {} (category={}, mode={})",
                saved.getId(), divisionCategoryId, config.getMedalRoundMode());
        return saved;
    }

    @Override
    public void updateMedalRoundMode(UUID roundId, MedalRoundMode mode, UUID adminUserId) {
        var round = requireTable(roundId);
        var judging = requireJudging(round.getJudgingId());
        requireAuthorizedForJudging(judging, adminUserId);
        requireNotFrozen(judging.getDivisionId());
        if (round.getType() != RoundType.MEDAL) {
            throw new BusinessRuleException("error.medal-round.mode-not-applicable");
        }
        if (round.getStatus() != JudgingRoundStatus.PENDING
                && round.getStatus() != JudgingRoundStatus.READY) {
            throw new BusinessRuleException("error.medal-round.mode-locked-after-start");
        }
        round.updateMedalMode(mode);
        judgingRoundRepository.save(round);
        log.info("Updated medal round {} mode → {}", roundId, mode);
    }

    @Override
    public void updateRoundName(UUID roundId, String name,
                                UUID adminUserId) {
        var table = requireTable(roundId);
        var judging = requireJudging(table.getJudgingId());
        requireAuthorizedForJudging(judging, adminUserId);
        requireNotFrozen(judging.getDivisionId());
        var trimmedName = name == null ? "" : name.trim();
        boolean nameTaken = judgingRoundRepository.findByJudgingId(table.getJudgingId()).stream()
                .filter(r -> !r.getId().equals(roundId))
                .anyMatch(r -> trimmedName.equalsIgnoreCase(r.getName()));
        if (nameTaken) {
            throw new BusinessRuleException("error.round.name-duplicate", trimmedName);
        }
        table.updateName(trimmedName);
        judgingRoundRepository.save(table);
        log.debug("Updated table name {} -> '{}'", roundId, trimmedName);
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
    public Optional<JudgingRound> findActiveRoundForJudge(UUID judgeUserId) {
        var active = judgingRoundRepository.findByJudgeUserId(judgeUserId).stream()
                .filter(r -> r.getStatus() == JudgingRoundStatus.ACTIVE)
                .toList();
        if (active.size() > 1) {
            log.warn("Judge {} has {} ACTIVE rounds (expected at most 1): {}",
                    judgeUserId, active.size(),
                    active.stream().map(JudgingRound::getName).toList());
        }
        return active.stream().findFirst();
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
        // Hard-COI guard: a judge who owns any entry in this round's category
        // cannot be assigned. The UI dialog prevents the selection, but this
        // service-level check is the source of truth.
        var conflictingEntry = entryService.findEntriesByFinalCategoryId(table.getDivisionCategoryId()).stream()
                .filter(e -> coiCheckService.check(judgeUserId, e.getId()).hardBlock())
                .findFirst();
        if (conflictingEntry.isPresent()) {
            throw new BusinessRuleException("error.coi.assign-hard-block",
                    judgeNameForError(judgeUserId),
                    String.valueOf(conflictingEntry.get().getEntryNumber()));
        }
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
                        judgeNameForError(judgeUserId));
            }
        }
        table.assignJudge(judgeUserId);
        recomputeScoringRoundReadiness(table);
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
        // Medal rounds skip the min-judges check — they often run with a
        // different (often smaller) panel than scoring rounds, and the
        // scoring-round minimum doesn't apply.
        if (table.getStatus() == JudgingRoundStatus.ACTIVE
                && table.getType() != RoundType.MEDAL) {
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
        recomputeScoringRoundReadiness(table);
        judgingRoundRepository.save(table);
        log.info("Removed judge {} from table {}", judgeUserId, roundId);
    }

    // === Table state transitions ===

    @Override
    public void revertScoringRound(UUID roundId, UUID adminUserId) {
        var round = requireTable(roundId);
        var judging = requireJudging(round.getJudgingId());
        requireAuthorizedForJudging(judging, adminUserId);
        requireNotFrozen(judging.getDivisionId());
        if (round.getType() != RoundType.SCORING) {
            throw new BusinessRuleException("error.round.revert-scoring-only");
        }
        if (round.getStatus() != JudgingRoundStatus.ACTIVE) {
            throw new BusinessRuleException("error.round.revert-only-active");
        }
        // Any judge engagement (DRAFT or SUBMITTED — anything beyond BLANK)
        // blocks revert: DRAFT content is real work that an accidental revert
        // would destroy. To clear, admins delete individual scoresheets via the
        // per-row 🗑 action until every remaining sheet is BLANK or gone.
        long touched = scoresheetService.countByRoundIdAndStatusNot(roundId, ScoresheetStatus.BLANK);
        if (touched > 0) {
            throw new BusinessRuleException("error.round.cannot-revert-touched-scoresheets",
                    String.valueOf(touched));
        }
        scoresheetService.deleteAllForRound(roundId);
        round.revertToReady();
        judgingRoundRepository.save(round);
        log.info("Reverted scoring round {} ACTIVE → READY (admin={})", roundId, adminUserId);
    }

    @Override
    public void startRound(UUID roundId, UUID adminUserId) {
        var table = requireTable(roundId);
        var judging = requireJudging(table.getJudgingId());
        requireAuthorizedForJudging(judging, adminUserId);
        var division = competitionService.findDivisionById(judging.getDivisionId());
        if (division.getStatus().isResultsFrozen()) {
            throw new BusinessRuleException("error.judging.results-published-frozen");
        }
        if (division.getStatus().ordinal() < DivisionStatus.JUDGING.ordinal()) {
            throw new BusinessRuleException("error.round.cannot-start-before-judging");
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
        // Cross-division shared-tables busy-check: when the competition has
        // sharedTables=true, the same physical "Table 1" can't be in use by
        // an active round in another division of the same competition.
        // Matching is by label (label equality stands in for "same physical
        // workspace" across the competition's per-division table records).
        var competition = competitionService.findCompetitionById(division.getCompetitionId());
        if (competition.isSharedTables()) {
            var thisLabel = physicalTableRepository.findById(table.getPhysicalTableId())
                    .map(PhysicalTable::getLabel)
                    .orElse(null);
            if (thisLabel != null) {
                boolean crossDivisionBusy = competitionService.findDivisionsByCompetition(competition.getId()).stream()
                        .filter(d -> !d.getId().equals(division.getId()))
                        .map(d -> judgingRepository.findByDivisionId(d.getId()))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .flatMap(j -> judgingRoundRepository.findByJudgingId(j.getId()).stream())
                        .filter(r -> r.getStatus() == JudgingRoundStatus.ACTIVE)
                        .filter(r -> r.getPhysicalTableId() != null)
                        .anyMatch(r -> physicalTableRepository.findById(r.getPhysicalTableId())
                                .map(pt -> thisLabel.equals(pt.getLabel()))
                                .orElse(false));
                if (crossDivisionBusy) {
                    throw new BusinessRuleException("error.round.physical-table-busy-shared", thisLabel);
                }
            }
        }
        // Scoring rounds need a full judging panel; medal rounds may use fewer.
        if (table.getType() == RoundType.SCORING
                && table.getAssignments().size() < division.getMinJudgesPerRound()) {
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
                        judgeNameForError(assignment.getJudgeUserId()));
            }
        }
        // Scoring rounds and SCORE_BASED medal rounds (small-category flow) must
        // have an explicit entry assignment before starting — the Assign Entries
        // dialog is the canonical way to set this. (COMPARATIVE medal rounds
        // source their entries from prelim scoring rounds, not round.entries.)
        boolean medalRoundOwnsSheets = table.getType() == RoundType.MEDAL
                && table.getMedalMode() == MedalRoundMode.SCORE_BASED;
        if ((table.getType() == RoundType.SCORING || medalRoundOwnsSheets) && table.getEntries().isEmpty()) {
            throw new BusinessRuleException("error.round.no-entries-assigned");
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
        if (table.getType() == RoundType.SCORING) {
            // Ensure CategoryJudgingConfig exists (default COMPARATIVE) for the table's category
            categoryConfigRepository.findByDivisionCategoryId(table.getDivisionCategoryId())
                    .orElseGet(() -> categoryConfigRepository.save(
                            new CategoryJudgingConfig(table.getDivisionCategoryId())));
            scoresheetService.createScoresheetsForTable(roundId);
            eventPublisher.publishEvent(new RoundStartedEvent(
                    table.getId(), table.getDivisionCategoryId(),
                    judging.getDivisionId(), Instant.now()));
        } else {
            // Medal round just transitioned to ACTIVE. For SCORE_BASED mode,
            // create BLANK scoresheets for any directly-assigned entries
            // (small-category flow with no preceding scoring round — the medal
            // round owns the sheets); createScoresheetsForTable is a no-op for
            // entries that already have a sheet (cascade-populated entries
            // arrive with prelim SUBMITTED sheets). Then pre-populate the top-3
            // medals (confirmed=false) from whatever's already SUBMITTED;
            // for the no-prelim path that's nothing, and the @EventListener on
            // ScoresheetSubmittedEvent will re-run autoPopulate once all sheets
            // on this round are SUBMITTED.
            if (table.getMedalMode() == MedalRoundMode.SCORE_BASED) {
                scoresheetService.createScoresheetsForTable(roundId);
                autoPopulateMedalsByScore(table.getDivisionCategoryId(),
                        judging.getDivisionId(), adminUserId);
            }
            eventPublisher.publishEvent(new MedalRoundActivatedEvent(
                    table.getDivisionCategoryId(), judging.getDivisionId(),
                    table.getMedalMode(), Instant.now()));
        }
        log.info("Started {} round {} in division {}",
                table.getType(), roundId, judging.getDivisionId());
    }

    /**
     * SCORE_BASED auto-fill (§2.D D10): walk gold→silver→bronze; stop the
     * cascade on the first tie within a slot. Auto-filled MedalAwards are
     * written with {@code confirmed = false} so they don't propagate to
     * results or BOS until the admin reviews them. Manual {@code recordMedal}
     * or {@code updateMedal} flip {@code confirmed = true}.
     */
    private void autoPopulateMedalsByScore(UUID divisionCategoryId, UUID divisionId,
                                            UUID adminUserId) {
        var sheetsByEntry = new HashMap<UUID, Integer>();
        var roundsInCategory = judgingRoundRepository.findByJudgingId(
                judgingRepository.findByDivisionId(divisionId)
                        .orElseThrow().getId()).stream()
                .filter(t -> t.getDivisionCategoryId().equals(divisionCategoryId))
                .toList();
        // Source sheets from SCORING rounds AND from the SCORE_BASED medal
        // round itself (small-category flow). COMPARATIVE medal rounds own no
        // sheets — they only consume them — so they're not a source here.
        var sourceRounds = roundsInCategory.stream()
                .filter(t -> t.getType() == RoundType.SCORING
                        || (t.getType() == RoundType.MEDAL
                                && t.getMedalMode() == MedalRoundMode.SCORE_BASED))
                .toList();
        var allSheets = new ArrayList<Scoresheet>();
        for (var t : sourceRounds) {
            allSheets.addAll(scoresheetRepository.findByRoundId(t.getId()));
        }
        for (var sheet : allSheets) {
            if (sheet.getStatus() != ScoresheetStatus.SUBMITTED || sheet.getTotalScore() == null) {
                continue;
            }
            // The "advanced to medal round" flag only gates prelim SCORING-round
            // sheets — it's how judges signal which entries deserve a medal
            // round at all. Medal-round-owned sheets ARE the medal round, so
            // every SUBMITTED sheet there is automatically a medal candidate.
            var sourceRound = sourceRounds.stream()
                    .filter(r -> r.getId().equals(sheet.getRoundId()))
                    .findFirst().orElse(null);
            if (sourceRound != null
                    && sourceRound.getType() == RoundType.SCORING
                    && !sheet.isAdvancedToMedalRound()) {
                continue;
            }
            sheetsByEntry.merge(sheet.getEntryId(), sheet.getTotalScore(), Integer::max);
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
            long tieCount = ranked.subList(rankIdx, ranked.size()).stream()
                    .takeWhile(e -> e.getValue() == slotScore)
                    .count();
            if (tieCount > 1) {
                break;
            }
            if (medalAwardRepository.findByEntryId(slot.getKey()).isEmpty()) {
                medalAwardRepository.save(new MedalAward(
                        slot.getKey(), divisionId, divisionCategoryId, medal, adminUserId));
            }
            rankIdx++;
        }
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
            config.updateMode(mode);
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
        // Source of truth is the medal round's explicit entries set
        // (populated at cascade time, editable via Assign Entries dialog).
        // Fallback to legacy derivation when no medal round exists yet OR
        // when its entries set is still empty — keeps older callers and
        // unit tests that skip the cascade working.
        var medalRound = judgingRoundRepository
                .findFirstByDivisionCategoryIdAndType(divisionCategoryId, RoundType.MEDAL);
        if (medalRound.isPresent() && !medalRound.get().getEntries().isEmpty()) {
            return findMedalRoundRowsFromExplicitEntries(medalRound.get(), mode);
        }
        return findMedalRoundRowsByDerivation(divisionCategoryId, mode);
    }

    private List<MedalRoundEntryRow> findMedalRoundRowsFromExplicitEntries(JudgingRound medalRound,
                                                                            MedalRoundMode mode) {
        var rows = new ArrayList<MedalRoundEntryRow>();
        for (var entryId : medalRound.getEntries()) {
            var entry = entryService.findEntryById(entryId);
            // Withdrawn entries drop out of the medal round even if previously assigned.
            if (entry.getStatus() != EntryStatus.RECEIVED) {
                continue;
            }
            var sheetOpt = scoresheetRepository.findByEntryId(entryId);
            // COMPARATIVE selects from advance-flagged prelim sheets, so it requires
            // a SUBMITTED sheet. SCORE_BASED (small-category flow) lets the medal
            // round own its sheets — surface assigned entries even when the sheet
            // is still BLANK/DRAFT or absent so admin sees what's coming.
            if (mode == MedalRoundMode.COMPARATIVE
                    && (sheetOpt.isEmpty() || sheetOpt.get().getStatus() != ScoresheetStatus.SUBMITTED)) {
                continue;
            }
            Integer totalScore = null;
            boolean advanced = false;
            if (sheetOpt.isPresent() && sheetOpt.get().getStatus() == ScoresheetStatus.SUBMITTED) {
                totalScore = sheetOpt.get().getTotalScore();
                advanced = sheetOpt.get().isAdvancedToMedalRound();
            }
            var medalOpt = medalAwardRepository.findByEntryId(entryId);
            rows.add(new MedalRoundEntryRow(
                    entry.getId(), entry.getEntryNumber(),
                    entry.getEntryCode(), entry.getMeadName(),
                    entry.getUserId(), totalScore, advanced,
                    sheetOpt.map(Scoresheet::getId).orElse(null),
                    medalOpt.map(MedalAward::getId).orElse(null),
                    medalOpt.map(MedalAward::getMedal).orElse(null)));
        }
        // Default to entry-code order so the row layout stays stable across
        // reloads — admins were confused by the automatic re-sort-by-score
        // every time they came back from submitting a sheet. The grid column
        // is now sortable, so the admin can pick a different sort manually.
        rows.sort(Comparator.comparing(MedalRoundEntryRow::entryCode));
        return rows;
    }

    private List<MedalRoundEntryRow> findMedalRoundRowsByDerivation(UUID divisionCategoryId,
                                                                     MedalRoundMode mode) {
        var rows = new ArrayList<MedalRoundEntryRow>();
        for (var entry : entryService.findEntriesByFinalCategoryId(divisionCategoryId)) {
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
                    entry.getId(), entry.getEntryNumber(),
                    entry.getEntryCode(), entry.getMeadName(),
                    entry.getUserId(), sheet.getTotalScore(), sheet.isAdvancedToMedalRound(),
                    sheet.getId(),
                    medalOpt.map(MedalAward::getId).orElse(null),
                    medalOpt.map(MedalAward::getMedal).orElse(null)));
        }
        rows.sort(Comparator.comparing(MedalRoundEntryRow::entryCode));
        return rows;
    }

    @Override
    public MedalRoundScorePreview recomputeScorePreview(UUID divisionCategoryId) {
        var rows = findMedalRoundEntries(divisionCategoryId, MedalRoundMode.SCORE_BASED);
        long medaled = rows.stream().filter(r -> r.currentMedal() != null).count();
        int openSlots = Math.max(0, Medal.values().length - (int) medaled);
        // findMedalRoundEntries returns entry-code order (stable for the UI);
        // the tie-detection logic needs the top scorer at index 0, so sort
        // unresolved by descending score here. Null totals sort last.
        var unresolved = rows.stream()
                .filter(r -> r.medalAwardId() == null)
                .sorted(Comparator.comparing(MedalRoundEntryRow::round1Total,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (openSlots == 0 || unresolved.isEmpty()) {
            return new MedalRoundScorePreview(0, Set.of());
        }
        var topScore = unresolved.get(0).round1Total();
        if (topScore == null) {
            // No SUBMITTED sheets yet — too early to assess ties. Most callers
            // hit this in the small-category SCORE_BASED flow before judges
            // start filling sheets; Objects.equals(null, null) would otherwise
            // flag every entry as tied at a phantom "null" score.
            return new MedalRoundScorePreview(0, Set.of());
        }
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
                .filter(catId -> effectiveMedalRoundStatusInternal(catId)
                        .map(s -> s == JudgingRoundStatus.ACTIVE)
                        .orElse(false))
                .flatMap(catId -> categoryConfigRepository.findByDivisionCategoryId(catId).stream())
                .toList();
    }

    // === Medal round transitions (operate on the medal JudgingRound) ===

    @Override
    public void completeMedalRoundById(UUID roundId, UUID adminUserId) {
        var round = requireMedalRound(roundId);
        var judging = requireJudging(round.getJudgingId());
        requireAuthorizedForDivision(judging.getDivisionId(), adminUserId);
        requireNotFrozen(judging.getDivisionId());
        try {
            round.markComplete();
        } catch (IllegalStateException e) {
            throw new BusinessRuleException("error.medal-round.cannot-complete", e.getMessage());
        }
        judgingRoundRepository.save(round);
        eventPublisher.publishEvent(new MedalRoundCompletedEvent(
                round.getDivisionCategoryId(), judging.getDivisionId(), Instant.now()));
        log.info("Completed medal round {} (category {})", roundId, round.getDivisionCategoryId());
    }

    @Override
    public void reopenMedalRoundById(UUID roundId, UUID adminUserId) {
        var round = requireMedalRound(roundId);
        var judging = requireJudging(round.getJudgingId());
        requireAuthorizedForDivision(judging.getDivisionId(), adminUserId);
        requireNotFrozen(judging.getDivisionId());
        if (judging.getPhase() != JudgingPhase.ACTIVE) {
            throw new BusinessRuleException("error.medal-round.judging-not-active");
        }
        try {
            round.reopen();
        } catch (IllegalStateException e) {
            throw new BusinessRuleException("error.medal-round.cannot-reopen", e.getMessage());
        }
        judgingRoundRepository.save(round);
        eventPublisher.publishEvent(new MedalRoundReopenedEvent(
                round.getDivisionCategoryId(), judging.getDivisionId(), Instant.now()));
        log.info("Reopened medal round {} (category {})", roundId, round.getDivisionCategoryId());
    }

    @Override
    public void resetMedalRoundById(UUID roundId, UUID adminUserId) {
        var round = requireMedalRound(roundId);
        var judging = requireJudging(round.getJudgingId());
        requireAuthorizedForDivision(judging.getDivisionId(), adminUserId);
        requireNotFrozen(judging.getDivisionId());
        if (judging.getPhase() != JudgingPhase.ACTIVE) {
            throw new BusinessRuleException("error.medal-round.judging-not-active");
        }
        var awards = medalAwardRepository.findByFinalCategoryId(round.getDivisionCategoryId());
        int wiped = awards.size();
        medalAwardRepository.deleteAll(awards);
        try {
            round.resetToReady();
        } catch (IllegalStateException e) {
            throw new BusinessRuleException("error.medal-round.cannot-reset", e.getMessage());
        }
        judgingRoundRepository.save(round);
        eventPublisher.publishEvent(new MedalRoundResetEvent(
                round.getDivisionCategoryId(), judging.getDivisionId(), wiped, Instant.now()));
        log.info("Reset medal round {} (category {}, wiped {} awards)",
                roundId, round.getDivisionCategoryId(), wiped);
    }

    private JudgingRound requireMedalRound(UUID roundId) {
        var round = judgingRoundRepository.findById(roundId)
                .orElseThrow(() -> new BusinessRuleException("error.judging-table.not-found"));
        if (round.getType() != RoundType.MEDAL) {
            throw new BusinessRuleException("error.medal-round.not-a-medal-round");
        }
        return round;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JudgingRound> findMedalRoundByCategoryId(UUID divisionCategoryId) {
        return judgingRoundRepository
                .findFirstByDivisionCategoryIdAndType(divisionCategoryId, RoundType.MEDAL);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JudgingRoundStatus> getEffectiveMedalRoundStatus(UUID divisionCategoryId) {
        return effectiveMedalRoundStatusInternal(divisionCategoryId);
    }

    /** Throws when the category's medal round is not ACTIVE. */
    private void requireMedalRoundActive(UUID divisionCategoryId) {
        var status = effectiveMedalRoundStatusInternal(divisionCategoryId);
        if (status.isEmpty()) {
            throw new BusinessRuleException("error.medal-round.not-found");
        }
        if (status.get() != JudgingRoundStatus.ACTIVE) {
            throw new BusinessRuleException("error.medal-round.not-active");
        }
    }

    /**
     * Enforces "at most one G / S / B per category" on manual medal assignment.
     * Auto-populate respects this naturally (it walks the medal list once and
     * stops on ties), but {@code recordMedal} and {@code updateMedal} were
     * happily letting admins stack three Golds in a row. {@code medal == null}
     * (explicit withhold) is exempt — withholds aren't medals.
     */
    private void requireUniqueMedalTypeInCategory(UUID finalCategoryId, UUID entryId, Medal medal) {
        if (medal == null) {
            return;
        }
        boolean duplicate = medalAwardRepository.findByFinalCategoryId(finalCategoryId).stream()
                .filter(a -> !a.getEntryId().equals(entryId))
                .anyMatch(a -> a.getMedal() == medal);
        if (duplicate) {
            throw new BusinessRuleException("error.medal.duplicate-type", medal.name());
        }
    }

    /** Internal call site for in-class use (bypasses Spring proxy / @Transactional). */
    private Optional<JudgingRoundStatus> effectiveMedalRoundStatusInternal(UUID divisionCategoryId) {
        return judgingRoundRepository
                .findFirstByDivisionCategoryIdAndType(divisionCategoryId, RoundType.MEDAL)
                .map(JudgingRound::getStatus);
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
        requireMedalRoundActive(finalCategoryId);
        requireAuthorizedForMedalAction(entry.getDivisionId(), finalCategoryId, judgeUserId);
        requireUniqueMedalTypeInCategory(finalCategoryId, entryId, medal);
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
        requireMedalRoundActive(award.getFinalCategoryId());
        requireAuthorizedForMedalAction(award.getDivisionId(), award.getFinalCategoryId(), judgeUserId);
        requireUniqueMedalTypeInCategory(award.getFinalCategoryId(), award.getEntryId(), newValue);
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
        requireMedalRoundActive(award.getFinalCategoryId());
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
        // Guard: every configured category's medal round must be COMPLETE
        var divCategories = competitionService.findDivisionCategories(divisionId);
        for (var cat : divCategories) {
            var status = effectiveMedalRoundStatusInternal(cat.getId());
            if (status.isPresent() && status.get() != JudgingRoundStatus.COMPLETE) {
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
        if (!medal.get().isConfirmed()) {
            throw new BusinessRuleException("error.bos.gold-not-confirmed");
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

    // --- helpers ---

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
