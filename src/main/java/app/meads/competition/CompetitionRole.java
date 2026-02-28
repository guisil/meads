package app.meads.competition;

public enum CompetitionRole {
    JUDGE,
    STEWARD,
    ENTRANT,
    COMPETITION_ADMIN;

    public boolean requiresAccessCode() {
        return this == JUDGE || this == STEWARD;
    }
}
