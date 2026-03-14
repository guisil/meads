package app.meads.competition;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CompetitionRole {
    JUDGE("Judge"),
    STEWARD("Steward"),
    ENTRANT("Entrant"),
    ADMIN("Admin");

    private final String displayName;

    public boolean requiresAccessCode() {
        return this == JUDGE || this == STEWARD;
    }
}
