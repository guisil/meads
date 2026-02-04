package app.meads.user.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for User entity operations.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

  /**
   * Finds a user by email address.
   * Email is the natural identifier for users.
   *
   * @param email the email address to search for
   * @return optional containing the user if found
   */
  Optional<User> findByEmail(String email);

  /**
   * Checks if a user with the given email exists.
   *
   * @param email the email address to check
   * @return true if a user with this email exists
   */
  boolean existsByEmail(String email);
}
