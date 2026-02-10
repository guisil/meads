package app.meads.internal;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;

@Route("login")
public class LoginView extends VerticalLayout {

    public LoginView() {
        var email = new TextField("Email");
        email.getElement().setAttribute("name", "username");

        var button = new Button("Continue");
        button.addClickListener(e ->
            e.getSource().getUI().ifPresent(ui ->
                ui.getPage().executeJs(
                    "const form = document.createElement('form');" +
                    "form.method = 'POST';" +
                    "form.action = '/ott/generate';" +
                    "const input = document.createElement('input');" +
                    "input.type = 'hidden';" +
                    "input.name = 'username';" +
                    "input.value = $0;" +
                    "form.appendChild(input);" +
                    "document.body.appendChild(form);" +
                    "form.submit();",
                    email.getValue()
                )
            )
        );

        add(email, button);
    }
}
