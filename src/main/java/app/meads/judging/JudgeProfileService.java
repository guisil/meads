package app.meads.judging;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface JudgeProfileService {

    JudgeProfile ensureProfileForJudge(UUID userId);

    JudgeProfile createOrUpdate(UUID userId,
                                Set<Certification> certifications,
                                String qualificationDetails,
                                UUID requestingUserId);

    Optional<JudgeProfile> findByUserId(UUID userId);

    void updatePreferredCommentLanguage(UUID userId, String languageCode);

    void delete(UUID userId, UUID adminUserId);
}
