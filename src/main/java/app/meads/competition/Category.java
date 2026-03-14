package app.meads.competition;

import jakarta.persistence.*;
import lombok.Getter;

import java.util.UUID;

@Entity
@Table(name = "categories")
@Getter
public class Category {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "scoring_system", nullable = false)
    private ScoringSystem scoringSystem;

    @Column(name = "parent_code")
    private String parentCode;

    protected Category() {} // JPA

    public Category(String code, String name, String description,
                    ScoringSystem scoringSystem, String parentCode) {
        this.id = UUID.randomUUID();
        this.code = code;
        this.name = name;
        this.description = description;
        this.scoringSystem = scoringSystem;
        this.parentCode = parentCode;
    }
}
