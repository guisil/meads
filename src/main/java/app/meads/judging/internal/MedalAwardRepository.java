package app.meads.judging.internal;

import app.meads.judging.MedalAward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedalAwardRepository extends JpaRepository<MedalAward, UUID> {

    Optional<MedalAward> findByEntryId(UUID entryId);

    List<MedalAward> findByDivisionId(UUID divisionId);

    List<MedalAward> findByFinalCategoryId(UUID finalCategoryId);
}
