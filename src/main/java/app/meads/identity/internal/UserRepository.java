package app.meads.identity.internal;

import app.meads.identity.Role;
import app.meads.identity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByRole(Role role);
    boolean existsByEmail(String email);
}
