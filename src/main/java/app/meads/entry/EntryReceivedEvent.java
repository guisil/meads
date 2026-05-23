package app.meads.entry;

import java.util.UUID;

/**
 * Fired when an entry transitions to {@link EntryStatus#RECEIVED}. The judging
 * module listens to keep scoresheets in sync — if the division is already in
 * JUDGING and a ROUND_1 table exists for the entry's final category, a DRAFT
 * scoresheet is created automatically.
 */
public record EntryReceivedEvent(UUID entryId, UUID divisionId) {}
