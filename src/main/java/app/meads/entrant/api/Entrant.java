package app.meads.entrant.api;

import java.util.UUID;

public record Entrant(
    UUID id,
    String email,
    String name,
    String phone,
    String addressLine1,
    String addressLine2,
    String city,
    String stateProvince,
    String postalCode,
    String country
) {}
