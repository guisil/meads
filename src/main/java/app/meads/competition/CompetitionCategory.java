package app.meads.competition;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "competition_categories",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"competition_id", "code"}))
@Getter
public class CompetitionCategory {

    @Id
    private UUID id;

    @Column(name = "competition_id", nullable = false)
    private UUID competitionId;

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

    @Column(nullable = false)
    private Instant createdAt;

    protected CompetitionCategory() {} // JPA

    public CompetitionCategory(UUID competitionId, UUID catalogCategoryId,
                               String code, String name, String description,
                               UUID parentId, int sortOrder) {
        this.id = UUID.randomUUID();
        this.competitionId = competitionId;
        this.catalogCategoryId = catalogCategoryId;
        this.code = code;
        this.name = name;
        this.description = description;
        this.parentId = parentId;
        this.sortOrder = sortOrder;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
