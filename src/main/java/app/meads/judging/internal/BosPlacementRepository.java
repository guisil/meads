package app.meads.judging.internal;

import app.meads.judging.BosPlacement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BosPlacementRepository extends JpaRepository<BosPlacement, UUID> {

    List<BosPlacement> findByDivisionIdOrderByPlace(UUID divisionId);

    Optional<BosPlacement> findByEntryId(UUID entryId);
}
