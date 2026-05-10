package app.meads.judging;

import jakarta.validation.constraints.NotNull;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface JudgeProfileService {

    JudgeProfile ensureProfileForJudge(@NotNull UUID userId);

    JudgeProfile createOrUpdate(@NotNull UUID userId,
                                Set<Certification> certifications,
                                String qualificationDetails,
                                @NotNull UUID requestingUserId);

    Optional<JudgeProfile> findByUserId(@NotNull UUID userId);

    void updatePreferredCommentLanguage(@NotNull UUID userId, String languageCode);

    void delete(@NotNull UUID userId, @NotNull UUID adminUserId);
}
