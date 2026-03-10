package app.meads.competition;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "competitions")
@Getter
public class Competition {

    private static final int MAX_LOGO_SIZE = 2560 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg");
    static final String SHORT_NAME_PATTERN = "^[a-z0-9][a-z0-9-]*[a-z0-9]$";

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "short_name", nullable = false, unique = true)
    private String shortName;

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

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "shipping_address", columnDefinition = "TEXT")
    private String shippingAddress;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    protected Competition() {} // JPA

    public Competition(String name, String shortName, LocalDate startDate, LocalDate endDate, String location) {
        validateDateOrdering(startDate, endDate);
        validateShortName(shortName);
        this.id = UUID.randomUUID();
        this.name = name;
        this.shortName = shortName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.location = location;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void updateDetails(String name, String shortName, LocalDate startDate, LocalDate endDate, String location) {
        validateDateOrdering(startDate, endDate);
        validateShortName(shortName);
        this.name = name;
        this.shortName = shortName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.location = location;
    }

    private static void validateDateOrdering(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must not be before start date");
        }
    }

    static void validateShortName(String shortName) {
        if (shortName == null || !shortName.matches(SHORT_NAME_PATTERN)) {
            throw new IllegalArgumentException(
                    "Short name must be lowercase alphanumeric with hyphens, at least 2 characters");
        }
    }

    public void updateLogo(byte[] logo, String contentType) {
        if (logo == null) {
            this.logo = null;
            this.logoContentType = null;
            return;
        }
        if (logo.length > MAX_LOGO_SIZE) {
            throw new IllegalArgumentException("Logo must not exceed 2.5 MB");
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

    public void updateContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public void updateShippingDetails(String shippingAddress, String phoneNumber) {
        this.shippingAddress = shippingAddress;
        this.phoneNumber = phoneNumber;
    }
}
