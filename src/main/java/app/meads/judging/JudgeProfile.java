package app.meads.judging;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "judge_profiles")
@Getter
public class JudgeProfile {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "judge_profile_certifications",
            joinColumns = @JoinColumn(name = "judge_profile_id"))
    @Column(name = "certification", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Certification> certifications = new HashSet<>();

    @Column(name = "qualification_details", length = 200)
    private String qualificationDetails;

    @Column(name = "preferred_comment_language", length = 5)
    private String preferredCommentLanguage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected JudgeProfile() {
    }

    public JudgeProfile(UUID userId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
    }

    public Set<Certification> getCertifications() {
        return Collections.unmodifiableSet(certifications);
    }

    public void updateCertifications(Set<Certification> certifications) {
        this.certifications.clear();
        if (certifications != null && !certifications.isEmpty()) {
            this.certifications.addAll(EnumSet.copyOf(certifications));
        }
    }

    public void updateQualificationDetails(String details) {
        this.qualificationDetails = StringUtils.hasText(details) ? details.trim() : null;
    }

    public void updatePreferredCommentLanguage(String code) {
        this.preferredCommentLanguage = StringUtils.hasText(code) ? code : null;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
