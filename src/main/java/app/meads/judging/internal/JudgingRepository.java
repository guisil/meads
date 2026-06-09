package app.meads.judging.internal;

import app.meads.judging.Judging;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JudgingRepository extends JpaRepository<Judging, UUID> {
    Optional<Judging> findByDivisionId(UUID divisionId);
}
