package app.meads.competition;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "competition_documents",
        uniqueConstraints = @UniqueConstraint(columnNames = {"competition_id", "name"}))
@Getter
public class CompetitionDocument {

    private static final int MAX_PDF_SIZE = 10 * 1024 * 1024;

    @Id
    private UUID id;

    @Column(name = "competition_id", nullable = false)
    private UUID competitionId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType type;

    @Basic(fetch = FetchType.LAZY)
    @Column(length = 10485760)
    private byte[] data;

    @Column(name = "content_type", length = 100)
    private String contentType;

    private String url;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    protected CompetitionDocument() {} // JPA

    private CompetitionDocument(UUID competitionId, String name, DocumentType type,
                                byte[] data, String contentType, String url, int displayOrder) {
        validateName(name);
        this.id = UUID.randomUUID();
        this.competitionId = competitionId;
        this.name = name;
        this.type = type;
        this.data = data;
        this.contentType = contentType;
        this.url = url;
        this.displayOrder = displayOrder;
    }

    public static CompetitionDocument createPdf(UUID competitionId, String name,
                                                byte[] data, String contentType, int displayOrder) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("PDF data must not be empty");
        }
        if (data.length > MAX_PDF_SIZE) {
            throw new IllegalArgumentException("PDF must not exceed 10 MB");
        }
        if (!"application/pdf".equals(contentType)) {
            throw new IllegalArgumentException("Content type must be application/pdf");
        }
        return new CompetitionDocument(competitionId, name, DocumentType.PDF,
                data, contentType, null, displayOrder);
    }

    public static CompetitionDocument createLink(UUID competitionId, String name,
                                                 String url, int displayOrder) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL must not be blank");
        }
        return new CompetitionDocument(competitionId, name, DocumentType.LINK,
                null, null, url, displayOrder);
    }

    public void updateName(String name) {
        validateName(name);
        this.name = name;
    }

    public void updateDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Document name must not be blank");
        }
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
