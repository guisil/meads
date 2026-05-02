package app.meads.competition;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@Getter
@RequiredArgsConstructor
public enum DivisionStatus {
    DRAFT("Draft", "badge-draft"),
    REGISTRATION_OPEN("Registration Open", "badge-registration-open"),
    REGISTRATION_CLOSED("Registration Closed", "badge-registration-closed"),
    JUDGING("Judging", "badge-judging"),
    DELIBERATION("Deliberation", "badge-deliberation"),
    RESULTS_PUBLISHED("Results Published", "badge-results-published");

    private final String displayName;
    private final String badgeCssClass;

    public boolean allowsCategoryModification() {
        return this == DRAFT || this == REGISTRATION_OPEN;
    }

    public boolean allowsRegistrationActions() {
        return this == DRAFT || this == REGISTRATION_OPEN;
    }

    public boolean allowsJudgingCategoryManagement() {
        return ordinal() >= REGISTRATION_CLOSED.ordinal();
    }

    public Optional<DivisionStatus> next() {
        var values = values();
        int nextOrdinal = ordinal() + 1;
        if (nextOrdinal >= values.length) {
            return Optional.empty();
        }
        return Optional.of(values[nextOrdinal]);
    }

    public Optional<DivisionStatus> previous() {
        int prevOrdinal = ordinal() - 1;
        if (prevOrdinal < 0) {
            return Optional.empty();
        }
        return Optional.of(values()[prevOrdinal]);
    }
}
