package app.meads.judging.internal;

import app.meads.entry.EntryReceivedEvent;
import app.meads.entry.EntryService;
import app.meads.judging.JudgingRoundStatus;
import app.meads.judging.JudgingService;
import app.meads.judging.MedalRoundMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Enforces the force-all invariant for SCORE_BASED medal rounds: every
 * RECEIVED entry in the category must be assigned to the medal round, and
 * nothing else. Listens to {@link EntryReceivedEvent}, which fires on every
 * transition that could change an entry's medal-round eligibility — status
 * → RECEIVED (add path), status leaving RECEIVED (zombie cleanup path), or
 * finalCategoryId set / changed on a RECEIVED entry.
 *
 * <p>Sync handles both directions: adding newly-eligible entries and
 * removing zombies (withdrawn / reverted). Entries with a SUBMITTED
 * scoresheet on the round are not auto-removed (committed work).
 */
@Slf4j
@Component
public class MedalRoundAutoSyncListener {

    private final EntryService entryService;
    private final JudgingService judgingService;

    MedalRoundAutoSyncListener(EntryService entryService, JudgingService judgingService) {
        this.entryService = entryService;
        this.judgingService = judgingService;
    }

    @ApplicationModuleListener
    public void on(EntryReceivedEvent event) {
        var entry = entryService.findById(event.entryId()).orElse(null);
        if (entry == null || entry.getFinalCategoryId() == null) {
            return;
        }
        var roundOpt = judgingService.findMedalRoundByCategoryId(entry.getFinalCategoryId());
        if (roundOpt.isEmpty()) {
            return;
        }
        var round = roundOpt.get();
        if (round.getMedalMode() != MedalRoundMode.SCORE_BASED
                || round.getStatus() == JudgingRoundStatus.COMPLETE) {
            return;
        }
        try {
            judgingService.syncScoreBasedMedalRoundEntries(
                    round.getId(), event.triggeredByUserId());
            log.debug("Auto-synced SCORE_BASED medal round {} after entry {} change",
                    round.getId(), event.entryId());
        } catch (Exception ex) {
            log.warn("Auto-sync of SCORE_BASED medal round {} for entry {} failed: {}",
                    round.getId(), event.entryId(), ex.getMessage());
        }
    }
}
