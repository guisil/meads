package app.meads.event.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface CompetitionRepository extends JpaRepository<CompetitionEntity, UUID> {

    List<CompetitionEntity> findByMeadEventId(UUID meadEventId);
}
