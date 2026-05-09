package app.meads.judging.internal;

import app.meads.judging.JudgingTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface JudgingTableRepository extends JpaRepository<JudgingTable, UUID> {

    List<JudgingTable> findByJudgingId(UUID judgingId);

    @Query("SELECT t FROM JudgingTable t JOIN t.assignments a WHERE a.judgeUserId = :judgeUserId")
    List<JudgingTable> findByJudgeUserId(UUID judgeUserId);

    @Query("SELECT COUNT(a) > 0 FROM JudgingTable t JOIN t.assignments a WHERE a.judgeUserId = :judgeUserId")
    boolean existsAssignmentByJudgeUserId(UUID judgeUserId);

    boolean existsByJudgingId(UUID judgingId);

    @Query("SELECT COUNT(t) > 0 FROM JudgingTable t WHERE t.judgingId = :judgingId AND t.status <> app.meads.judging.JudgingTableStatus.NOT_STARTED")
    boolean existsStartedByJudgingId(UUID judgingId);
}
