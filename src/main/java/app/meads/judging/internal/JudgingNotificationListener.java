package app.meads.judging.internal;

import app.meads.LanguageMapping;
import app.meads.competition.CompetitionService;
import app.meads.entry.EntryService;
import app.meads.identity.EmailService;
import app.meads.identity.JwtMagicLinkService;
import app.meads.identity.UserService;
import app.meads.judging.MedalRoundActivatedEvent;
import app.meads.judging.ScoresheetRevertedEvent;
import app.meads.judging.RoundStartedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.UUID;

/**
 * Sends "your judging work is ready" emails to judges. Notifies on the three
 * judging events that put new work in front of a judge: a table being started,
 * a submitted scoresheet being reopened, and a medal round being activated.
 * The other 10 judging events are admin-triggered state transitions with no
 * judge-facing call to action, so they are intentionally not consumed here.
 */
@Slf4j
@Component
public class JudgingNotificationListener {

    private static final Duration LINK_VALIDITY = Duration.ofDays(7);

    private final JudgingRoundRepository judgingRoundRepository;
    private final ScoresheetRepository scoresheetRepository;
    private final CompetitionService competitionService;
    private final EntryService entryService;
    private final UserService userService;
    private final EmailService emailService;
    private final JwtMagicLinkService jwtMagicLinkService;

    JudgingNotificationListener(JudgingRoundRepository judgingRoundRepository,
                                ScoresheetRepository scoresheetRepository,
                                CompetitionService competitionService,
                                EntryService entryService,
                                UserService userService,
                                EmailService emailService,
                                JwtMagicLinkService jwtMagicLinkService) {
        this.judgingRoundRepository = judgingRoundRepository;
        this.scoresheetRepository = scoresheetRepository;
        this.competitionService = competitionService;
        this.entryService = entryService;
        this.userService = userService;
        this.emailService = emailService;
        this.jwtMagicLinkService = jwtMagicLinkService;
    }

    @ApplicationModuleListener
    public void on(RoundStartedEvent event) {
        var table = judgingRoundRepository.findById(event.roundId()).orElse(null);
        if (table == null) {
            log.warn("RoundStartedEvent for unknown table {} — no judges notified", event.roundId());
            return;
        }
        var division = competitionService.findDivisionById(event.divisionId());
        var competition = competitionService.findCompetitionById(division.getCompetitionId());
        var category = competitionService.findDivisionCategoryById(event.divisionCategoryId());

        for (var assignment : table.getAssignments()) {
            var judge = userService.findById(assignment.getJudgeUserId());
            var locale = LanguageMapping.resolveLocale(judge.getPreferredLanguage(), judge.getCountry());
            var categoryLabel = category.getCode() + " — " + category.getName(locale);
            var link = jwtMagicLinkService.generateLink(judge.getEmail(), LINK_VALIDITY);
            emailService.sendJudgingTableReady(judge.getEmail(), table.getName(),
                    categoryLabel, competition.getName(), division.getName(), link, locale);
            log.info("Sent judging-table-ready email to {} for table '{}'",
                    judge.getEmail(), table.getName());
        }
    }

    @ApplicationModuleListener
    public void on(ScoresheetRevertedEvent event) {
        var scoresheet = scoresheetRepository.findById(event.scoresheetId()).orElse(null);
        if (scoresheet == null) {
            log.warn("ScoresheetRevertedEvent for unknown scoresheet {} — no judge notified",
                    event.scoresheetId());
            return;
        }
        var judgeUserId = scoresheet.getFilledByJudgeUserId();
        if (judgeUserId == null) {
            log.info("Reverted scoresheet {} was never filled — no judge to notify",
                    event.scoresheetId());
            return;
        }
        var entry = entryService.findEntryById(event.entryId());
        var division = competitionService.findDivisionById(entry.getDivisionId());
        var competition = competitionService.findCompetitionById(division.getCompetitionId());
        var judge = userService.findById(judgeUserId);

        var locale = LanguageMapping.resolveLocale(judge.getPreferredLanguage(), judge.getCountry());
        var link = jwtMagicLinkService.generateLink(judge.getEmail(), LINK_VALIDITY);
        emailService.sendScoresheetReverted(judge.getEmail(), entry.getEntryCode(),
                competition.getName(), division.getName(), link, locale);
        log.info("Sent scoresheet-reverted email to {} for entry {}",
                judge.getEmail(), entry.getEntryCode());
    }

    @ApplicationModuleListener
    public void on(MedalRoundActivatedEvent event) {
        var tables = judgingRoundRepository.findByDivisionCategoryId(event.divisionCategoryId());
        var judgeUserIds = new LinkedHashSet<UUID>();
        for (var table : tables) {
            for (var assignment : table.getAssignments()) {
                judgeUserIds.add(assignment.getJudgeUserId());
            }
        }
        if (judgeUserIds.isEmpty()) {
            log.info("MedalRoundActivatedEvent for category {} — no judges assigned, none notified",
                    event.divisionCategoryId());
            return;
        }
        var division = competitionService.findDivisionById(event.divisionId());
        var competition = competitionService.findCompetitionById(division.getCompetitionId());
        var category = competitionService.findDivisionCategoryById(event.divisionCategoryId());

        for (var judgeUserId : judgeUserIds) {
            var judge = userService.findById(judgeUserId);
            var locale = LanguageMapping.resolveLocale(judge.getPreferredLanguage(), judge.getCountry());
            var categoryLabel = category.getCode() + " — " + category.getName(locale);
            var link = jwtMagicLinkService.generateLink(judge.getEmail(), LINK_VALIDITY);
            emailService.sendMedalRoundReady(judge.getEmail(), categoryLabel,
                    competition.getName(), division.getName(), link, locale);
            log.info("Sent medal-round-ready email to {} for category {}",
                    judge.getEmail(), categoryLabel);
        }
    }
}
