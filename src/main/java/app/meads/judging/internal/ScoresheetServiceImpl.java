package app.meads.judging.internal;

import app.meads.BusinessRuleException;
import app.meads.competition.CompetitionService;
import app.meads.entry.EntryService;
import app.meads.judging.CategoryJudgingConfig;
import app.meads.judging.CoiCheckService;
import app.meads.judging.Judging;
import app.meads.judging.JudgeProfileService;
import app.meads.judging.JudgingTable;
import app.meads.judging.JudgingTableStatus;
import app.meads.judging.MedalRoundStatus;
import app.meads.judging.Scoresheet;
import app.meads.judging.ScoresheetRevertedEvent;
import app.meads.judging.ScoresheetService;
import app.meads.judging.ScoresheetStatus;
import app.meads.judging.ScoresheetSubmittedEvent;
import app.meads.judging.TableCompletedEvent;
import app.meads.judging.TableReopenedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@Validated
public class ScoresheetServiceImpl implements ScoresheetService {

    private final ScoresheetRepository scoresheetRepository;
    private final JudgingTableRepository judgingTableRepository;
    private final CategoryJudgingConfigRepository categoryConfigRepository;
    private final JudgingRepository judgingRepository;
    private final EntryService entryService;
    private final CompetitionService competitionService;
    private final JudgeProfileService judgeProfileService;
    private final CoiCheckService coiCheckService;
    private final ApplicationEventPublisher eventPublisher;

    ScoresheetServiceImpl(ScoresheetRepository scoresheetRepository,
                          JudgingTableRepository judgingTableRepository,
                          CategoryJudgingConfigRepository categoryConfigRepository,
                          JudgingRepository judgingRepository,
                          EntryService entryService,
                          CompetitionService competitionService,
                          JudgeProfileService judgeProfileService,
                          CoiCheckService coiCheckService,
                          ApplicationEventPublisher eventPublisher) {
        this.scoresheetRepository = scoresheetRepository;
        this.judgingTableRepository = judgingTableRepository;
        this.categoryConfigRepository = categoryConfigRepository;
        this.judgingRepository = judgingRepository;
        this.entryService = entryService;
        this.competitionService = competitionService;
        this.judgeProfileService = judgeProfileService;
        this.coiCheckService = coiCheckService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void createScoresheetsForTable(UUID tableId) {
        var table = requireTable(tableId);
        var entries = entryService.findEntriesByFinalCategoryId(table.getDivisionCategoryId());
        for (var entry : entries) {
            if (scoresheetRepository.findByEntryId(entry.getId()).isEmpty()) {
                scoresheetRepository.save(new Scoresheet(tableId, entry.getId()));
                log.info("Created DRAFT scoresheet for entry {} at table {}", entry.getId(), tableId);
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
        var judging = judgingRepository.findByDivisionId(entry.getDivisionId()).orElse(null);
        if (judging == null) {
            return;
        }
        var matchingTable = judgingTableRepository.findByJudgingId(judging.getId()).stream()
                .filter(t -> t.getDivisionCategoryId().equals(entry.getFinalCategoryId()))
                .filter(t -> t.getStatus() == JudgingTableStatus.ROUND_1)
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
    public void updateScore(UUID scoresheetId, String fieldName,
                            Integer value, String comment, UUID judgeUserId) {
        var sheet = requireScoresheet(scoresheetId);
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
        enforceCoi(judgeUserId, sheet);
        var table = requireTable(sheet.getTableId());
        var configOpt = categoryConfigRepository.findByDivisionCategoryId(table.getDivisionCategoryId());
        if (configOpt.isPresent() && configOpt.get().getMedalRoundStatus() == MedalRoundStatus.ACTIVE) {
            throw new BusinessRuleException("error.scoresheet.medal-round-active");
        }
        sheet.setAdvancedToMedalRound(advanced);
        scoresheetRepository.save(sheet);
    }

    @Override
    public void setCommentLanguage(UUID scoresheetId, String languageCode,
                                    UUID judgeUserId) {
        var sheet = requireScoresheet(scoresheetId);
        enforceCoi(judgeUserId, sheet);
        var table = requireTable(sheet.getTableId());
        var judging = requireJudging(table.getJudgingId());
        var division = competitionService.findDivisionById(judging.getDivisionId());
        var competition = competitionService.findCompetitionById(division.getCompetitionId());
        var allowed = new HashSet<>(competition.getCommentLanguages());
        judgeProfileService.findByUserId(judgeUserId)
                .map(p -> p.getPreferredCommentLanguage())
                .ifPresent(code -> { if (code != null) allowed.add(code); });
        if (!allowed.contains(languageCode)) {
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

    @Override
    public void submit(UUID scoresheetId, UUID judgeUserId) {
        var sheet = requireScoresheet(scoresheetId);
        enforceCoi(judgeUserId, sheet);
        if (sheet.getCommentLanguage() == null) {
            var defaultLang = resolveDefaultCommentLanguage(judgeUserId, sheet);
            if (defaultLang != null) {
                sheet.setCommentLanguage(defaultLang);
            }
        }
        try {
            sheet.submit();
        } catch (IllegalStateException e) {
            throw new BusinessRuleException("error.scoresheet.incomplete", e.getMessage());
        }
        scoresheetRepository.save(sheet);
        var table = requireTable(sheet.getTableId());
        eventPublisher.publishEvent(new ScoresheetSubmittedEvent(
                sheet.getId(), sheet.getEntryId(), table.getId(),
                sheet.getTotalScore(), sheet.getSubmittedAt()));
        // Cascade table → category if all sheets at this table are SUBMITTED
        var tableSheets = scoresheetRepository.findByTableId(table.getId());
        boolean allSubmitted = tableSheets.stream()
                .allMatch(s -> s.getStatus() == ScoresheetStatus.SUBMITTED);
        if (allSubmitted && table.getStatus() == JudgingTableStatus.ROUND_1) {
            table.markComplete();
            judgingTableRepository.save(table);
            var judging = requireJudging(table.getJudgingId());
            eventPublisher.publishEvent(new TableCompletedEvent(
                    table.getId(), table.getDivisionCategoryId(),
                    judging.getDivisionId(), Instant.now()));
            cascadeMarkCategoryReadyIfAllTablesComplete(judging, table.getDivisionCategoryId());
        }
        log.info("Submitted scoresheet {} (total={})", sheet.getId(), sheet.getTotalScore());
    }

    @Override
    public void revertToDraft(UUID scoresheetId, UUID adminUserId) {
        var sheet = requireScoresheet(scoresheetId);
        var table = requireTable(sheet.getTableId());
        var judging = requireJudging(table.getJudgingId());
        if (!competitionService.isAuthorizedForDivision(judging.getDivisionId(), adminUserId)) {
            throw new BusinessRuleException("error.auth.unauthorized");
        }
        var configOpt = categoryConfigRepository.findByDivisionCategoryId(table.getDivisionCategoryId());
        if (configOpt.isPresent()) {
            var status = configOpt.get().getMedalRoundStatus();
            if (status != MedalRoundStatus.PENDING && status != MedalRoundStatus.READY) {
                throw new BusinessRuleException("error.scoresheet.cannot-revert-medal-active");
            }
        }
        try {
            sheet.revertToDraft();
        } catch (IllegalStateException e) {
            throw new BusinessRuleException("error.scoresheet.not-submitted");
        }
        scoresheetRepository.save(sheet);
        eventPublisher.publishEvent(new ScoresheetRevertedEvent(
                sheet.getId(), sheet.getEntryId(), table.getId(), Instant.now()));
        if (table.getStatus() == JudgingTableStatus.COMPLETE) {
            table.reopenToRound1();
            judgingTableRepository.save(table);
            eventPublisher.publishEvent(new TableReopenedEvent(
                    table.getId(), table.getDivisionCategoryId(),
                    judging.getDivisionId(), Instant.now()));
            // If category config was READY, retreat to PENDING
            configOpt.ifPresent(config -> {
                if (config.getMedalRoundStatus() == MedalRoundStatus.READY) {
                    config.markPending();
                    categoryConfigRepository.save(config);
                }
            });
        }
        log.info("Reverted scoresheet {} to DRAFT", sheet.getId());
    }

    @Override
    public void moveToTable(UUID scoresheetId, UUID newTableId,
                            UUID adminUserId) {
        var sheet = requireScoresheet(scoresheetId);
        var newTable = requireTable(newTableId);
        var judging = requireJudging(newTable.getJudgingId());
        if (!competitionService.isAuthorizedForDivision(judging.getDivisionId(), adminUserId)) {
            throw new BusinessRuleException("error.auth.unauthorized");
        }
        var entry = entryService.findEntryById(sheet.getEntryId());
        if (entry.getFinalCategoryId() == null
                || !entry.getFinalCategoryId().equals(newTable.getDivisionCategoryId())) {
            throw new BusinessRuleException("error.scoresheet.category-mismatch");
        }
        try {
            sheet.moveToTable(newTableId);
        } catch (IllegalStateException e) {
            throw new BusinessRuleException("error.scoresheet.not-draft");
        }
        scoresheetRepository.save(sheet);
        log.info("Moved scoresheet {} to table {}", sheet.getId(), newTableId);
    }

    // --- helpers ---

    private void cascadeMarkCategoryReadyIfAllTablesComplete(Judging judging,
                                                              UUID divisionCategoryId) {
        var allTables = judgingTableRepository.findByJudgingId(judging.getId()).stream()
                .filter(t -> t.getDivisionCategoryId().equals(divisionCategoryId))
                .toList();
        boolean allComplete = !allTables.isEmpty() && allTables.stream()
                .allMatch(t -> t.getStatus() == JudgingTableStatus.COMPLETE);
        if (!allComplete) {
            return;
        }
        var config = categoryConfigRepository.findByDivisionCategoryId(divisionCategoryId)
                .orElseGet(() -> categoryConfigRepository.save(new CategoryJudgingConfig(divisionCategoryId)));
        if (config.getMedalRoundStatus() == MedalRoundStatus.PENDING) {
            config.markReady();
            categoryConfigRepository.save(config);
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

    private JudgingTable requireTable(UUID id) {
        return judgingTableRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("error.judging-table.not-found"));
    }

    private Judging requireJudging(UUID id) {
        return judgingRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("error.judging.not-found"));
    }

    @Override
    public long countByTableIdAndStatus(UUID tableId, ScoresheetStatus status) {
        return scoresheetRepository.countByTableIdAndStatus(tableId, status);
    }

    @SuppressWarnings("unused")
    private static final Set<ScoresheetStatus> ANY_STATUS = Set.of(ScoresheetStatus.values());
}
