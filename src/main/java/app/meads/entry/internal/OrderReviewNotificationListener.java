package app.meads.entry.internal;

import app.meads.competition.CompetitionService;
import app.meads.entry.OrderRequiresReviewEvent;
import app.meads.identity.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderReviewNotificationListener {

    private final CompetitionService competitionService;
    private final EmailService emailService;

    OrderReviewNotificationListener(CompetitionService competitionService,
                                     EmailService emailService) {
        this.competitionService = competitionService;
        this.emailService = emailService;
    }

    @ApplicationModuleListener
    public void on(OrderRequiresReviewEvent event) {
        var divisionNames = String.join(", ", event.affectedDivisionNames());
        for (var competitionId : event.affectedCompetitionIds()) {
            var competition = competitionService.findCompetitionById(competitionId);
            var adminEmails = competitionService.findAdminEmailsByCompetitionId(competitionId);
            for (var email : adminEmails) {
                emailService.sendOrderReviewAlert(
                        email, competition.getName(),
                        event.jumpsellerOrderId(), event.customerName(),
                        divisionNames);
                log.info("Sent order review alert to {} for competition {}",
                        email, competition.getName());
            }
        }
    }
}
