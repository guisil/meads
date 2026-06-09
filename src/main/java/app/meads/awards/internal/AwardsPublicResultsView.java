package app.meads.awards.internal;

import app.meads.BusinessRuleException;
import app.meads.MainLayout;
import app.meads.awards.AwardsService;
import app.meads.awards.PublicResultsView;
import app.meads.identity.UserService;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.UUID;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
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
    private final transient AuthenticationContext authenticationContext;
    private final UserService userService;
    private String compShortName;
    private String divShortName;

    public AwardsPublicResultsView(AwardsService awardsService,
                                   AuthenticationContext authenticationContext,
                                   UserService userService) {
        this.awardsService = awardsService;
        this.authenticationContext = authenticationContext;
        this.userService = userService;
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
            render(awardsService.getPublicResults(compShortName, divShortName, getLocale()), false);
        } catch (BusinessRuleException e) {
            // Not published yet — offer an admin preview if the current user is
            // authorized for the division; otherwise no leak (forward to root).
            if (!tryRenderPreview()) {
                event.forwardTo("");
            }
        }
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        if (compShortName != null && divShortName != null) {
            try {
                render(awardsService.getPublicResults(compShortName, divShortName, getLocale()), false);
            } catch (BusinessRuleException e) {
                tryRenderPreview();
            }
        }
    }

    /** Renders the admin preview when the current user is authorized; else no-op. */
    private boolean tryRenderPreview() {
        var userId = currentUserId();
        if (userId == null) {
            return false;
        }
        try {
            render(awardsService.getResultsPreview(compShortName, divShortName, getLocale(), userId), true);
            return true;
        } catch (BusinessRuleException e) {
            return false;
        }
    }

    private UUID currentUserId() {
        return authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(ud -> userService.findByEmail(ud.getUsername()).getId())
                .orElse(null);
    }

    private void render(PublicResultsView view, boolean preview) {
        removeAll();
        if (preview) {
            var banner = new Span(getTranslation("awards.preview.banner"));
            banner.setId("awards-preview-banner");
            banner.getStyle().set("background-color", "var(--lumo-warning-color-10pct)")
                    .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                    .set("border-radius", "var(--lumo-border-radius-m)")
                    .set("font-weight", "600");
            add(banner);
        }
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
            bosGrid.addColumn(PublicResultsView.PublicBosRow::producer)
                    .setHeader(getTranslation(view.meaderyRequired()
                            ? "awards.public.bos.meadery-name"
                            : "awards.public.bos.maker-name"));
            bosGrid.setItems(view.bosLeaderboard());
            bosGrid.setAllRowsVisible(true);
            add(bosGrid);
        }

        for (var section : view.categories()) {
            var sectionHeading = new H3(section.categoryCode() + " — " + section.categoryName());
            // Extra breathing room before each category so the page reads as
            // distinct blocks.
            sectionHeading.getStyle().set("margin-top", "var(--lumo-space-xl)");
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

        // The view is setSizeFull, so a CSS padding-bottom sits at the viewport
        // edge and overflowing content spills past it. A real trailing element
        // guarantees visible breathing room after the last category when scrolled.
        var bottomSpacer = new Div();
        bottomSpacer.setHeight("4rem");
        bottomSpacer.getStyle().set("flex", "0 0 auto");
        add(bottomSpacer);
    }

    private void renderMedalGroup(java.util.List<PublicResultsView.PublicMedalRow> rows, String labelKey) {
        if (rows.isEmpty()) {
            return;
        }
        // Keep the medal label tight against its entries (no inter-child spacing),
        // with only a small gap between medal groups.
        var group = new VerticalLayout();
        group.setPadding(false);
        group.setSpacing(false);
        group.getStyle().set("margin-top", "var(--lumo-space-s)");
        var label = new Span(getTranslation(labelKey));
        label.getStyle().set("font-weight", "bold");
        group.add(label);
        for (var row : rows) {
            var line = new Paragraph(row.meadName() + " — " + row.producer());
            line.getStyle().set("margin", "0");
            group.add(line);
        }
        add(group);
    }
}
