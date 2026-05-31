package app.meads.judging.internal;

import com.vaadin.flow.component.html.Span;

/** Shared badge factories for the round drill-in headers. */
final class RoundBadges {

    private RoundBadges() {
    }

    /**
     * A neutral pill showing the (final) category being evaluated, meant to sit
     * next to the round-type badge. Deliberately styled with a neutral
     * contrast tint rather than a Lumo status colour (primary/success/contrast
     * are already used by the type badge), so it reads as an informational label,
     * not a status.
     *
     * @param code         the category code shown in the pill (e.g. {@code M1A})
     * @param name         the full category name, shown in the hover tooltip
     * @param tooltipLabel localized "Category" label prefixed to the tooltip
     */
    static Span categoryBadge(String code, String name, String tooltipLabel) {
        var badge = new Span(code);
        badge.getElement().setProperty("title", tooltipLabel + ": " + code + " — " + name);
        badge.getStyle()
                .set("background-color", "var(--lumo-contrast-10pct)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("font-weight", "500")
                .set("padding", "0.15em 0.6em")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("white-space", "nowrap");
        return badge;
    }
}
