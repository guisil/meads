package app.meads.judging.internal;

import app.meads.judging.Scoresheet;
import app.meads.judging.ScoresheetStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScoresheetRepository extends JpaRepository<Scoresheet, UUID> {

    Optional<Scoresheet> findByEntryId(UUID entryId);

    List<Scoresheet> findByTableId(UUID tableId);

    long countByTableIdAndStatus(UUID tableId, ScoresheetStatus status);
}
