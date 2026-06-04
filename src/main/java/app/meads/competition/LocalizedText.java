package app.meads.competition;

/**
 * A localized name + description pair for a single language. Used to carry per-locale
 * category translations into and out of {@link DivisionCategory}. Either field may be
 * blank, in which case resolution falls back to the category's English base value.
 */
public record LocalizedText(String name, String description) {
}
