package app.meads.awards.internal;

import app.meads.BusinessRuleException;
import app.meads.awards.AdminResultsView;
import app.meads.awards.AnonymizedScoresheetView;
import app.meads.awards.AwardsService;
import app.meads.awards.EntrantResultRow;
import app.meads.awards.PublicResultsView;
import app.meads.awards.Publication;
import app.meads.awards.ResultsPublishedEvent;
import app.meads.awards.ResultsRepublishedEvent;
import app.meads.competition.CompetitionService;
import app.meads.competition.DivisionCategory;
import app.meads.competition.DivisionStatus;
import app.meads.entry.Entry;
import app.meads.entry.EntryService;
import app.meads.identity.User;
import app.meads.identity.UserService;
import app.meads.judging.JudgingService;
import app.meads.judging.Medal;
import app.meads.judging.ScoreField;
import app.meads.judging.Scoresheet;
import app.meads.judging.ScoresheetService;
import app.meads.judging.ScoresheetStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@Validated
public class AwardsServiceImpl implements AwardsService {

    private static final int JUSTIFICATION_MIN_LENGTH = 20;
    private static final int JUSTIFICATION_MAX_LENGTH = 1000;

    private final PublicationRepository publicationRepository;
    private final CompetitionService competitionService;
    private final EntryService entryService;
    private final JudgingService judgingService;
    private final ScoresheetService scoresheetService;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    public AwardsServiceImpl(PublicationRepository publicationRepository,
                             CompetitionService competitionService,
                             EntryService entryService,
                             JudgingService judgingService,
                             ScoresheetService scoresheetService,
                             UserService userService,
                             ApplicationEventPublisher eventPublisher) {
        this.publicationRepository = publicationRepository;
        this.competitionService = competitionService;
        this.entryService = entryService;
        this.judgingService = judgingService;
        this.scoresheetService = scoresheetService;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Publication publish(@NotNull UUID divisionId, @NotNull UUID adminUserId) {
        if (!competitionService.isAuthorizedForDivision(divisionId, adminUserId)) {
            throw new BusinessRuleException("error.awards.unauthorized");
        }
        var division = competitionService.findDivisionById(divisionId);
        if (division.getStatus() != DivisionStatus.DELIBERATION) {
            throw new BusinessRuleException("error.awards.publish-wrong-status");
        }
        if (publicationRepository.existsByDivisionId(divisionId)) {
            throw new BusinessRuleException("error.awards.already-published");
        }
        var publication = publicationRepository.save(new Publication(divisionId, adminUserId));
        competitionService.advanceDivisionStatus(divisionId, adminUserId);
        eventPublisher.publishEvent(new ResultsPublishedEvent(
                divisionId, publication.getId(), publication.getVersion(),
                publication.getPublishedAt(), publication.getPublishedBy()));
        log.info("Published results for division {} (version {})", divisionId, publication.getVersion());
        return publication;
    }

    @Override
    public Publication republish(@NotNull UUID divisionId,
                                  @NotBlank String justification,
                                  @NotNull UUID adminUserId) {
        if (!competitionService.isAuthorizedForDivision(divisionId, adminUserId)) {
            throw new BusinessRuleException("error.awards.unauthorized");
        }
        var division = competitionService.findDivisionById(divisionId);
        if (division.getStatus() != DivisionStatus.RESULTS_PUBLISHED) {
            throw new BusinessRuleException("error.awards.republish-wrong-status");
        }
        var trimmed = justification.trim();
        if (trimmed.length() < JUSTIFICATION_MIN_LENGTH) {
            throw new BusinessRuleException("error.awards.justification-too-short");
        }
        if (trimmed.length() > JUSTIFICATION_MAX_LENGTH) {
            throw new BusinessRuleException("error.awards.justification-too-long");
        }
        var previous = publicationRepository.findTopByDivisionIdOrderByVersionDesc(divisionId)
                .orElseThrow(() -> new BusinessRuleException("error.awards.no-prior-publication"));
        var publication = publicationRepository.save(
                Publication.republish(divisionId, previous.getVersion(), trimmed, adminUserId));
        eventPublisher.publishEvent(new ResultsRepublishedEvent(
                divisionId, publication.getId(), publication.getVersion(),
                publication.getPublishedAt(), publication.getPublishedBy(), trimmed));
        log.info("Republished results for division {} (version {})", divisionId, publication.getVersion());
        return publication;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Publication> getLatestPublication(@NotNull UUID divisionId) {
        return publicationRepository.findTopByDivisionIdOrderByVersionDesc(divisionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Publication> getPublicationHistory(@NotNull UUID divisionId) {
        return publicationRepository.findByDivisionIdOrderByVersionAsc(divisionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EntrantResultRow> getResultsForEntrant(@NotNull UUID userId, @NotNull UUID divisionId) {
        var division = competitionService.findDivisionById(divisionId);
        if (division.getStatus() != DivisionStatus.RESULTS_PUBLISHED) {
            throw new BusinessRuleException("error.awards.not-published");
        }
        var entries = entryService.findEntriesByDivisionAndUser(divisionId, userId);
        var rows = new ArrayList<EntrantResultRow>();
        for (var entry : entries) {
            rows.add(buildEntrantRow(entry));
        }
        return rows;
    }

    private EntrantResultRow buildEntrantRow(Entry entry) {
        var categoryInfo = resolveCategoryInfo(entry);
        var sheet = scoresheetService.findByEntryIdOrderBySubmittedAtAsc(entry.getId())
                .stream().findFirst().orElse(null);
        Integer total = sheet != null ? sheet.getTotalScore() : null;
        boolean advanced = sheet != null && sheet.isAdvancedToMedalRound();
        UUID sheetId = sheet != null && sheet.getStatus() == ScoresheetStatus.SUBMITTED
                ? sheet.getId() : null;
        var medal = judgingService.findMedalAwardByEntryId(entry.getId())
                .map(a -> a.getMedal()).orElse(null);
        var bosPlace = judgingService.findBosPlacementByEntryId(entry.getId())
                .map(p -> p.getPlace()).orElse(null);
        return new EntrantResultRow(
                entry.getId(), entry.getEntryCode(), entry.getMeadName(),
                categoryInfo.code(), categoryInfo.name(),
                entry.getStatus(), total, advanced, medal, bosPlace, sheetId);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminResultsView getResultsForAdmin(@NotNull UUID divisionId, @NotNull UUID adminUserId) {
        if (!competitionService.isAuthorizedForDivision(divisionId, adminUserId)) {
            throw new BusinessRuleException("error.awards.unauthorized");
        }
        var division = competitionService.findDivisionById(divisionId);
        var competition = competitionService.findCompetitionById(division.getCompetitionId());
        var judgingCategories = competitionService.findJudgingCategories(divisionId);
        var entries = entryService.findEntriesByDivision(divisionId);
        var entriesByCategory = new HashMap<UUID, List<Entry>>();
        for (var entry : entries) {
            if (entry.getFinalCategoryId() != null) {
                entriesByCategory.computeIfAbsent(entry.getFinalCategoryId(), k -> new ArrayList<>())
                        .add(entry);
            }
        }
        var leaderboards = new ArrayList<AdminResultsView.AdminCategoryLeaderboard>();
        for (var category : judgingCategories) {
            var catEntries = entriesByCategory.getOrDefault(category.getId(), List.of());
            var rows = new ArrayList<AdminResultsView.AdminEntryRow>();
            for (var entry : catEntries) {
                rows.add(buildAdminEntryRow(entry));
            }
            rows.sort(Comparator.comparing(
                    (AdminResultsView.AdminEntryRow r) -> r.round1Total() == null ? -1 : r.round1Total())
                    .reversed());
            leaderboards.add(new AdminResultsView.AdminCategoryLeaderboard(
                    category.getId(), category.getCode(), category.getName(), rows));
        }
        var bosRows = new ArrayList<AdminResultsView.AdminBosRow>();
        var placements = judgingService.findBosPlacementsForDivision(divisionId, adminUserId);
        for (var placement : placements) {
            var entry = entryService.findEntryById(placement.getEntryId());
            var entrant = userService.findById(entry.getUserId());
            var catInfo = resolveCategoryInfo(entry);
            bosRows.add(new AdminResultsView.AdminBosRow(
                    placement.getPlace(), entry.getId(), entry.getEntryCode(),
                    entrant.getName(), entrant.getMeaderyName(), entry.getMeadName(),
                    catInfo.code()));
        }
        var history = publicationRepository.findByDivisionIdOrderByVersionAsc(divisionId)
                .stream()
                .map(p -> new AdminResultsView.PublicationSummary(
                        p.getVersion(), p.getPublishedAt(),
                        userService.findById(p.getPublishedBy()).getName(),
                        p.getJustification(), p.isInitial()))
                .toList();
        return new AdminResultsView(
                divisionId, division.getName(), competition.getName(),
                division.getStatus().name(), leaderboards, bosRows, history);
    }

    private AdminResultsView.AdminEntryRow buildAdminEntryRow(Entry entry) {
        var entrant = userService.findById(entry.getUserId());
        var sheet = scoresheetService.findByEntryIdOrderBySubmittedAtAsc(entry.getId())
                .stream().findFirst().orElse(null);
        Integer total = sheet != null ? sheet.getTotalScore() : null;
        boolean advanced = sheet != null && sheet.isAdvancedToMedalRound();
        var medalAward = judgingService.findMedalAwardByEntryId(entry.getId()).orElse(null);
        var medalLabel = formatAdminMedalLabel(medalAward);
        var bosPlace = judgingService.findBosPlacementByEntryId(entry.getId())
                .map(p -> p.getPlace()).orElse(null);
        return new AdminResultsView.AdminEntryRow(
                entry.getId(), entry.getEntryCode(), entrant.getName(),
                entrant.getMeaderyName(), entry.getMeadName(),
                total, advanced, medalLabel, bosPlace);
    }

    private String formatAdminMedalLabel(app.meads.judging.MedalAward award) {
        if (award == null) {
            return "—";
        }
        if (award.getMedal() == null) {
            return "Withheld";
        }
        return award.getMedal().name();
    }

    @Override
    @Transactional(readOnly = true)
    public PublicResultsView getPublicResults(@NotBlank String competitionShortName,
                                              @NotBlank String divisionShortName) {
        var competition = competitionService.findCompetitionByShortName(competitionShortName);
        var division = competitionService.findDivisionByShortName(competition.getId(), divisionShortName);
        if (division.getStatus() != DivisionStatus.RESULTS_PUBLISHED) {
            throw new BusinessRuleException("error.awards.not-published");
        }
        var judgingCategories = competitionService.findJudgingCategories(division.getId());
        var entries = entryService.findEntriesByDivision(division.getId());
        var entriesByCategory = new HashMap<UUID, List<Entry>>();
        for (var entry : entries) {
            if (entry.getFinalCategoryId() != null) {
                entriesByCategory.computeIfAbsent(entry.getFinalCategoryId(), k -> new ArrayList<>())
                        .add(entry);
            }
        }
        var sections = new ArrayList<PublicResultsView.PublicCategorySection>();
        for (var category : judgingCategories) {
            var rows = new ArrayList<PublicResultsView.PublicMedalRow>();
            for (var entry : entriesByCategory.getOrDefault(category.getId(), List.of())) {
                var medalAward = judgingService.findMedalAwardByEntryId(entry.getId()).orElse(null);
                if (medalAward == null || medalAward.getMedal() == null) {
                    continue;
                }
                var entrant = userService.findById(entry.getUserId());
                var row = new PublicResultsView.PublicMedalRow(entry.getMeadName(), entrant.getMeaderyName());
                if (!rowMedalMatches(medalAward, Medal.GOLD) && !rowMedalMatches(medalAward, Medal.SILVER)
                        && !rowMedalMatches(medalAward, Medal.BRONZE)) {
                    continue;
                }
                rows.add(row);
            }
            var golds = filterByMedal(entriesByCategory.getOrDefault(category.getId(), List.of()), Medal.GOLD);
            var silvers = filterByMedal(entriesByCategory.getOrDefault(category.getId(), List.of()), Medal.SILVER);
            var bronzes = filterByMedal(entriesByCategory.getOrDefault(category.getId(), List.of()), Medal.BRONZE);
            if (!golds.isEmpty() || !silvers.isEmpty() || !bronzes.isEmpty()) {
                sections.add(new PublicResultsView.PublicCategorySection(
                        category.getCode(), category.getName(), golds, silvers, bronzes));
            }
        }
        var publicBos = new ArrayList<PublicResultsView.PublicBosRow>();
        var placements = publicBosPlacements(division.getId());
        for (var placement : placements) {
            var entry = entryService.findEntryById(placement.getEntryId());
            var entrant = userService.findById(entry.getUserId());
            publicBos.add(new PublicResultsView.PublicBosRow(
                    placement.getPlace(), entry.getMeadName(), entrant.getMeaderyName()));
        }
        var history = publicationRepository.findByDivisionIdOrderByVersionAsc(division.getId());
        var latest = history.isEmpty() ? null : history.get(history.size() - 1);
        return new PublicResultsView(
                competition.getName(), division.getName(),
                latest != null ? latest.getPublishedAt() : null,
                history.size() > 1, sections, publicBos);
    }

    private List<PublicResultsView.PublicMedalRow> filterByMedal(List<Entry> entries, Medal medal) {
        var rows = new ArrayList<PublicResultsView.PublicMedalRow>();
        for (var entry : entries) {
            var award = judgingService.findMedalAwardByEntryId(entry.getId()).orElse(null);
            if (award != null && award.getMedal() == medal) {
                var entrant = userService.findById(entry.getUserId());
                rows.add(new PublicResultsView.PublicMedalRow(entry.getMeadName(), entrant.getMeaderyName()));
            }
        }
        return rows;
    }

    private boolean rowMedalMatches(app.meads.judging.MedalAward award, Medal medal) {
        return award.getMedal() == medal;
    }

    private List<app.meads.judging.BosPlacement> publicBosPlacements(UUID divisionId) {
        // Fetch via internal repo path; we need a non-auth-gated version
        var placements = new ArrayList<app.meads.judging.BosPlacement>();
        for (var entry : entryService.findEntriesByDivision(divisionId)) {
            judgingService.findBosPlacementByEntryId(entry.getId()).ifPresent(placements::add);
        }
        placements.sort(Comparator.comparingInt(app.meads.judging.BosPlacement::getPlace));
        return placements;
    }

    @Override
    @Transactional(readOnly = true)
    public AnonymizedScoresheetView getAnonymizedScoresheet(@NotNull UUID scoresheetId,
                                                              @NotNull UUID requestingUserId) {
        var sheet = scoresheetService.findById(scoresheetId)
                .orElseThrow(() -> new BusinessRuleException("error.awards.scoresheet-not-found"));
        if (sheet.getStatus() != ScoresheetStatus.SUBMITTED) {
            throw new BusinessRuleException("error.awards.scoresheet-not-found");
        }
        var entry = entryService.findEntryById(sheet.getEntryId());
        var division = competitionService.findDivisionById(entry.getDivisionId());
        boolean isAdmin = competitionService.isAuthorizedForDivision(entry.getDivisionId(), requestingUserId);
        boolean isOwner = entry.getUserId().equals(requestingUserId);
        if (!isAdmin && !isOwner) {
            throw new BusinessRuleException("error.awards.unauthorized");
        }
        if (!isAdmin && division.getStatus() != DivisionStatus.RESULTS_PUBLISHED) {
            throw new BusinessRuleException("error.awards.not-published");
        }
        var sheets = scoresheetService.findByEntryIdOrderBySubmittedAtAsc(sheet.getEntryId());
        var anonymized = new ArrayList<AnonymizedScoresheetView.AnonymizedScoresheet>();
        int ordinal = 1;
        for (var s : sheets) {
            if (s.getStatus() != ScoresheetStatus.SUBMITTED) {
                continue;
            }
            anonymized.add(buildAnonymizedScoresheet(s, ordinal++));
        }
        var categoryInfo = resolveCategoryInfo(entry);
        return new AnonymizedScoresheetView(
                sheet.getId(), entry.getId(), entry.getEntryCode(),
                entry.getMeadName(), categoryInfo.code(), categoryInfo.name(), anonymized);
    }

    private AnonymizedScoresheetView.AnonymizedScoresheet buildAnonymizedScoresheet(Scoresheet sheet, int ordinal) {
        var fieldScores = new ArrayList<AnonymizedScoresheetView.FieldScore>();
        for (ScoreField f : sheet.getFields()) {
            int value = f.getValue() != null ? f.getValue() : 0;
            fieldScores.add(new AnonymizedScoresheetView.FieldScore(
                    f.getFieldName(), value, f.getMaxValue(), null));
        }
        return new AnonymizedScoresheetView.AnonymizedScoresheet(
                ordinal, sheet.getCommentLanguage(),
                sheet.getTotalScore(), fieldScores, sheet.getOverallComments());
    }

    private CategoryInfo resolveCategoryInfo(Entry entry) {
        UUID catId = entry.getFinalCategoryId() != null
                ? entry.getFinalCategoryId()
                : entry.getInitialCategoryId();
        if (catId == null) {
            return new CategoryInfo("", "");
        }
        DivisionCategory cat = competitionService.findDivisionCategoryById(catId);
        return new CategoryInfo(cat.getCode(), cat.getName());
    }

    private record CategoryInfo(String code, String name) {
    }
}
