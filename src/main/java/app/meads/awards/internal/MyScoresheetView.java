package app.meads.awards.internal;

import app.meads.BusinessRuleException;
import app.meads.MainLayout;
import app.meads.awards.AnonymizedScoresheetView;
import app.meads.awards.AwardsService;
import app.meads.entry.Entry;
import app.meads.entry.EntryService;
import app.meads.identity.UserService;
import app.meads.judging.AnonymizationLevel;
import app.meads.judging.ScoresheetPdfService;
import app.meads.judging.ScoresheetService;
import app.meads.judging.ScoresheetStatus;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.ByteArrayInputStream;
import java.util.UUID;

@Route(value = "competitions/:compShortName/divisions/:divShortName/my-entries/:entryId/scoresheet",
        layout = MainLayout.class)
@PermitAll
public class MyScoresheetView extends VerticalLayout implements BeforeEnterObserver {

    private final AwardsService awardsService;
    private final ScoresheetService scoresheetService;
    private final ScoresheetPdfService scoresheetPdfService;
    private final EntryService entryService;
    private final UserService userService;
    private final transient AuthenticationContext authenticationContext;

    public MyScoresheetView(AwardsService awardsService,
                             ScoresheetService scoresheetService,
                             ScoresheetPdfService scoresheetPdfService,
                             EntryService entryService,
                             UserService userService,
                             AuthenticationContext authenticationContext) {
        this.awardsService = awardsService;
        this.scoresheetService = scoresheetService;
        this.scoresheetPdfService = scoresheetPdfService;
        this.entryService = entryService;
        this.userService = userService;
        this.authenticationContext = authenticationContext;
        setSizeFull();
        setPadding(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var compShortName = event.getRouteParameters().get("compShortName").orElse(null);
        var divShortName = event.getRouteParameters().get("divShortName").orElse(null);
        var entryIdStr = event.getRouteParameters().get("entryId").orElse(null);
        if (compShortName == null || divShortName == null || entryIdStr == null) {
            event.forwardTo("");
            return;
        }
        var currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            event.forwardTo("login");
            return;
        }
        Entry entry;
        try {
            entry = entryService.findEntryById(UUID.fromString(entryIdStr));
        } catch (BusinessRuleException | IllegalArgumentException e) {
            event.forwardTo("");
            return;
        }
        var sheets = scoresheetService.findByEntryIdOrderBySubmittedAtAsc(entry.getId());
        if (sheets.isEmpty()) {
            event.forwardTo("competitions/" + compShortName + "/divisions/" + divShortName + "/my-results");
            return;
        }
        try {
            // Use the first submitted scoresheet as the reference (auth + status check happens here)
            var firstSubmitted = sheets.stream()
                    .filter(s -> s.getStatus() == ScoresheetStatus.SUBMITTED)
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException("error.awards.scoresheet-not-found"));
            var view = awardsService.getAnonymizedScoresheet(firstSubmitted.getId(), currentUserId);
            render(view, currentUserId, compShortName, divShortName);
        } catch (BusinessRuleException e) {
            event.forwardTo("competitions/" + compShortName + "/divisions/" + divShortName + "/my-entries");
        }
    }

    private void render(AnonymizedScoresheetView view, UUID currentUserId,
                        String compShortName, String divShortName) {
        removeAll();
        add(new H2(view.entryNumber() + " — " + view.meadName()));
        add(new Paragraph(getTranslation("my-scoresheet.category") + ": "
                + view.categoryCode() + " — " + view.categoryName()));

        for (var sheet : view.scoresheets()) {
            var card = new VerticalLayout();
            card.setPadding(true);
            card.getStyle().set("border", "1px solid #ddd").set("border-radius", "8px")
                    .set("margin-bottom", "12px");
            card.add(new H3(getTranslation("my-scoresheet.judge-ordinal", sheet.judgeOrdinal())));
            if (sheet.commentLanguage() != null) {
                card.add(new Paragraph(getTranslation("my-scoresheet.comment-language") + ": "
                        + sheet.commentLanguage()));
            }
            for (var field : sheet.fieldScores()) {
                card.add(new Paragraph(field.fieldName() + ": " + field.value() + " / " + field.maxValue()));
            }
            card.add(new Span(getTranslation("my-scoresheet.total") + ": "
                    + (sheet.totalScore() != null ? sheet.totalScore() : "—")));
            if (sheet.overallComments() != null && !sheet.overallComments().isBlank()) {
                card.add(new H3(getTranslation("my-scoresheet.overall-comments")));
                card.add(new Paragraph(sheet.overallComments()));
            }
            // PDF download button per scoresheet
            var pdfButton = createPdfButton(view.scoresheetId(), currentUserId);
            card.add(pdfButton);
            add(card);
        }

        var backLink = new Anchor(
                "competitions/" + compShortName + "/divisions/" + divShortName + "/my-results",
                getTranslation("my-scoresheet.back"));
        backLink.setId("my-scoresheet-back");
        add(backLink);
    }

    private Anchor createPdfButton(UUID scoresheetId, UUID userId) {
        var resource = new StreamResource("scoresheet-" + scoresheetId + ".pdf",
                () -> new ByteArrayInputStream(scoresheetPdfService.generatePdf(
                        scoresheetId, userId, AnonymizationLevel.ANONYMIZED, getLocale())));
        var anchor = new Anchor(resource, getTranslation("my-scoresheet.download-pdf"));
        anchor.setId("my-scoresheet-download-pdf");
        anchor.getElement().setAttribute("download", true);
        return anchor;
    }

    private UUID getCurrentUserId() {
        return authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(u -> userService.findByEmail(u.getUsername()).getId())
                .orElse(null);
    }
}
