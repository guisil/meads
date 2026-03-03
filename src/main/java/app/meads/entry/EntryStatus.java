package app.meads.entry;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EntryStatus {
    DRAFT("Draft", "badge-draft"),
    SUBMITTED("Submitted", "badge-submitted"),
    RECEIVED("Received", "badge-received"),
    WITHDRAWN("Withdrawn", "badge-withdrawn");

    private final String displayName;
    private final String badgeCssClass;
}
