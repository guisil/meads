package app.meads.judging.internal;

import app.meads.entry.EntryService;
import app.meads.identity.UserService;
import app.meads.judging.CoiCheckService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CoiCheckServiceImpl implements CoiCheckService {

    private static final String SIMILAR_MEADERY_KEY = "coi.warning.similar-meadery";

    private final UserService userService;
    private final EntryService entryService;

    CoiCheckServiceImpl(UserService userService, EntryService entryService) {
        this.userService = userService;
        this.entryService = entryService;
    }

    @Override
    public CoiResult check(UUID judgeUserId, UUID entryId) {
        var entry = entryService.findEntryById(entryId);
        if (entry.getUserId().equals(judgeUserId)) {
            return CoiResult.blocking();
        }
        var judge = userService.findById(judgeUserId);
        var entrant = userService.findById(entry.getUserId());
        if (MeaderyNameNormalizer.areSimilar(
                judge.getMeaderyName(), judge.getCountry(),
                entrant.getMeaderyName(), entrant.getCountry())) {
            return CoiResult.warn(SIMILAR_MEADERY_KEY);
        }
        return CoiResult.clear();
    }
}
