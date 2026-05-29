package app.meads.judging.internal;

import app.meads.BusinessRuleException;
import app.meads.competition.CompetitionService;
import app.meads.entry.EntryService;
import app.meads.entry.EntryStatus;
import app.meads.judging.CategoryJudgingConfig;
import app.meads.judging.CoiCheckService;
import app.meads.judging.Judging;
import app.meads.judging.JudgeProfileService;
import app.meads.judging.JudgingRound;
import app.meads.judging.JudgingRoundStatus;
import app.meads.judging.MedalRoundMode;
import app.meads.judging.RoundType;
import app.meads.judging.Scoresheet;
import app.meads.judging.ScoresheetRevertedEvent;
import app.meads.judging.ScoresheetService;
import app.meads.judging.ScoresheetStatus;
import app.meads.judging.ScoresheetSubmittedEvent;
import app.meads.judging.RoundCompletedEvent;
import app.meads.judging.RoundReopenedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@Validated
public class ScoresheetServiceImpl implements ScoresheetService {

    private final ScoresheetRepository scoresheetRepository;
    private final JudgingRoundRepository judgingRoundRepository;
    private final CategoryJudgingConfigRepository categoryConfigRepository;
    private final JudgingRepository judgingRepository;
    private final EntryService entryService;
    private final CompetitionService competitionService;
    private final JudgeProfileService judgeProfileService;
    private final CoiCheckService coiCheckService;
    private final ApplicationEventPublisher eventPublisher;

    ScoresheetServiceImpl(ScoresheetRepository scoresheetRepository,
                          JudgingRoundRepository judgingRoundRepository,
                          CategoryJudgingConfigRepository categoryConfigRepository,
                          JudgingRepository judgingRepository,
                          EntryService entryService,
                          CompetitionService competitionService,
                          JudgeProfileService judgeProfileService,
                          CoiCheckService coiCheckService,
                          ApplicationEventPublisher eventPublisher) {
        this.scoresheetRepository = scoresheetRepository;
        this.judgingRoundRepository = judgingRoundRepository;
        this.categoryConfigRepository = categoryConfigRepository;
        this.judgingRepository = judgingRepository;
        this.entryService = entryService;
        this.competitionService = competitionService;
        this.judgeProfileService = judgeProfileService;
        this.coiCheckService = coiCheckService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void createScoresheetsForTable(UUID roundId) {
        var table = requireTable(roundId);
        var judging = requireJudging(table.getJudgingId());
        requireNotFrozen(judging.getDivisionId());
        var entries = table.getEntries().isEmpty()
                ? entryService.findEntriesByFinalCategoryId(table.getDivisionCategoryId())
                : table.getEntries().stream().map(entryService::findEntryById).toList();
        for (var entry : entries) {
            // Only physically-present entries are judged — a bottle that never
            // arrived (SUBMITTED) or was pulled (WITHDRAWN) gets no scoresheet.
            if (entry.getStatus() != EntryStatus.RECEIVED) {
                continue;
            }
            if (scoresheetRepository.findByEntryId(entry.getId()).isEmpty()) {
                scoresheetRepository.save(new Scoresheet(roundId, entry.getId()));
                log.info("Created DRAFT scoresheet for entry {} at table {}", entry.getId(), roundId);
            }
        }
    }

    @Override
    public void ensureScoresheetForEntry(UUID entryId) {
        if (scoresheetRepository.findByEntryId(entryId).isPresent()) {
            return;
        }
        var entry = entryService.findEntryById(entryId);
        if (entry.getFinalCategoryId() == null) {
            return;
        }
        requireNotFrozen(entry.getDivisionId());
        var judging = judgingRepository.findByDivisionId(entry.getDivisionId()).orElse(null);
        if (judging == null) {
            return;
        }
        var matchingTable = judgingRoundRepository.findByJudgingId(judging.getId()).stream()
                .filter(t -> t.getDivisionCategoryId().equals(entry.getFinalCategoryId()))
                .filter(t -> t.getStatus() == JudgingRoundStatus.ACTIVE)
                .findFirst()
                .orElse(null);
        if (matchingTable == null) {
            return;
        }
        scoresheetRepository.save(new Scoresheet(matchingTable.getId(), entryId));
        log.info("Sync rule: created DRAFT scoresheet for entry {} at table {}",
                entryId, matchingTable.getId());
    }

    @Override
    public void ensureScoresheetForRound(UUID entryId, UUID roundId) {
        if (scoresheetRepository.findByEntryId(entryId).isPresent()) {
            return;
        }
        var entry = entryService.findEntryById(entryId);
        if (entry.getStatus() != EntryStatus.RECEIVED) {
            return;
        }
        requireNotFrozen(entry.getDivisionId());
        scoresheetRepository.save(new Scoresheet(roundId, entryId));
        log.info("Created BLANK scoresheet for entry {} at medal round {}", entryId, roundId);
    }

    @Override
    public void updateScore(UUID scoresheetId, String fieldName,
                            Integer value, String comment, UUID judgeUserId) {
        var sheet = requireScoresheet(scoresheetId);
        requireNotFrozenForSheet(sheet);
        enforceCoi(judgeUserId, sheet);
        try {
            sheet.updateScore(fieldName, value, comment);
        } catch (IllegalStateException e) {
            throw new BusinessRuleException("error.scoresheet.not-draft");
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("error.scoresheet.invalid-value", e.getMessage());
        }
        if (sheet.getFilledByJudgeUserId() == null) {
            sheet.setFilledBy(judgeUserId);
        }
        scoresheetRepository.save(sheet);
    }

    @Override
    public void updateOverallComments(UUID scoresheetId, String comments,
                                       UUID judgeUserId) {
        var sheet = requireScoresheet(scoresheetId);
        requireNotFrozenForSheet(sheet);
        enforceCoi(judgeUserId, sheet);
        try {
            sheet.updateOverallComments(comments);
        } catch (IllegalStateException e) {
            throw new BusinessRuleException("error.scoresheet.not-draft");
        }
        if (sheet.getFilledByJudgeUserId() == null) {
            sheet.setFilledBy(judgeUserId);
        }
        scoresheetRepository.save(sheet);
    }

    @Override
    public void setAdvancedToMedalRound(UUID scoresheetId, boolean advanced,
                                         UUID judgeUserId) {
        var sheet = requireScoresheet(scoresheetId);
        requireNotFrozenForSheet(sheet);
        enforceCoi(judgeUserId, sheet);
        var table = requireTable(sheet.getRoundId());
        // No-op for sheets owned by a MEDAL round (small-category SCORE_BASED
        // flow): the "advance to medal round" flag exists to gate prelim
        // SCORING sheets, but a medal-round-owned sheet is already at the
        // medal round — the flag is meaningless and the existing "medal round
        // active" guard would block every judge save.
        if (table.getType() == RoundType.MEDAL) {
            return;
        }
        if (effectiveMedalRoundStatus(table.getDivisionCategoryId()) == JudgingRoundStatus.ACTIVE) {
            throw new BusinessRuleException("error.scoresheet.medal-round-active");
        }
        sheet.setAdvancedToMedalRound(advanced);
        scoresheetRepository.save(sheet);
    }

    @Override
    public void setCommentLanguage(UUID scoresheetId, String languageCode,
                                    UUID judgeUserId) {
        var sheet = requireScoresheet(scoresheetId);
        var table = requireTable(sheet.getRoundId());
        var judging = requireJudging(table.getJudgingId());
        var division = competitionService.findDivisionById(judging.getDivisionId());
        if (division.getStatus().isResultsFrozen()) {
            throw new BusinessRuleException("error.judging.results-published-frozen");
        }
        enforceCoi(judgeUserId, sheet);
        if (!isValidIsoLanguageCode(languageCode)) {
            throw new BusinessRuleException("error.scoresheet.language-not-allowed", languageCode);
        }
        try {
            sheet.setCommentLanguage(languageCode);
        } catch (IllegalStateException e) {
            throw new BusinessRuleException("error.scoresheet.not-draft");
        }
        scoresheetRepository.save(sheet);
        judgeProfileService.updatePreferredCommentLanguage(judgeUserId, languageCode);
    }

    private static final java.util.Set<String> ISO_LANGUAGE_CODES =
            java.util.Set.of(java.util.Locale.getISOLanguages());

    private static boolean isValidIsoLanguageCode(String code) {
        return code != null && ISO_LANGUAGE_CODES.contains(code);
    }

    @Override
    public void markFilled(UUID scoresheetId, UUID judgeUserId) {
        var sheet = requireScoresheet(scoresheetId);
        requireNotFrozenForSheet(sheet);
        enforceCoi(judgeUserId, sheet);
        requireFieldCommentsLongEnough(sheet);
        if (sheet.getFilledByJudgeUserId() == null) {
            sheet.setFilledBy(judgeUserId);
        }
        try {
            sheet.markFilled();
        } catch (IllegalStateException e) {
            throw new BusinessRuleException("error.scoresheet.incomplete", e.getMessage());
        }
        scoresheetRepository.save(sheet);
        log.info("Marked scoresheet {} FILLED (judge {})", sheet.getId(), judgeUserId);
    }

    @Override
    public void submit(UUID scoresheetId, UUID judgeUserId) {
        var sheet = requireScoresheet(scoresheetId);
        requireNotFrozenForSheet(sheet);
        enforceCoi(judgeUserId, sheet);
        if (sheet.getCommentLanguage() == null) {
            var defaultLang = resolveDefaultCommentLanguage(judgeUserId, sheet);
            if (defaultLang != null) {
                sheet.setCommentLanguage(defaultLang);
            }
        }
        // Comment-length validation runs *before* the entity's field-filled
        // check so the error messages stay specific. Each per-criterion comment
        // must clear MIN_PER_FIELD_COMMENT_LENGTH; the additional ("overall")
        // comment is optional. Drafts can stay incomplete.
        requireFieldCommentsLongEnough(sheet);
        try {
            // Transitional bridge: submit() now requires FILLED. A judge who came
            // through the per-row submit shortcut (or a test) may still hand us a
            // DRAFT sheet — promote it first so submit() can run. The round-level
            // Finalize flow submits already-FILLED sheets directly.
            if (sheet.getStatus() == ScoresheetStatus.DRAFT) {
                sheet.markFilled();
            }
            sheet.submit();
        } catch (IllegalStateException e) {
            throw new BusinessRuleException("error.scoresheet.incomplete", e.getMessage());
        }
        scoresheetRepository.save(sheet);
        var table = requireTable(sheet.getRoundId());
        eventPublisher.publishEvent(new ScoresheetSubmittedEvent(
                sheet.getId(), sheet.getEntryId(), table.getId(),
                sheet.getTotalScore(), sheet.getSubmittedAt()));
        // Cascade SCORING round → category-medal-ready when all its sheets are
        // SUBMITTED. Restricted to SCORING rounds — MEDAL rounds owning their
        // own sheets (small-category SCORE_BASED flow) should stay ACTIVE
        // until the admin reviews medals and clicks Finalize, otherwise the
        // medal-button actions vanish before the admin has a chance to act.
        var tableSheets = scoresheetRepository.findByRoundId(table.getId());
        boolean allSubmitted = tableSheets.stream()
                .allMatch(s -> s.getStatus() == ScoresheetStatus.SUBMITTED);
        if (allSubmitted && table.getStatus() == JudgingRoundStatus.ACTIVE
                && table.getType() == RoundType.SCORING) {
            table.markComplete();
            judgingRoundRepository.save(table);
            var judging = requireJudging(table.getJudgingId());
            eventPublisher.publishEvent(new RoundCompletedEvent(
                    table.getId(), table.getDivisionCategoryId(),
                    judging.getDivisionId(), Instant.now()));
            cascadeMarkCategoryReadyIfAllTablesComplete(judging, table.getDivisionCategoryId());
        }
        log.info("Submitted scoresheet {} (total={})", sheet.getId(), sheet.getTotalScore());
    }

    @Override
    public void revertToDraft(UUID scoresheetId, UUID adminUserId) {
        var sheet = requireScoresheet(scoresheetId);
        var table = requireTable(sheet.getRoundId());
        var judging = requireJudging(table.getJudgingId());
        if (!competitionService.isAuthorizedForDivision(judging.getDivisionId(), adminUserId)) {
            throw new BusinessRuleException("error.auth.unauthorized");
        }
        requireNotFrozen(judging.getDivisionId());
        var status = effectiveMedalRoundStatus(table.getDivisionCategoryId());
        if (status != null && status != JudgingRoundStatus.PENDING
                && status != JudgingRoundStatus.READY) {
            throw new BusinessRuleException("error.scoresheet.cannot-revert-medal-active");
        }
        try {
            sheet.revertToDraft();
        } catch (IllegalStateException e) {
            throw new BusinessRuleException("error.scoresheet.not-submitted");
        }
        scoresheetRepository.save(sheet);
        eventPublisher.publishEvent(new ScoresheetRevertedEvent(
                sheet.getId(), sheet.getEntryId(), table.getId(), Instant.now()));
        if (table.getStatus() == JudgingRoundStatus.COMPLETE) {
            table.reopen();
            judgingRoundRepository.save(table);
            eventPublisher.publishEvent(new RoundReopenedEvent(
                    table.getId(), table.getDivisionCategoryId(),
                    judging.getDivisionId(), Instant.now()));
            retreatMedalRoundFromReady(table.getDivisionCategoryId());
        }
        log.info("Reverted scoresheet {} to DRAFT", sheet.getId());
    }

    @Override
    public void deleteScoresheet(UUID scoresheetId, UUID adminUserId) {
        var sheet = requireScoresheet(scoresheetId);
        var table = requireTable(sheet.getRoundId());
        var judging = requireJudging(table.getJudgingId());
        if (!competitionService.isAuthorizedForDivision(judging.getDivisionId(), adminUserId)) {
            throw new BusinessRuleException("error.auth.unauthorized");
        }
        requireNotFrozen(judging.getDivisionId());
        var status = effectiveMedalRoundStatus(table.getDivisionCategoryId());
        if (status != null && status != JudgingRoundStatus.PENDING
                && status != JudgingRoundStatus.READY) {
            throw new BusinessRuleException("error.scoresheet.cannot-delete-medal-active");
        }
        scoresheetRepository.delete(sheet);
        // If deleting the last scoresheet (or a SUBMITTED one) leaves the table COMPLETE
        // without any SUBMITTED siblings, reopen it to ROUND_1 so admins see a sane state.
        if (table.getStatus() == JudgingRoundStatus.COMPLETE) {
            table.reopen();
            judgingRoundRepository.save(table);
            eventPublisher.publishEvent(new RoundReopenedEvent(
                    table.getId(), table.getDivisionCategoryId(),
                    judging.getDivisionId(), Instant.now()));
            retreatMedalRoundFromReady(table.getDivisionCategoryId());
        }
        log.info("Deleted scoresheet {} (entry {}, table {})",
                sheet.getId(), sheet.getEntryId(), table.getId());
    }

    @Override
    public void moveToRound(UUID scoresheetId, UUID newRoundId,
                            UUID adminUserId) {
        var sheet = requireScoresheet(scoresheetId);
        var newTable = requireTable(newRoundId);
        var judging = requireJudging(newTable.getJudgingId());
        if (!competitionService.isAuthorizedForDivision(judging.getDivisionId(), adminUserId)) {
            throw new BusinessRuleException("error.auth.unauthorized");
        }
        requireNotFrozen(judging.getDivisionId());
        var entry = entryService.findEntryById(sheet.getEntryId());
        if (entry.getFinalCategoryId() == null
                || !entry.getFinalCategoryId().equals(newTable.getDivisionCategoryId())) {
            throw new BusinessRuleException("error.scoresheet.category-mismatch");
        }
        try {
            sheet.moveToRound(newRoundId);
        } catch (IllegalStateException e) {
            throw new BusinessRuleException("error.scoresheet.not-draft");
        }
        scoresheetRepository.save(sheet);
        log.info("Moved scoresheet {} to table {}", sheet.getId(), newRoundId);
    }

    // --- helpers ---

    private void cascadeMarkCategoryReadyIfAllTablesComplete(Judging judging,
                                                              UUID divisionCategoryId) {
        var roundsInCategory = judgingRoundRepository.findByJudgingId(judging.getId()).stream()
                .filter(t -> t.getDivisionCategoryId().equals(divisionCategoryId))
                .toList();
        var scoringRounds = roundsInCategory.stream()
                .filter(r -> r.getType() == RoundType.SCORING)
                .toList();
        boolean allScoringComplete = !scoringRounds.isEmpty() && scoringRounds.stream()
                .allMatch(t -> t.getStatus() == JudgingRoundStatus.COMPLETE);
        if (!allScoringComplete) {
            return;
        }
        // Ensure CategoryJudgingConfig exists (default mode if not configured).
        var config = categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId)
                .orElseGet(() -> categoryConfigRepository.save(new CategoryJudgingConfig(divisionCategoryId)));
        // New flow: ensure a medal JudgingRound (type=MEDAL) exists for the
        // category, then mark it READY. Auto-creating here means the medal
        // round always exists by the time scoring completes — no separate
        // admin step required for the minimal-touch migration.
        var medalRound = roundsInCategory.stream()
                .filter(r -> r.getType() == RoundType.MEDAL)
                .findFirst()
                .orElseGet(() -> {
                    var category = competitionService.findDivisionCategoryById(divisionCategoryId);
                    var newMedalRound = new JudgingRound(judging.getId(),
                            "Medal — " + category.getCode(), divisionCategoryId, null);
                    newMedalRound.convertToMedalRound(config.getMedalRoundMode());
                    return judgingRoundRepository.save(newMedalRound);
                });
        if (medalRound.getStatus() == JudgingRoundStatus.PENDING) {
            medalRound.markReady();
        }
        // Populate medalRound.entries from the eligible set, per mode:
        //  * COMPARATIVE: entries whose SUBMITTED scoresheet has the advance flag.
        //  * SCORE_BASED: every entry with a SUBMITTED scoresheet (all candidates).
        // Idempotent — uses Set semantics (assignEntry no-ops when already present).
        populateMedalRoundEntries(medalRound, divisionCategoryId);
        judgingRoundRepository.save(medalRound);
    }

    private void populateMedalRoundEntries(JudgingRound medalRound, UUID divisionCategoryId) {
        var mode = medalRound.getMedalMode();
        for (var entry : entryService.findEntriesByFinalCategoryId(divisionCategoryId)) {
            if (entry.getStatus() != EntryStatus.RECEIVED) {
                continue;
            }
            var sheetOpt = scoresheetRepository.findByEntryId(entry.getId());
            if (sheetOpt.isEmpty() || sheetOpt.get().getStatus() != ScoresheetStatus.SUBMITTED) {
                continue;
            }
            if (mode == MedalRoundMode.COMPARATIVE
                    && !sheetOpt.get().isAdvancedToMedalRound()) {
                continue;
            }
            medalRound.assignEntry(entry.getId());
        }
    }

    private String resolveDefaultCommentLanguage(UUID judgeUserId, Scoresheet sheet) {
        var profileLang = judgeProfileService.findByUserId(judgeUserId)
                .map(p -> p.getPreferredCommentLanguage())
                .orElse(null);
        if (profileLang != null) {
            return profileLang;
        }
        // No profile language → don't set; leave as null (admin can edit later)
        return null;
    }

    /**
     * Each per-criterion comment must clear {@link Scoresheet#MIN_PER_FIELD_COMMENT_LENGTH}.
     * Shared by the per-sheet submit bridge and {@link #markFilled}. The
     * additional ("overall") comment is optional and not checked here.
     */
    private void requireFieldCommentsLongEnough(Scoresheet sheet) {
        for (var field : sheet.getFields()) {
            var comment = field.getComment();
            if (comment == null || comment.trim().length() < Scoresheet.MIN_PER_FIELD_COMMENT_LENGTH) {
                throw new BusinessRuleException("error.scoresheet.field-comment-too-short",
                        field.getFieldName(),
                        String.valueOf(Scoresheet.MIN_PER_FIELD_COMMENT_LENGTH));
            }
        }
    }

    private void enforceCoi(UUID judgeUserId, Scoresheet sheet) {
        var result = coiCheckService.check(judgeUserId, sheet.getEntryId());
        if (result.hardBlock()) {
            throw new BusinessRuleException("error.coi.self-entry");
        }
    }

    private Scoresheet requireScoresheet(UUID id) {
        return scoresheetRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("error.scoresheet.not-found"));
    }

    private JudgingRound requireTable(UUID id) {
        return judgingRoundRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("error.judging-table.not-found"));
    }

    private Judging requireJudging(UUID id) {
        return judgingRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("error.judging.not-found"));
    }

    private void requireNotFrozen(UUID divisionId) {
        if (competitionService.findDivisionById(divisionId).getStatus().isResultsFrozen()) {
            throw new BusinessRuleException("error.judging.results-published-frozen");
        }
    }

    /**
     * Effective medal-round status for a category, sourced from the medal
     * {@link JudgingRound}. Returns {@code null} when no medal round exists.
     */
    private JudgingRoundStatus effectiveMedalRoundStatus(UUID divisionCategoryId) {
        return judgingRoundRepository
                .findFirstByDivisionCategoryIdAndType(divisionCategoryId, RoundType.MEDAL)
                .map(JudgingRound::getStatus)
                .orElse(null);
    }

    /**
     * Used after a table reopens from COMPLETE: if the category's medal round
     * was READY (i.e., waiting on this table's completion), drop it back to
     * PENDING.
     */
    private void retreatMedalRoundFromReady(UUID divisionCategoryId) {
        judgingRoundRepository
                .findFirstByDivisionCategoryIdAndType(divisionCategoryId, RoundType.MEDAL)
                .filter(r -> r.getStatus() == JudgingRoundStatus.READY)
                .ifPresent(r -> {
                    r.markPending();
                    judgingRoundRepository.save(r);
                });
    }

    private void requireNotFrozenForSheet(Scoresheet sheet) {
        var table = requireTable(sheet.getRoundId());
        var judging = requireJudging(table.getJudgingId());
        requireNotFrozen(judging.getDivisionId());
    }

    @Override
    public long countByRoundIdAndStatus(UUID roundId, ScoresheetStatus status) {
        return scoresheetRepository.countByRoundIdAndStatus(roundId, status);
    }

    @Override
    public long countByRoundIdAndStatusNot(UUID roundId, ScoresheetStatus status) {
        return scoresheetRepository.countByRoundIdAndStatusNot(roundId, status);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<UUID, Integer> runningTotalsByRoundId(UUID roundId) {
        var sheets = scoresheetRepository.findByRoundId(roundId);
        var totals = new java.util.HashMap<UUID, Integer>();
        for (var sheet : sheets) {
            if (sheet.getTotalScore() != null) {
                totals.put(sheet.getId(), sheet.getTotalScore());
                continue;
            }
            int running = sheet.getFields().stream()
                    .mapToInt(f -> f.getValue() == null ? 0 : f.getValue())
                    .sum();
            totals.put(sheet.getId(), running);
        }
        return totals;
    }

    @Override
    public void deleteAllForRound(UUID roundId) {
        var sheets = scoresheetRepository.findByRoundId(roundId);
        if (!sheets.isEmpty()) {
            scoresheetRepository.deleteAll(sheets);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Scoresheet> findByRoundId(UUID roundId) {
        return scoresheetRepository.findByRoundId(roundId);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<Scoresheet> findById(UUID scoresheetId) {
        return scoresheetRepository.findById(scoresheetId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Scoresheet> findByEntryIdOrderBySubmittedAtAsc(UUID entryId) {
        return scoresheetRepository.findByEntryIdOrderBySubmittedAtAsc(entryId);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<UUID> findNextDraftForJudge(UUID judgeUserId) {
        var tables = judgingRoundRepository.findByJudgeUserId(judgeUserId);
        return tables.stream()
                .sorted((a, b) -> {
                    var ad = a.getScheduledDate();
                    var bd = b.getScheduledDate();
                    if (ad == null && bd == null) {
                        return a.getName().compareTo(b.getName());
                    }
                    if (ad == null) return 1;
                    if (bd == null) return -1;
                    int dateCmp = ad.compareTo(bd);
                    return dateCmp != 0 ? dateCmp : a.getName().compareTo(b.getName());
                })
                .flatMap(t -> scoresheetRepository.findByRoundId(t.getId()).stream())
                .filter(s -> s.getStatus() != ScoresheetStatus.SUBMITTED)
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .map(Scoresheet::getId)
                .findFirst();
    }

    @SuppressWarnings("unused")
    private static final Set<ScoresheetStatus> ANY_STATUS = Set.of(ScoresheetStatus.values());
}
