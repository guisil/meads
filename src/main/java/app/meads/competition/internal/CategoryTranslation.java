package app.meads.competition.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/**
 * Per-language name/description override for a {@link app.meads.competition.DivisionCategory}.
 * Owned by the parent aggregate via {@code @OneToMany + @JoinColumn} — has no division-category
 * FK field of its own (JPA manages it). The {@code locale} is an ISO 639-1 language code
 * (e.g. {@code "pt"}, {@code "es"}). English ({@code "en"}) is never stored here; the parent's
 * base {@code name}/{@code description} are the English values.
 */
@Entity
@Table(name = "division_category_translations")
@Getter
public class CategoryTranslation {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String locale;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    protected CategoryTranslation() { // JPA
    }

    public CategoryTranslation(String locale, String name, String description) {
        this.id = UUID.randomUUID();
        this.locale = locale;
        this.name = name;
        this.description = description;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
