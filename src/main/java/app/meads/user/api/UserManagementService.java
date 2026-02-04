package app.meads.user.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API for user management operations.
 * Exposed to other modules for CRUD operations and magic link functionality.
 */
public interface UserManagementService {

  /**
   * Creates a new user.
   *
   * @param email the user's email
   * @param displayName the user's display name (optional)
   * @param displayCountry the user's country (optional)
   * @param isSystemAdmin whether the user is a system admin
   * @return the created user
   * @throws IllegalArgumentException if email already exists
   */
  UserDto createUser(String email, String displayName, String displayCountry, boolean isSystemAdmin);

  /**
   * Updates an existing user.
   *
   * @param userId the user ID
   * @param displayName the new display name (optional)
   * @param displayCountry the new country (optional)
   * @param isSystemAdmin the new admin status
   * @return the updated user
   * @throws IllegalArgumentException if user not found
   */
  UserDto updateUser(UUID userId, String displayName, String displayCountry, boolean isSystemAdmin);

  /**
   * Deletes a user.
   *
   * @param userId the user ID
   * @throws IllegalArgumentException if user not found
   */
  void deleteUser(UUID userId);

  /**
   * Finds a user by ID.
   *
   * @param userId the user ID
   * @return optional containing the user if found
   */
  Optional<UserDto> findUserById(UUID userId);

  /**
   * Finds a user by email.
   *
   * @param email the email address
   * @return optional containing the user if found
   */
  Optional<UserDto> findUserByEmail(String email);

  /**
   * Retrieves all users.
   *
   * @return list of all users
   */
  List<UserDto> findAllUsers();

  /**
   * Generates and sends a magic link to a user.
   *
   * @param userId the user ID
   * @throws IllegalArgumentException if user not found
   */
  void sendMagicLink(UUID userId);

  /**
   * Generates and sends magic links to multiple users.
   *
   * @param userIds list of user IDs
   */
  void sendBulkMagicLinks(List<UUID> userIds);
}
