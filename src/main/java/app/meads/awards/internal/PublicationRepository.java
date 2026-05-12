package app.meads.awards.internal;

import app.meads.awards.Publication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublicationRepository extends JpaRepository<Publication, UUID> {

    Optional<Publication> findTopByDivisionIdOrderByVersionDesc(UUID divisionId);

    List<Publication> findByDivisionIdOrderByVersionAsc(UUID divisionId);

    boolean existsByDivisionId(UUID divisionId);
}
