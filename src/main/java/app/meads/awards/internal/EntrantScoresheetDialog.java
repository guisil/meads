package app.meads.awards.internal;

import app.meads.awards.AnonymizedScoresheetView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Read-only entrant-facing scoresheet dialog, opened from the
 * {@link MyResultsView} results grid. Shows the entrant their own
 * <strong>prefixed entry number</strong> (never the anonymized judging code),
 * each criterion's score <em>and per-criterion comment</em>, the total,
 * whether the entry <strong>advanced to the medal round</strong>, and the
 * overall comments.
 *
 * <p>Deliberately omits any judge identity or "Judge N" labelling — entrants
 * know judges evaluated their mead; the individual judge is not surfaced.
 * Multiple scoresheets are stacked as separate cards.
 */
class EntrantScoresheetDialog extends Dialog {

    EntrantScoresheetDialog(AnonymizedScoresheetView view) {
        setHeaderTitle(view.entryNumber() + " — " + view.meadName());
        setId("entrant-scoresheet-dialog");
        setWidth("560px");

        var layout = new VerticalLayout();
        layout.setPadding(false);
        layout.add(new Paragraph(getTranslation("my-scoresheet.category") + ": "
                + view.categoryCode() + " — " + view.categoryName()));

        for (var sheet : view.scoresheets()) {
            layout.add(buildScoresheetCard(sheet));
        }

        add(layout);
        getFooter().add(new Button(getTranslation("button.close"), e -> close()));
    }

    private VerticalLayout buildScoresheetCard(AnonymizedScoresheetView.AnonymizedScoresheet sheet) {
        var card = new VerticalLayout();
        card.setPadding(true);
        card.setSpacing(false);
        card.getStyle().set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("margin-bottom", "var(--lumo-space-s)");

        if (sheet.advanced()) {
            var icon = VaadinIcon.CHECK.create();
            icon.setColor("var(--lumo-success-color)");
            var advanced = new Span(icon, new Span(" " + getTranslation("my-scoresheet.advanced")));
            advanced.setId("entrant-scoresheet-advanced-" + sheet.judgeOrdinal());
            card.add(advanced);
        }

        if (sheet.commentLanguage() != null) {
            card.add(new Paragraph(getTranslation("my-scoresheet.comment-language") + ": "
                    + sheet.commentLanguage()));
        }

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
            card.add(line);
        }

        card.add(new Span(getTranslation("my-scoresheet.total") + ": "
                + (sheet.totalScore() != null ? sheet.totalScore() : "—")));

        if (sheet.overallComments() != null && !sheet.overallComments().isBlank()) {
            card.add(new H3(getTranslation("my-scoresheet.overall-comments")));
            card.add(new Paragraph(sheet.overallComments()));
        }
        return card;
    }
}
