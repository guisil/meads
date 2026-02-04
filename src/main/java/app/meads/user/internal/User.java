package app.meads.user.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Core user identity.
 * Created when webhook fires or when someone is added as admin/judge.
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User {

  @Id
  @Builder.Default
  @Column(name = "id", nullable = false)
  private UUID id = UUID.randomUUID();

  @Column(name = "email", nullable = false, unique = true)
  private String email;

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "display_country")
  private String displayCountry;

  @Column(name = "is_system_admin", nullable = false)
  @Builder.Default
  private Boolean isSystemAdmin = false;

  @Column(name = "created_at", nullable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  @Builder.Default
  private Instant updatedAt = Instant.now();
}
