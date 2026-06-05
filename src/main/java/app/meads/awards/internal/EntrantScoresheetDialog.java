package app.meads.awards.internal;

import app.meads.awards.AnonymizedScoresheetView;
import app.meads.judging.Medal;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Read-only entrant-facing scoresheet dialog, opened from the
 * {@link MyResultsView} results grid. Shows the entrant their own
 * <strong>prefixed entry number</strong> (never the anonymized judging code),
 * a prominent <strong>outcome banner</strong> (medal won + Best of Show
 * placement), each criterion's score <em>and per-criterion comment</em>, a
 * prominent <strong>total</strong>, whether the entry
 * <strong>advanced to the medal round</strong>, and the overall comments.
 *
 * <p>Deliberately omits any judge identity or "Judge N" labelling — entrants
 * know judges evaluated their mead; the individual judge is not surfaced.
 * Multiple scoresheets are stacked as separate cards.
 */
class EntrantScoresheetDialog extends Dialog {

    EntrantScoresheetDialog(AnonymizedScoresheetView view) {
        setId("entrant-scoresheet-dialog");
        setWidth("560px");

        // Header bar: a small competition logo pinned to the top-left corner, with
        // the entry-number + mead-name title beside it. Built as a custom header
        // layout (rather than setHeaderTitle) so the logo can precede the title.
        // The title takes the remaining column (flex:1 + min-width:0) so a long
        // mead name wraps as text to the RIGHT of the logo — its first line stays
        // on the same line as the logo instead of dropping below it.
        var headerBar = new HorizontalLayout();
        headerBar.setAlignItems(FlexComponent.Alignment.START);
        headerBar.setSpacing(true);
        headerBar.setWidthFull();
        if (view.competitionLogoDataUri() != null) {
            var logo = new Image(view.competitionLogoDataUri(), "");
            logo.setId("entrant-scoresheet-logo");
            logo.setHeight("32px");
            logo.getStyle().set("flex-shrink", "0");
            headerBar.add(logo);
        }
        var title = new Span(view.entryNumber() + " — " + view.meadName());
        title.setId("entrant-scoresheet-title");
        title.getStyle().set("font-weight", "600").set("font-size", "var(--lumo-font-size-l)")
                .set("flex", "1").set("min-width", "0").set("white-space", "normal");
        headerBar.add(title);
        getHeader().add(headerBar);

        var layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        layout.add(buildMeadDetails(view));

        var outcome = buildOutcomeBanner(view);
        if (outcome != null) {
            layout.add(outcome);
        }

        // "Advanced to the medal round" sits outside the comments card — below the
        // medal/BoS banner, above the scoresheet box. (entry_id is unique on
        // scoresheets, so an entry has a single sheet; surface it at entry level.)
        if (view.scoresheets().stream().anyMatch(AnonymizedScoresheetView.AnonymizedScoresheet::advanced)) {
            var icon = VaadinIcon.CHECK.create();
            icon.setColor("var(--lumo-success-color)");
            var advanced = new Span(icon, new Span(" " + getTranslation("my-scoresheet.advanced")));
            advanced.setId("entrant-scoresheet-advanced");
            advanced.getStyle().set("font-weight", "600")
                    .set("color", "var(--lumo-success-text-color)");
            layout.add(advanced);
        }

        for (var sheet : view.scoresheets()) {
            layout.add(buildScoresheetCard(sheet));
        }

        add(layout);
        getFooter().add(new Button(getTranslation("button.close"), e -> close()));
    }

    /**
     * Full mead details below the (header) mead name: category plus every
     * characteristic the entrant declared. The owning entrant sees their own
     * mead in full — there is no anonymity concern for one's own entry.
     */
    private VerticalLayout buildMeadDetails(AnonymizedScoresheetView view) {
        var details = new VerticalLayout();
        details.setId("entrant-scoresheet-mead-details");
        details.setPadding(false);
        details.setSpacing(false);
        details.add(attributeLine("my-scoresheet.category",
                view.categoryCode() + " — " + view.categoryName()));
        var md = view.meadDetails();
        if (md != null) {
            details.add(attributeLine("entries.view.sweetness",
                    getTranslation("entry.sweetness." + md.sweetness().name())));
            details.add(attributeLine("entries.view.strength",
                    getTranslation("entry.strength." + md.strength().name())));
            details.add(attributeLine("entries.view.abv",
                    md.abv() == null ? "—" : md.abv().toPlainString() + "%"));
            details.add(attributeLine("entries.view.carbonation",
                    getTranslation("entry.carbonation." + md.carbonation().name())));
            details.add(attributeLine("entries.view.honey", orDash(md.honeyVarieties())));
            details.add(attributeLine("entries.view.other-ingredients", orDash(md.otherIngredients())));
            details.add(attributeLine("entries.view.wood-aged",
                    getTranslation(md.woodAged()
                            ? "entries.view.wood-aged.yes" : "entries.view.wood-aged.no")));
            details.add(attributeLine("entries.view.wood-details", orDash(md.woodAgeingDetails())));
            details.add(attributeLine("entries.view.additional-info", orDash(md.additionalInformation())));
        }
        return details;
    }

    private Span attributeLine(String labelKey, String value) {
        var label = new Span(getTranslation(labelKey) + ": ");
        label.getStyle().set("font-weight", "600");
        return new Span(label, new Span(value));
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    /**
     * Entry-level outcome banner: the medal won and/or the Best of Show
     * placement, rendered prominently. Returns {@code null} when the entry won
     * neither (no banner shown).
     */
    private HorizontalLayout buildOutcomeBanner(AnonymizedScoresheetView view) {
        if (view.medal() == null && view.bosPlace() == null) {
            return null;
        }
        var banner = new HorizontalLayout();
        banner.setId("entrant-scoresheet-outcome");
        banner.setPadding(true);
        banner.setSpacing(true);
        banner.setAlignItems(FlexComponent.Alignment.CENTER);
        banner.getStyle().set("background-color", "var(--lumo-primary-color-10pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("flex-wrap", "wrap");

        if (view.medal() != null) {
            var medal = new Span(medalEmoji(view.medal()) + " "
                    + getTranslation("my-results.medal." + view.medal().name().toLowerCase()));
            medal.setId("entrant-scoresheet-medal");
            medal.getStyle().set("font-size", "var(--lumo-font-size-l)")
                    .set("font-weight", "700");
            banner.add(medal);
        }
        if (view.bosPlace() != null) {
            var bos = new Span("🏆 " + getTranslation("my-scoresheet.bos") + " — "
                    + getTranslation("my-scoresheet.bos.place", view.bosPlace()));
            bos.setId("entrant-scoresheet-bos");
            bos.getStyle().set("font-size", "var(--lumo-font-size-l)")
                    .set("font-weight", "700");
            banner.add(bos);
        }
        return banner;
    }

    private String medalEmoji(Medal medal) {
        return switch (medal) {
            case GOLD -> "🥇";   // 🥇
            case SILVER -> "🥈"; // 🥈
            case BRONZE -> "🥉"; // 🥉
        };
    }

    private VerticalLayout buildScoresheetCard(AnonymizedScoresheetView.AnonymizedScoresheet sheet) {
        var card = new VerticalLayout();
        card.setPadding(true);
        card.setSpacing(true);
        card.getStyle().set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)");

        if (sheet.commentLanguage() != null) {
            var lang = new Paragraph(getTranslation("my-scoresheet.comment-language") + ": "
                    + sheet.commentLanguage());
            lang.getStyle().set("margin", "0")
                    .set("color", "var(--lumo-secondary-text-color)");
            card.add(lang);
        }

        // Per-criterion scores + comments, each spaced from the next.
        var fields = new VerticalLayout();
        fields.setPadding(false);
        fields.setSpacing(true);
        for (var field : sheet.fieldScores()) {
            var line = new VerticalLayout();
            line.setPadding(false);
            line.setSpacing(false);
            var header = new Span(field.fieldName() + ": " + field.value() + " / " + field.maxValue());
            header.getStyle().set("font-weight", "600");
            line.add(header);
            if (field.comment() != null && !field.comment().isBlank()) {
                var comment = new Paragraph(field.comment());
                comment.getStyle().set("margin", "0").set("color", "var(--lumo-secondary-text-color)");
                line.add(comment);
            }
            fields.add(line);
        }
        card.add(fields);

        // Total — rendered prominently and separated from the criteria above.
        var total = new Span(getTranslation("my-scoresheet.total") + ": "
                + (sheet.totalScore() != null ? sheet.totalScore() : "—"));
        total.setId("entrant-scoresheet-total-" + sheet.judgeOrdinal());
        total.getStyle().set("font-size", "var(--lumo-font-size-xl)")
                .set("font-weight", "700")
                .set("padding-top", "var(--lumo-space-s)")
                .set("border-top", "1px solid var(--lumo-contrast-20pct)")
                .set("align-self", "stretch");
        card.add(total);

        if (sheet.overallComments() != null && !sheet.overallComments().isBlank()) {
            var heading = new H3(getTranslation("my-scoresheet.overall-comments"));
            heading.getStyle().set("margin-top", "var(--lumo-space-s)").set("margin-bottom", "0");
            card.add(heading);
            var overall = new Paragraph(sheet.overallComments());
            overall.getStyle().set("margin", "0");
            card.add(overall);
        }
        return card;
    }
}
