package app.meads.entry.internal;

import app.meads.competition.CompetitionService;
import app.meads.entry.CreditsAwardedEvent;
import app.meads.identity.EmailService;
import app.meads.identity.JwtMagicLinkService;
import app.meads.identity.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class CreditNotificationListener {

    private static final Duration LINK_VALIDITY = Duration.ofDays(7);

    private final CompetitionService competitionService;
    private final UserService userService;
    private final EmailService emailService;
    private final JwtMagicLinkService jwtMagicLinkService;

    CreditNotificationListener(CompetitionService competitionService,
                                UserService userService,
                                EmailService emailService,
                                JwtMagicLinkService jwtMagicLinkService) {
        this.competitionService = competitionService;
        this.userService = userService;
        this.emailService = emailService;
        this.jwtMagicLinkService = jwtMagicLinkService;
    }

    @ApplicationModuleListener
    public void on(CreditsAwardedEvent event) {
        var division = competitionService.findDivisionById(event.divisionId());
        var competition = competitionService.findCompetitionById(division.getCompetitionId());
        var user = userService.findById(event.userId());

        var loginLink = jwtMagicLinkService.generateLink(user.getEmail(), LINK_VALIDITY);

        emailService.sendCreditNotification(
                user.getEmail(),
                event.amount(), division.getName(),
                competition.getName(), loginLink,
                competition.getContactEmail());
        log.info("Sent credit notification to {} for {} credits in {}",
                user.getEmail(), event.amount(), division.getName());
    }
}
