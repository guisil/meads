package app.meads.judging.internal;

import app.meads.entry.EntryReceivedEvent;
import app.meads.judging.ScoresheetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Keeps scoresheets in sync with the entry lifecycle: when an entry transitions
 * to RECEIVED during JUDGING, ensure a DRAFT scoresheet exists on a matching
 * ROUND_1 table. {@link ScoresheetService#ensureScoresheetForEntry} is
 * idempotent and a no-op when no matching table exists.
 */
@Slf4j
@Component
class EntryReceivedScoresheetListener {

    private final ScoresheetService scoresheetService;

    EntryReceivedScoresheetListener(ScoresheetService scoresheetService) {
        this.scoresheetService = scoresheetService;
    }

    @ApplicationModuleListener
    void on(EntryReceivedEvent event) {
        log.debug("Entry {} marked RECEIVED — syncing scoresheet", event.entryId());
        scoresheetService.ensureScoresheetForEntry(event.entryId());
    }
}
