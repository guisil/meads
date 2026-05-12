package app.meads.awards;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "publications")
@Getter
public class Publication {

    @Id
    private UUID id;

    @Column(name = "division_id", nullable = false)
    private UUID divisionId;

    @Column(nullable = false)
    private int version;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "published_by", nullable = false)
    private UUID publishedBy;

    @Column(columnDefinition = "TEXT")
    private String justification;

    @Column(name = "is_initial", nullable = false)
    private boolean initial;

    protected Publication() {
    }

    public Publication(UUID divisionId, UUID publishedBy) {
        this.id = UUID.randomUUID();
        this.divisionId = divisionId;
        this.version = 1;
        this.publishedBy = publishedBy;
        this.justification = null;
        this.initial = true;
    }

    public static Publication republish(UUID divisionId, int previousVersion,
                                        String justification, UUID publishedBy) {
        if (!StringUtils.hasText(justification)) {
            throw new IllegalArgumentException("justification is required for republish");
        }
        var p = new Publication();
        p.id = UUID.randomUUID();
        p.divisionId = divisionId;
        p.version = previousVersion + 1;
        p.publishedBy = publishedBy;
        p.justification = justification;
        p.initial = false;
        return p;
    }

    @PrePersist
    void onCreate() {
        if (publishedAt == null) {
            publishedAt = Instant.now();
        }
    }
}
