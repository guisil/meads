package app.meads.awards.internal;

import app.meads.BusinessRuleException;
import app.meads.MainLayout;
import app.meads.awards.AwardsService;
import app.meads.awards.PublicResultsView;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.i18n.LocaleChangeEvent;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.ZoneId;

@Route(value = "competitions/:compShortName/divisions/:divShortName/results", layout = MainLayout.class)
@AnonymousAllowed
public class AwardsPublicResultsView extends VerticalLayout
        implements BeforeEnterObserver, LocaleChangeObserver {

    private final AwardsService awardsService;
    private String compShortName;
    private String divShortName;

    public AwardsPublicResultsView(AwardsService awardsService) {
        this.awardsService = awardsService;
        setSizeFull();
        setPadding(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        compShortName = event.getRouteParameters().get("compShortName").orElse(null);
        divShortName = event.getRouteParameters().get("divShortName").orElse(null);
        if (compShortName == null || divShortName == null) {
            event.forwardTo("");
            return;
        }
        try {
            var view = awardsService.getPublicResults(compShortName, divShortName);
            render(view);
        } catch (BusinessRuleException e) {
            event.forwardTo("");
        }
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        if (compShortName != null && divShortName != null) {
            try {
                render(awardsService.getPublicResults(compShortName, divShortName));
            } catch (BusinessRuleException e) {
                // Ignore — beforeEnter will have already forwarded on first render.
            }
        }
    }

    private void render(PublicResultsView view) {
        removeAll();
        var heading = new H2(getTranslation("awards.public.title",
                view.competitionName(), view.divisionName()));
        heading.setId("awards-public-heading");
        add(heading);

        if (!view.bosLeaderboard().isEmpty()) {
            add(new H3(getTranslation("awards.public.bos.heading")));
            var bosGrid = new Grid<PublicResultsView.PublicBosRow>();
            bosGrid.setId("awards-public-bos");
            bosGrid.addColumn(PublicResultsView.PublicBosRow::place)
                    .setHeader(getTranslation("awards.public.bos.place"));
            bosGrid.addColumn(PublicResultsView.PublicBosRow::meadName)
                    .setHeader(getTranslation("awards.public.bos.mead-name"));
            bosGrid.addColumn(PublicResultsView.PublicBosRow::meaderyName)
                    .setHeader(getTranslation("awards.public.bos.meadery-name"));
            bosGrid.setItems(view.bosLeaderboard());
            bosGrid.setAllRowsVisible(true);
            add(bosGrid);
        }

        for (var section : view.categories()) {
            var sectionHeading = new H3(section.categoryCode() + " — " + section.categoryName());
            add(sectionHeading);
            renderMedalGroup(section.golds(), "awards.public.medal.gold");
            renderMedalGroup(section.silvers(), "awards.public.medal.silver");
            renderMedalGroup(section.bronzes(), "awards.public.medal.bronze");
        }

        if (view.hasMultiplePublications() && view.lastUpdatedAt() != null) {
            var fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                    .withLocale(getLocale()).withZone(ZoneId.systemDefault());
            var footer = new Paragraph(getTranslation("awards.public.last-updated",
                    fmt.format(view.lastUpdatedAt())));
            footer.setId("awards-public-last-updated");
            add(footer);
        }
    }

    private void renderMedalGroup(java.util.List<PublicResultsView.PublicMedalRow> rows, String labelKey) {
        if (rows.isEmpty()) {
            return;
        }
        var label = new Span(getTranslation(labelKey));
        label.getStyle().set("font-weight", "bold");
        add(label);
        for (var row : rows) {
            add(new Paragraph(row.meadName() + " — " + row.meaderyName()));
        }
    }
}
