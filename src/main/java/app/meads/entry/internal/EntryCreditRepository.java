package app.meads.entry.internal;

import app.meads.entry.EntryCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EntryCreditRepository extends JpaRepository<EntryCredit, UUID> {
    List<EntryCredit> findByDivisionIdAndUserId(UUID divisionId, UUID userId);

    boolean existsByDivisionId(UUID divisionId);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM EntryCredit c WHERE c.divisionId = :divisionId AND c.userId = :userId")
    int sumAmountByDivisionIdAndUserId(UUID divisionId, UUID userId);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM EntryCredit c WHERE c.divisionId = :divisionId")
    int sumAmountByDivisionId(UUID divisionId);

    @Query("SELECT DISTINCT c.divisionId FROM EntryCredit c WHERE c.userId = :userId")
    List<UUID> findDistinctDivisionIdsByUserId(UUID userId);

    @Query("SELECT c FROM EntryCredit c WHERE c.userId = :userId AND c.divisionId IN "
            + "(SELECT d.id FROM Division d WHERE d.competitionId = :competitionId)")
    List<EntryCredit> findByUserIdAndCompetitionId(@Param("userId") UUID userId,
                                                    @Param("competitionId") UUID competitionId);
}
