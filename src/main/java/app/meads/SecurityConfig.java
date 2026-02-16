package app.meads;

import static com.vaadin.flow.spring.security.VaadinSecurityConfigurer.vaadin;

import app.meads.identity.LoginView;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ott.InMemoryOneTimeTokenService;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login/magic").permitAll()
            )
            .with(vaadin(), vaadin -> vaadin
                .loginView(LoginView.class)
            )
            .formLogin(form -> form.disable())
            .oneTimeTokenLogin(ott -> ott.showDefaultSubmitPage(false));

        return http.build();
    }

    @Bean
    public OneTimeTokenService oneTimeTokenService() {
        return new InMemoryOneTimeTokenService();
    }

}
