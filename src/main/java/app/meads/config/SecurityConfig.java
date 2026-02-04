package app.meads.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for MEADS application with magic link authentication.
 */
@EnableWebSecurity
@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            // Allow magic link verification without authentication
            .requestMatchers("/auth/**").permitAll()
            // Allow login error page
            .requestMatchers("/login-error").permitAll()
            // Allow Vaadin internal resources
            .requestMatchers("/VAADIN/**").permitAll()
            // For now, permit all other requests (TODO: lock down in production)
            .anyRequest().permitAll()
        )
        // Enable session management for authenticated users
        .sessionManagement(session -> session
            .maximumSessions(1)
        )
        .csrf(csrf -> csrf.disable()); // Vaadin handles CSRF internally

    return http.build();
  }
}


