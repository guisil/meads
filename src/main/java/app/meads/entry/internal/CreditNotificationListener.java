package app.meads.entry.internal;

import app.meads.competition.CompetitionService;
import app.meads.entry.CreditsAwardedEvent;
import app.meads.identity.EmailService;
import app.meads.identity.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CreditNotificationListener {

    private final CompetitionService competitionService;
    private final UserService userService;
    private final EmailService emailService;

    CreditNotificationListener(CompetitionService competitionService,
                                UserService userService,
                                EmailService emailService) {
        this.competitionService = competitionService;
        this.userService = userService;
        this.emailService = emailService;
    }

    @ApplicationModuleListener
    public void on(CreditsAwardedEvent event) {
        var division = competitionService.findDivisionById(event.divisionId());
        var competition = competitionService.findCompetitionById(division.getCompetitionId());
        var user = userService.findById(event.userId());

        var entriesUrl = "/competitions/" + competition.getShortName()
                + "/divisions/" + division.getShortName() + "/my-entries";

        emailService.sendCreditNotification(
                user.getEmail(),
                event.amount(), division.getName(),
                competition.getName(), entriesUrl,
                competition.getContactEmail());
        log.info("Sent credit notification to {} for {} credits in {}",
                user.getEmail(), event.amount(), division.getName());
    }
}
