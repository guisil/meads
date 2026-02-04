package app.meads.user.internal;

import app.meads.user.TokenPurpose;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Magic link token for passwordless authentication.
 * Tokens are single-use and time-limited.
 */
@Entity
@Table(name = "access_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AccessToken {

  @Id
  @Builder.Default
  @Column(name = "id", nullable = false)
  private UUID id = UUID.randomUUID();

  /**
   * Hash of the token (not the raw token).
   * Only the hash is stored for security.
   */
  @Column(name = "token_hash", nullable = false, unique = true)
  private String tokenHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "purpose", nullable = false, length = 50)
  private TokenPurpose purpose;

  /**
   * Email address for token lookup.
   * Denormalized to allow lookup before user exists.
   */
  @Column(name = "email", nullable = false)
  private String email;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used", nullable = false)
  @Builder.Default
  private Boolean used = false;

  @Column(name = "created_at", nullable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  /**
   * The user this token belongs to.
   * Nullable if user doesn't exist yet.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  /**
   * Competition context for JUDGING_SESSION tokens.
   * Nullable for LOGIN tokens.
   * TODO: Add Competition entity and relationship
   */
  @Column(name = "competition_id")
  private UUID competitionId;
}
