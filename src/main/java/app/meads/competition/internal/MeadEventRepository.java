package app.meads.competition.internal;

import app.meads.competition.MeadEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MeadEventRepository extends JpaRepository<MeadEvent, UUID> {
}
