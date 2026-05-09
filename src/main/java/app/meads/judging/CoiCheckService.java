package app.meads.judging;

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
}
