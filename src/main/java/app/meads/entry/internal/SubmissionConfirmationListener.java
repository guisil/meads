package app.meads.entry.internal;

import app.meads.LanguageMapping;
import app.meads.competition.CompetitionService;
import app.meads.entry.EntriesSubmittedEvent;
import app.meads.identity.EmailService;
import app.meads.identity.JwtMagicLinkService;
import app.meads.identity.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class SubmissionConfirmationListener {

    private static final Duration LINK_VALIDITY = Duration.ofDays(7);

    private final CompetitionService competitionService;
    private final UserService userService;
    private final EmailService emailService;
    private final JwtMagicLinkService jwtMagicLinkService;
    private final MessageSource messageSource;

    SubmissionConfirmationListener(CompetitionService competitionService,
                                    UserService userService,
                                    EmailService emailService,
                                    JwtMagicLinkService jwtMagicLinkService,
                                    MessageSource messageSource) {
        this.competitionService = competitionService;
        this.userService = userService;
        this.emailService = emailService;
        this.jwtMagicLinkService = jwtMagicLinkService;
        this.messageSource = messageSource;
    }

    @ApplicationModuleListener
    public void on(EntriesSubmittedEvent event) {
        var division = competitionService.findDivisionById(event.divisionId());
        var competition = competitionService.findCompetitionById(division.getCompetitionId());
        var user = userService.findById(event.userId());

        var loginLink = jwtMagicLinkService.generateLink(user.getEmail(), LINK_VALIDITY);

        var locale = LanguageMapping.resolveLocale(user.getPreferredLanguage(), user.getCountry());
        var entryLines = event.entryDetails().stream()
                .map(d -> {
                    var key = "category." + d.categoryCode() + ".name";
                    var catName = messageSource.getMessage(key, null, d.categoryName(), locale);
                    return "#" + d.entryNumber() + " — " + d.meadName()
                            + " — " + d.categoryCode() + " " + catName;
                })
                .toList();
        emailService.sendSubmissionConfirmation(
                user.getEmail(), competition.getName(),
                division.getName(), entryLines, loginLink, locale);
        log.info("Sent submission confirmation to {} for {} entries in {}",
                user.getEmail(), event.entryDetails().size(), division.getName());
    }
}
