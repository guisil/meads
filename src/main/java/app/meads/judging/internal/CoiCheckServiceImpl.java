package app.meads.judging.internal;

import app.meads.BusinessRuleException;
import app.meads.competition.CompetitionService;
import app.meads.entry.EntryService;
import app.meads.identity.UserService;
import app.meads.judging.CoiCheckService;
import app.meads.judging.ManualCoiView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CoiCheckServiceImpl implements CoiCheckService {

    private static final String SIMILAR_MEADERY_KEY = "coi.warning.similar-meadery";

    private final UserService userService;
    private final EntryService entryService;
    private final CompetitionService competitionService;
    private final ManualCoiRepository manualCoiRepository;

    CoiCheckServiceImpl(UserService userService, EntryService entryService,
                        CompetitionService competitionService,
                        ManualCoiRepository manualCoiRepository) {
        this.userService = userService;
        this.entryService = entryService;
        this.competitionService = competitionService;
        this.manualCoiRepository = manualCoiRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CoiResult check(UUID judgeUserId, UUID entryId) {
        var entry = entryService.findEntryById(entryId);
        var entrantUserId = entry.getUserId();
        if (entrantUserId.equals(judgeUserId)) {
            return CoiResult.blocking();
        }
        var competitionId = competitionService.findDivisionById(entry.getDivisionId()).getCompetitionId();
        if (manualCoiRepository.existsByCompetitionIdAndJudgeUserIdAndEntrantUserId(
                competitionId, judgeUserId, entrantUserId)) {
            return CoiResult.blocking();
        }
        var judge = userService.findById(judgeUserId);
        var entrant = userService.findById(entrantUserId);
        if (MeaderyNameNormalizer.areSimilar(
                judge.getMeaderyName(), judge.getCountry(),
                entrant.getMeaderyName(), entrant.getCountry())) {
            return CoiResult.warn(SIMILAR_MEADERY_KEY);
        }
        return CoiResult.clear();
    }

    @Override
    public void addManualCoi(UUID competitionId, UUID judgeUserId,
                             UUID entrantUserId, UUID adminUserId) {
        if (!competitionService.isAuthorizedForCompetition(competitionId, adminUserId)) {
            throw new BusinessRuleException("error.coi.manual.not-authorized");
        }
        if (judgeUserId.equals(entrantUserId)) {
            throw new BusinessRuleException("error.coi.manual.same-user");
        }
        if (manualCoiRepository.existsByCompetitionIdAndJudgeUserIdAndEntrantUserId(
                competitionId, judgeUserId, entrantUserId)) {
            throw new BusinessRuleException("error.coi.manual.duplicate");
        }
        manualCoiRepository.save(new ManualCoi(competitionId, judgeUserId, entrantUserId, adminUserId));
    }

    @Override
    public void removeManualCoi(UUID manualCoiId, UUID adminUserId) {
        var coi = manualCoiRepository.findById(manualCoiId)
                .orElseThrow(() -> new BusinessRuleException("error.coi.manual.not-found"));
        if (!competitionService.isAuthorizedForCompetition(coi.getCompetitionId(), adminUserId)) {
            throw new BusinessRuleException("error.coi.manual.not-authorized");
        }
        manualCoiRepository.delete(coi);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManualCoiView> findManualCois(UUID competitionId) {
        return manualCoiRepository.findByCompetitionId(competitionId).stream()
                .map(coi -> {
                    var judge = userService.findById(coi.getJudgeUserId());
                    var entrant = userService.findById(coi.getEntrantUserId());
                    return new ManualCoiView(coi.getId(),
                            coi.getJudgeUserId(), judge.getName(), judge.getEmail(),
                            coi.getEntrantUserId(), entrant.getName(), entrant.getEmail());
                })
                .toList();
    }
}
