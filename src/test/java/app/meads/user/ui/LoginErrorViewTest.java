package app.meads.user.ui;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LoginErrorView Unit Tests")
class LoginErrorViewTest {

  private LoginErrorView view;

  @BeforeEach
  void setUp() {
    // When
    view = new LoginErrorView();
  }

  @Nested
  @DisplayName("View Initialization")
  class ViewInitializationTests {

    @Test
    @DisplayName("should initialize view with title")
    void shouldInitializeViewWithTitle() {
      // Then
      assertThat(view.getChildren()).isNotEmpty();
      var titleOptional = view.getChildren()
          .filter(component -> component instanceof H1)
          .findFirst();
      assertThat(titleOptional).isPresent();
      var title = (H1) titleOptional.get();
      assertThat(title.getText()).isEqualTo("Authentication Failed");
    }

    @Test
    @DisplayName("should initialize view with error message")
    void shouldInitializeViewWithErrorMessage() {
      // Then
      var messageOptional = view.getChildren()
          .filter(component -> component instanceof Paragraph)
          .findFirst();
      assertThat(messageOptional).isPresent();
      var message = (Paragraph) messageOptional.get();
      assertThat(message.getText())
          .contains("magic link")
          .contains("invalid or has expired");
    }

    @Test
    @DisplayName("should center content")
    void shouldCenterContent() {
      // Then
      assertThat(view.getAlignItems()).isEqualTo(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
      assertThat(view.getJustifyContentMode()).isEqualTo(com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode.CENTER);
    }

    @Test
    @DisplayName("should have exactly two components")
    void shouldHaveExactlyTwoComponents() {
      // Then
      assertThat(view.getChildren().count()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("View Content")
  class ViewContentTests {

    @Test
    @DisplayName("should display helpful error message")
    void shouldDisplayHelpfulErrorMessage() {
      // Then
      var messageOptional = view.getChildren()
          .filter(component -> component instanceof Paragraph)
          .findFirst();
      assertThat(messageOptional).isPresent();
      var message = (Paragraph) messageOptional.get();
      assertThat(message.getText())
          .contains("Please request a new magic link to continue");
    }

    @Test
    @DisplayName("should have clear authentication failure title")
    void shouldHaveClearAuthenticationFailureTitle() {
      // Then
      var titleOptional = view.getChildren()
          .filter(component -> component instanceof H1)
          .findFirst();
      assertThat(titleOptional).isPresent();
      var title = (H1) titleOptional.get();
      assertThat(title.getText())
          .isNotEmpty()
          .contains("Authentication")
          .contains("Failed");
    }
  }

  @Nested
  @DisplayName("View Layout")
  class ViewLayoutTests {

    @Test
    @DisplayName("should use vertical layout")
    void shouldUseVerticalLayout() {
      // Then
      assertThat(view).isInstanceOf(com.vaadin.flow.component.orderedlayout.VerticalLayout.class);
    }

    @Test
    @DisplayName("should have proper alignment settings")
    void shouldHaveProperAlignmentSettings() {
      // Then
      assertThat(view.getAlignItems())
          .isEqualTo(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
      assertThat(view.getJustifyContentMode())
          .isEqualTo(com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode.CENTER);
    }
  }
}
