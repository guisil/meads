package app.meads.internal;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.QueryParameters;

import java.util.Map;

@Route("login")
public class LoginView extends VerticalLayout {

    private final MagicLinkService magicLinkService;

    public LoginView(MagicLinkService magicLinkService) {
        this.magicLinkService = magicLinkService;

        var email = new TextField("Email");
        email.getElement().setAttribute("name", "username");

        var button = new Button("Continue");
        button.addClickListener(e -> {
            var token = magicLinkService.requestMagicLink(email.getValue());
            e.getSource().getUI().ifPresent(ui ->
                ui.navigate("login", QueryParameters.simple(Map.of("tokenSent", "")))
            );
        });

        add(email, button);
    }
}
