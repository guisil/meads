package app.meads.user.ui;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Error page shown when magic link authentication fails.
 */
@Route("login-error")
@PageTitle("Login Error | MEADS")
@AnonymousAllowed
public class LoginErrorView extends VerticalLayout {

  public LoginErrorView() {
    setSizeFull();
    setAlignItems(Alignment.CENTER);
    setJustifyContentMode(JustifyContentMode.CENTER);

    H1 title = new H1("Authentication Failed");
    Paragraph message = new Paragraph(
        "The magic link you used is invalid or has expired. " +
        "Please request a new magic link to continue."
    );

    add(title, message);
  }
}
