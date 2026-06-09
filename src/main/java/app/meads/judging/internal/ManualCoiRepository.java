package app.meads.judging.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ManualCoiRepository extends JpaRepository<ManualCoi, UUID> {

    List<ManualCoi> findByCompetitionId(UUID competitionId);

    boolean existsByCompetitionIdAndJudgeUserIdAndEntrantUserId(
            UUID competitionId, UUID judgeUserId, UUID entrantUserId);
}
