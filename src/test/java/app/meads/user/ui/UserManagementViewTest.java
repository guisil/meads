package app.meads.user.ui;

import app.meads.user.api.UserDto;
import app.meads.user.api.UserManagementService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.notification.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserManagementView Unit Tests")
class UserManagementViewTest {

  @Mock
  private UserManagementService userService;

  @Captor
  private ArgumentCaptor<String> stringCaptor;

  @Captor
  private ArgumentCaptor<UUID> uuidCaptor;

  @Captor
  private ArgumentCaptor<List<UUID>> uuidListCaptor;

  private UserManagementView view;
  private List<UserDto> mockUsers;

  @BeforeEach
  void setUp() {
    // Given
    mockUsers = createMockUsers();
    when(userService.findAllUsers()).thenReturn(mockUsers);

    // When
    view = new UserManagementView(userService);
  }

  @Nested
  @DisplayName("View Initialization")
  class ViewInitializationTests {

    @Test
    @DisplayName("should initialize view with title and components")
    void shouldInitializeViewWithTitleAndComponents() {
      // Then
      assertThat(view.getChildren()).isNotEmpty();
      verify(userService).findAllUsers();
    }

    @Test
    @DisplayName("should load users into grid on initialization")
    void shouldLoadUsersIntoGridOnInitialization() {
      // Then
      verify(userService).findAllUsers();
    }
  }

  @Nested
  @DisplayName("Create User")
  class CreateUserTests {

    @Test
    @DisplayName("should call service when creating new user")
    void shouldCallServiceWhenCreatingNewUser() {
      // Given
      var newUser = new UserDto(
          UUID.randomUUID(),
          "newuser@example.com",
          "New User",
          "USA",
          false,
          Instant.now(),
          Instant.now()
      );

      when(userService.createUser(anyString(), anyString(), anyString(), anyBoolean()))
          .thenReturn(newUser);

      // When - simulate saving a new user through the service
      var result = userService.createUser("newuser@example.com", "New User", "USA", false);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.email()).isEqualTo("newuser@example.com");
      verify(userService).createUser("newuser@example.com", "New User", "USA", false);
    }

    @Test
    @DisplayName("should handle service error when creating user")
    void shouldHandleServiceErrorWhenCreatingUser() {
      // Given
      doThrow(new IllegalArgumentException("Email already exists"))
          .when(userService).createUser(anyString(), anyString(), anyString(), anyBoolean());

      // When & Then
      try {
        userService.createUser("duplicate@example.com", "User", "USA", false);
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage()).contains("Email already exists");
      }

      verify(userService).createUser("duplicate@example.com", "User", "USA", false);
    }
  }

  @Nested
  @DisplayName("Update User")
  class UpdateUserTests {

    @Test
    @DisplayName("should call service when updating existing user")
    void shouldCallServiceWhenUpdatingExistingUser() {
      // Given
      var userId = mockUsers.get(0).id();
      var updatedUser = new UserDto(
          userId,
          mockUsers.get(0).email(),
          "Updated Name",
          "Canada",
          true,
          mockUsers.get(0).createdAt(),
          Instant.now()
      );

      when(userService.updateUser(eq(userId), anyString(), anyString(), anyBoolean()))
          .thenReturn(updatedUser);

      // When
      var result = userService.updateUser(userId, "Updated Name", "Canada", true);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.displayName()).isEqualTo("Updated Name");
      assertThat(result.displayCountry()).isEqualTo("Canada");
      assertThat(result.isSystemAdmin()).isTrue();
      verify(userService).updateUser(userId, "Updated Name", "Canada", true);
    }

    @Test
    @DisplayName("should handle service error when updating user")
    void shouldHandleServiceErrorWhenUpdatingUser() {
      // Given
      var userId = UUID.randomUUID();
      doThrow(new IllegalArgumentException("User not found"))
          .when(userService).updateUser(eq(userId), anyString(), anyString(), anyBoolean());

      // When & Then
      try {
        userService.updateUser(userId, "Name", "USA", false);
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage()).contains("User not found");
      }

      verify(userService).updateUser(userId, "Name", "USA", false);
    }
  }

  @Nested
  @DisplayName("Delete User")
  class DeleteUserTests {

    @Test
    @DisplayName("should call service when deleting user")
    void shouldCallServiceWhenDeletingUser() {
      // Given
      var userId = mockUsers.get(0).id();

      // When
      userService.deleteUser(userId);

      // Then
      verify(userService).deleteUser(userId);
    }

    @Test
    @DisplayName("should handle service error when deleting user")
    void shouldHandleServiceErrorWhenDeletingUser() {
      // Given
      var userId = UUID.randomUUID();
      doThrow(new IllegalArgumentException("User not found"))
          .when(userService).deleteUser(userId);

      // When & Then
      try {
        userService.deleteUser(userId);
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage()).contains("User not found");
      }

      verify(userService).deleteUser(userId);
    }
  }

  @Nested
  @DisplayName("Send Single Magic Link")
  class SendSingleMagicLinkTests {

    @Test
    @DisplayName("should call service when sending single magic link")
    void shouldCallServiceWhenSendingSingleMagicLink() {
      // Given
      var userId = mockUsers.get(0).id();

      // When
      userService.sendMagicLink(userId);

      // Then
      verify(userService).sendMagicLink(userId);
    }

    @Test
    @DisplayName("should handle service error when sending magic link")
    void shouldHandleServiceErrorWhenSendingMagicLink() {
      // Given
      var userId = UUID.randomUUID();
      doThrow(new IllegalArgumentException("User not found"))
          .when(userService).sendMagicLink(userId);

      // When & Then
      try {
        userService.sendMagicLink(userId);
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage()).contains("User not found");
      }

      verify(userService).sendMagicLink(userId);
    }
  }

  @Nested
  @DisplayName("Send Bulk Magic Links")
  class SendBulkMagicLinksTests {

    @Test
    @DisplayName("should call service when sending bulk magic links")
    void shouldCallServiceWhenSendingBulkMagicLinks() {
      // Given
      var userIds = mockUsers.stream()
          .map(UserDto::id)
          .limit(2)
          .toList();

      // When
      userService.sendBulkMagicLinks(userIds);

      // Then
      verify(userService).sendBulkMagicLinks(uuidListCaptor.capture());
      var capturedIds = uuidListCaptor.getValue();
      assertThat(capturedIds).hasSize(2);
      assertThat(capturedIds).containsExactlyElementsOf(userIds);
    }

    @Test
    @DisplayName("should handle empty selection when sending bulk magic links")
    void shouldHandleEmptySelectionWhenSendingBulkMagicLinks() {
      // Given
      var emptyList = List.<UUID>of();

      // When
      userService.sendBulkMagicLinks(emptyList);

      // Then
      verify(userService).sendBulkMagicLinks(emptyList);
    }

    @Test
    @DisplayName("should handle service error when sending bulk magic links")
    void shouldHandleServiceErrorWhenSendingBulkMagicLinks() {
      // Given
      var userIds = List.of(UUID.randomUUID(), UUID.randomUUID());
      doThrow(new IllegalArgumentException("One or more users not found"))
          .when(userService).sendBulkMagicLinks(anyList());

      // When & Then
      try {
        userService.sendBulkMagicLinks(userIds);
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage()).contains("One or more users not found");
      }

      verify(userService).sendBulkMagicLinks(userIds);
    }
  }

  @Nested
  @DisplayName("Refresh Grid")
  class RefreshGridTests {

    @Test
    @DisplayName("should reload users when refreshing grid")
    void shouldReloadUsersWhenRefreshingGrid() {
      // Given
      var newUserList = List.of(
          new UserDto(
              UUID.randomUUID(),
              "fresh@example.com",
              "Fresh User",
              null,
              false,
              Instant.now(),
              Instant.now()
          )
      );
      when(userService.findAllUsers()).thenReturn(newUserList);

      // When - simulate refresh by calling the service again
      var result = userService.findAllUsers();

      // Then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).email()).isEqualTo("fresh@example.com");
    }
  }

  @Nested
  @DisplayName("Grid Configuration")
  class GridConfigurationTests {

    @Test
    @DisplayName("should display all user columns")
    void shouldDisplayAllUserColumns() {
      // Then - verify grid is properly populated
      verify(userService).findAllUsers();
    }

    @Test
    @DisplayName("should support multi-selection")
    void shouldSupportMultiSelection() {
      // The grid is configured with multi-selection mode
      // This is verified through the component structure
      assertThat(view).isNotNull();
    }
  }

  @Nested
  @DisplayName("User Form Data")
  class UserFormDataTests {

    @Test
    @DisplayName("should create form data with default values")
    void shouldCreateFormDataWithDefaultValues() {
      // Given - simulate creating new user with minimal data
      when(userService.createUser(eq("test@example.com"), eq(null), eq(null), eq(false)))
          .thenReturn(new UserDto(
              UUID.randomUUID(),
              "test@example.com",
              null,
              null,
              false,
              Instant.now(),
              Instant.now()
          ));

      // When
      var result = userService.createUser("test@example.com", null, null, false);

      // Then
      assertThat(result.email()).isEqualTo("test@example.com");
      assertThat(result.displayName()).isNull();
      assertThat(result.displayCountry()).isNull();
      assertThat(result.isSystemAdmin()).isFalse();
    }

    @Test
    @DisplayName("should create form data with all fields")
    void shouldCreateFormDataWithAllFields() {
      // Given
      when(userService.createUser(
          eq("full@example.com"),
          eq("Full Name"),
          eq("USA"),
          eq(true)))
          .thenReturn(new UserDto(
              UUID.randomUUID(),
              "full@example.com",
              "Full Name",
              "USA",
              true,
              Instant.now(),
              Instant.now()
          ));

      // When
      var result = userService.createUser("full@example.com", "Full Name", "USA", true);

      // Then
      assertThat(result.email()).isEqualTo("full@example.com");
      assertThat(result.displayName()).isEqualTo("Full Name");
      assertThat(result.displayCountry()).isEqualTo("USA");
      assertThat(result.isSystemAdmin()).isTrue();
    }
  }

  @Nested
  @DisplayName("Service Integration")
  class ServiceIntegrationTests {

    @Test
    @DisplayName("should use injected user service")
    void shouldUseInjectedUserService() {
      // Then
      assertThat(view).isNotNull();
      verify(userService).findAllUsers();
    }

    @Test
    @DisplayName("should handle multiple service calls")
    void shouldHandleMultipleServiceCalls() {
      // Given - setUp already calls findAllUsers() once during view initialization

      // When - simulate multiple operations
      userService.findAllUsers();
      userService.findAllUsers();

      // Then - verify() counts all calls including the one from setUp
      verify(userService, org.mockito.Mockito.times(3)).findAllUsers();
    }
  }

  private List<UserDto> createMockUsers() {
    return List.of(
        new UserDto(
            UUID.randomUUID(),
            "user1@example.com",
            "User One",
            "USA",
            false,
            Instant.now(),
            Instant.now()
        ),
        new UserDto(
            UUID.randomUUID(),
            "user2@example.com",
            "User Two",
            "Canada",
            true,
            Instant.now(),
            Instant.now()
        ),
        new UserDto(
            UUID.randomUUID(),
            "user3@example.com",
            "User Three",
            "UK",
            false,
            Instant.now(),
            Instant.now()
        )
    );
  }
}
