package app.meads.user.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Data transfer object for User information.
 * Exposed to other modules via the user module's public API.
 */
public record UserDto(
    UUID id,
    String email,
    String displayName,
    String displayCountry,
    Boolean isSystemAdmin,
    Instant createdAt,
    Instant updatedAt
) {}
