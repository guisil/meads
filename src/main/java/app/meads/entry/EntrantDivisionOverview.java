package app.meads.entry;

import app.meads.competition.DivisionStatus;

import java.util.UUID;

public record EntrantDivisionOverview(
        UUID competitionId, String competitionName, String competitionShortName,
        UUID divisionId, String divisionName, String divisionShortName,
        int creditBalance, long activeEntryCount, DivisionStatus status) {
}
