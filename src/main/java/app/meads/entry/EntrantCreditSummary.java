package app.meads.entry;

import java.util.UUID;

public record EntrantCreditSummary(UUID userId, String email, String name,
                                    int creditBalance, long entryCount) {}
