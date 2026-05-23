package app.meads.judging.internal;

import app.meads.judging.JudgingRound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface JudgingRoundRepository extends JpaRepository<JudgingRound, UUID> {

    List<JudgingRound> findByJudgingId(UUID judgingId);

    List<JudgingRound> findByDivisionCategoryId(UUID divisionCategoryId);

    @Query("SELECT t FROM JudgingRound t JOIN t.assignments a WHERE a.judgeUserId = :judgeUserId")
    List<JudgingRound> findByJudgeUserId(UUID judgeUserId);

    @Query("SELECT COUNT(a) > 0 FROM JudgingRound t JOIN t.assignments a WHERE a.judgeUserId = :judgeUserId")
    boolean existsAssignmentByJudgeUserId(UUID judgeUserId);

    @Query("SELECT COUNT(a) > 0 FROM JudgingRound t JOIN t.assignments a WHERE t.id = :roundId AND a.judgeUserId = :judgeUserId")
    boolean existsAssignmentByTableIdAndJudgeUserId(UUID roundId, UUID judgeUserId);

    boolean existsByJudgingId(UUID judgingId);

    @Query("SELECT COUNT(t) > 0 FROM JudgingRound t WHERE t.judgingId = :judgingId AND t.status <> app.meads.judging.JudgingRoundStatus.PENDING AND t.status <> app.meads.judging.JudgingRoundStatus.READY")
    boolean existsStartedByJudgingId(UUID judgingId);

    @Query("SELECT COUNT(a) FROM JudgingRound t JOIN t.assignments a WHERE t.id = :roundId")
    long countAssignmentsByTableId(UUID roundId);
}
