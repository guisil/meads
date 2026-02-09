package app.meads;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
                .requestMatchers("/", "/login/**", "/VAADIN/**", "/favicon.ico").permitAll()
                .anyRequest().authenticated()
            )
            // Disable CSRF for now (we'll configure it properly with magic link auth)
            .csrf(csrf -> csrf.disable())
            // Disable form login - we're using magic links
            .formLogin(form -> form.disable())
            // Disable HTTP Basic
            .httpBasic(basic -> basic.disable())
            // Configure logout
            .logout(logout -> logout.permitAll());

        return http.build();
    }
}
