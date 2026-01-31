package app.meads.event.api;

import java.time.LocalDate;
import java.util.UUID;

public record MeadEvent(
    UUID id,
    String slug,
    String name,
    String description,
    LocalDate startDate,
    LocalDate endDate,
    boolean active
) {}
