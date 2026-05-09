package app.meads.judging.internal;

import app.meads.judging.JudgeProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JudgeProfileRepository extends JpaRepository<JudgeProfile, UUID> {
    Optional<JudgeProfile> findByUserId(UUID userId);
}
