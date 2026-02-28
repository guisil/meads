package app.meads.competition;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "events")
public class Event {

    private static final int MAX_LOGO_SIZE = 512 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg");

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    private String location;

    @Basic(fetch = FetchType.LAZY)
    @Column(length = 524288)
    private byte[] logo;

    @Column(name = "logo_content_type", length = 100)
    private String logoContentType;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    protected Event() {} // JPA

    public Event(UUID id, String name, LocalDate startDate, LocalDate endDate, String location) {
        validateDateOrdering(startDate, endDate);
        this.id = id;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.location = location;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void updateDetails(String name, LocalDate startDate, LocalDate endDate, String location) {
        validateDateOrdering(startDate, endDate);
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.location = location;
    }

    private static void validateDateOrdering(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must not be before start date");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getLocation() {
        return location;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void updateLogo(byte[] logo, String contentType) {
        if (logo == null) {
            this.logo = null;
            this.logoContentType = null;
            return;
        }
        if (logo.length > MAX_LOGO_SIZE) {
            throw new IllegalArgumentException("Logo must not exceed 512 KB");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Logo content type must be image/png or image/jpeg");
        }
        this.logo = logo;
        this.logoContentType = contentType;
    }

    public boolean hasLogo() {
        return logo != null;
    }

    public byte[] getLogo() {
        return logo;
    }

    public String getLogoContentType() {
        return logoContentType;
    }
}
