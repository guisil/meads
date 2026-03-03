package app.meads.competition;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
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

    @Column(nullable = false)
    private Instant createdAt;

    protected DivisionCategory() {} // JPA

    public DivisionCategory(UUID divisionId, UUID catalogCategoryId,
                             String code, String name, String description,
                             UUID parentId, int sortOrder) {
        this.id = UUID.randomUUID();
        this.divisionId = divisionId;
        this.catalogCategoryId = catalogCategoryId;
        this.code = code;
        this.name = name;
        this.description = description;
        this.parentId = parentId;
        this.sortOrder = sortOrder;
    }

    public void updateDetails(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.catalogCategoryId = null;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
