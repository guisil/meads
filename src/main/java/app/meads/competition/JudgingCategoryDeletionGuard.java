package app.meads.competition;

import java.util.UUID;

public interface JudgingCategoryDeletionGuard {
    void checkDeletionAllowed(UUID categoryId);
}
