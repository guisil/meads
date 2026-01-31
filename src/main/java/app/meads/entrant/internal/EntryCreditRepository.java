package app.meads.entrant.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface EntryCreditRepository extends JpaRepository<EntryCreditEntity, UUID> {

    List<EntryCreditEntity> findByEntrantId(UUID entrantId);

    Optional<EntryCreditEntity> findByExternalOrderIdAndExternalSource(
        String externalOrderId, String externalSource);

    @Query("SELECT DISTINCT c.competitionId FROM EntryCreditEntity c WHERE c.entrant.id = :entrantId")
    List<UUID> findDistinctCompetitionIdsByEntrantId(@Param("entrantId") UUID entrantId);

    boolean existsByEntrantIdAndCompetitionId(UUID entrantId, UUID competitionId);
}
