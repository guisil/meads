package app.meads.entrant.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface EntrantRepository extends JpaRepository<EntrantEntity, UUID> {

    Optional<EntrantEntity> findByEmail(String email);
}
