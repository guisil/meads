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
        return ordinal() >= REGISTRATION_CLOSED.ordinal() && ordinal() < DELIBERATION.ordinal();
    }

    /**
     * Entry-level mutations (admin create/edit, status advance/revert, mark-received, withdraw,
     * final-category assignment) are allowed only through JUDGING. From DELIBERATION onward the
     * results are being computed/published, so entries are locked (P21). To change a locked entry
     * an admin must first revert the division back to JUDGING.
     */
    public boolean allowsEntryMutations() {
        return ordinal() < DELIBERATION.ordinal();
    }

    public boolean isResultsFrozen() {
        return this == RESULTS_PUBLISHED;
    }

    /**
     * Bottle labels are only useful before judging starts (entrants print and attach them, then
     * ship). From JUDGING onward the bottles are already with judges, so entry-label downloads are
     * withdrawn. Entrants get their (scoresheet) PDFs through the results views once published.
     */
    public boolean allowsLabelDownloads() {
        return ordinal() < JUDGING.ordinal();
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
