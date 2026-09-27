package app.meads;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Looks up the scoresheet state of entries. Defined in root module so the entry
 * module's admin view can show judging progress without depending on judging.
 */
public interface EntryScoresheetChecker {

    /** Entries without a scoresheet are absent from the returned map. */
    Map<UUID, ScoresheetState> findScoresheetStates(Collection<UUID> entryIds);

    record ScoresheetState(UUID scoresheetId, boolean submitted) {}
}
