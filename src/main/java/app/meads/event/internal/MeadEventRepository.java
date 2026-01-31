package app.meads.event.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface MeadEventRepository extends JpaRepository<MeadEventEntity, UUID> {

    Optional<MeadEventEntity> findBySlug(String slug);
}
