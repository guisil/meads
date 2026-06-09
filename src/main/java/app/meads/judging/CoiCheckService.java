package app.meads.judging;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoiCheckService {

    record CoiResult(boolean hardBlock, Optional<String> softWarningKey) {
        public static CoiResult blocking() {
            return new CoiResult(true, Optional.empty());
        }

        public static CoiResult warn(String key) {
            return new CoiResult(false, Optional.of(key));
        }

        public static CoiResult clear() {
            return new CoiResult(false, Optional.empty());
        }
    }

    CoiResult check(UUID judgeUserId, UUID entryId);

    /**
     * Declares a manual COI hard-blocking {@code judgeUserId} from judging
     * {@code entrantUserId}'s entries in the given competition.
     */
    void addManualCoi(@NotNull UUID competitionId, @NotNull UUID judgeUserId,
                      @NotNull UUID entrantUserId, @NotNull UUID adminUserId);

    void removeManualCoi(@NotNull UUID manualCoiId, @NotNull UUID adminUserId);

    List<ManualCoiView> findManualCois(@NotNull UUID competitionId);
}
