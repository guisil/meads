package app.meads.identity.internal;

import app.meads.identity.User;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route("users")
@RolesAllowed("SYSTEM_ADMIN")
public class UserListView extends VerticalLayout {

    public UserListView(UserRepository userRepository) {
        add(new H1("Users"));

        Grid<User> grid = new Grid<>(User.class, false);
        grid.addColumn(User::getEmail).setHeader("Email");
        grid.addColumn(User::getName).setHeader("Name");
        grid.addColumn(User::getRole).setHeader("Role");
        grid.addColumn(User::getStatus).setHeader("Status");

        grid.setItems(userRepository.findAll());

        add(grid);
    }
}
