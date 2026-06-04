package app.meads.competition;

import app.meads.competition.internal.CategoryTranslation;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "division_categories",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"division_id", "code"}))
@Getter
public class DivisionCategory {

    @Id
    private UUID id;

    @Column(name = "division_id", nullable = false)
    private UUID divisionId;

    @Column(name = "catalog_category_id")
    private UUID catalogCategoryId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoryScope scope;

    @Column(nullable = false)
    private Instant createdAt;

    @Getter(AccessLevel.NONE)
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "division_category_id", nullable = false)
    private List<CategoryTranslation> translations = new ArrayList<>();

    protected DivisionCategory() {} // JPA

    public DivisionCategory(UUID divisionId, UUID catalogCategoryId,
                             String code, String name, String description,
                             UUID parentId, int sortOrder) {
        this(divisionId, catalogCategoryId, code, name, description, parentId, sortOrder,
                CategoryScope.REGISTRATION);
    }

    public DivisionCategory(UUID divisionId, UUID catalogCategoryId,
                             String code, String name, String description,
                             UUID parentId, int sortOrder, CategoryScope scope) {
        this.id = UUID.randomUUID();
        this.divisionId = divisionId;
        this.catalogCategoryId = catalogCategoryId;
        this.code = code;
        this.name = name;
        this.description = description;
        this.parentId = parentId;
        this.sortOrder = sortOrder;
        this.scope = scope;
    }

    public void updateDetails(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.catalogCategoryId = null;
    }

    /** Localized name for the given locale's language, falling back to the English base name. */
    public String getName(Locale locale) {
        return translationFor(locale)
                .map(LocalizedText::name)
                .filter(StringUtils::hasText)
                .orElse(name);
    }

    /** Localized description for the given locale's language, falling back to the English base. */
    public String getDescription(Locale locale) {
        return translationFor(locale)
                .map(LocalizedText::description)
                .filter(StringUtils::hasText)
                .orElse(description);
    }

    /** Current translations keyed by ISO 639-1 language code (English base excluded). */
    public Map<String, LocalizedText> getTranslations() {
        var result = new LinkedHashMap<String, LocalizedText>();
        for (var t : translations) {
            result.put(t.getLocale(), new LocalizedText(t.getName(), t.getDescription()));
        }
        return result;
    }

    /**
     * Replaces all translations. Entries whose name and description are both blank are dropped;
     * blank individual fields are stored as empty and resolve to the English base.
     */
    public void setTranslations(Map<String, LocalizedText> newTranslations) {
        // Reconcile in place (update existing rows, add missing, drop orphans) rather than
        // clear()+re-add, so a same-locale row is updated — not delete+inserted, which would
        // transiently violate the UNIQUE(division_category_id, locale) constraint at flush.
        var desired = new LinkedHashMap<String, LocalizedText>();
        if (newTranslations != null) {
            newTranslations.forEach((language, text) -> {
                if (text != null
                        && (StringUtils.hasText(text.name()) || StringUtils.hasText(text.description()))) {
                    desired.put(language, text);
                }
            });
        }
        translations.removeIf(t -> !desired.containsKey(t.getLocale()));
        desired.forEach((language, text) -> {
            var name = text.name() == null ? "" : text.name();
            var description = text.description() == null ? "" : text.description();
            translations.stream()
                    .filter(t -> t.getLocale().equals(language))
                    .findFirst()
                    .ifPresentOrElse(
                            existing -> existing.update(name, description),
                            () -> translations.add(new CategoryTranslation(language, name, description)));
        });
    }

    private Optional<LocalizedText> translationFor(Locale locale) {
        if (locale == null) {
            return Optional.empty();
        }
        var language = locale.getLanguage();
        return translations.stream()
                .filter(t -> t.getLocale().equals(language))
                .findFirst()
                .map(t -> new LocalizedText(t.getName(), t.getDescription()));
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
