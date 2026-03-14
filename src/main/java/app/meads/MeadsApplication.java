package app.meads;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.Theme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Theme("meads")
public class MeadsApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(MeadsApplication.class, args);
    }
}
