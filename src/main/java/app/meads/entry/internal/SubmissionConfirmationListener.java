package app.meads.entry.internal;

import app.meads.competition.CompetitionService;
import app.meads.entry.EntriesSubmittedEvent;
import app.meads.entry.EntryDetail;
import app.meads.identity.EmailService;
import app.meads.identity.JwtMagicLinkService;
import app.meads.identity.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class SubmissionConfirmationListener {

    private static final Duration LINK_VALIDITY = Duration.ofDays(7);

    private final CompetitionService competitionService;
    private final UserService userService;
    private final EmailService emailService;
    private final JwtMagicLinkService jwtMagicLinkService;

    SubmissionConfirmationListener(CompetitionService competitionService,
                                    UserService userService,
                                    EmailService emailService,
                                    JwtMagicLinkService jwtMagicLinkService) {
        this.competitionService = competitionService;
        this.userService = userService;
        this.emailService = emailService;
        this.jwtMagicLinkService = jwtMagicLinkService;
    }

    @ApplicationModuleListener
    public void on(EntriesSubmittedEvent event) {
        var division = competitionService.findDivisionById(event.divisionId());
        var competition = competitionService.findCompetitionById(division.getCompetitionId());
        var user = userService.findById(event.userId());

        var loginLink = jwtMagicLinkService.generateLink(user.getEmail(), LINK_VALIDITY);

        var entrySummary = formatEntrySummary(event.entryDetails());

        emailService.sendSubmissionConfirmation(
                user.getEmail(), competition.getName(),
                division.getName(), entrySummary, loginLink);
        log.info("Sent submission confirmation to {} for {} entries in {}",
                user.getEmail(), event.entryDetails().size(), division.getName());
    }

    private String formatEntrySummary(List<EntryDetail> details) {
        var sb = new StringBuilder();
        for (var detail : details) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append("#").append(detail.entryNumber())
                    .append(" — ").append(escapeHtml(detail.meadName()))
                    .append(" — ").append(escapeHtml(detail.categoryCode()))
                    .append(" ").append(escapeHtml(detail.categoryName()));
        }
        return sb.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
