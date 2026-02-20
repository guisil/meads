package app.meads.internal;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@AnonymousAllowed
public class MainLayout extends AppLayout {

    public MainLayout() {
        H1 title = new H1("MEADS");
        title.getStyle().set("font-size", "1.125rem").set("margin", "0");
        addToNavbar(title);
    }
}
