package app.meads.identity.internal;

import static com.vaadin.flow.spring.security.VaadinSecurityConfigurer.vaadin;

import app.meads.identity.AccessCodeValidator;
import app.meads.identity.JwtMagicLinkService;
import app.meads.identity.LoginView;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain webhookSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/webhooks/**")
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/webhooks/**"))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtMagicLinkService jwtMagicLinkService,
                                                   UserDetailsService userDetailsService,
                                                   ApplicationEventPublisher eventPublisher,
                                                   AccessCodeValidator accessCodeValidator) throws Exception {
        var magicLinkFilter = new MagicLinkAuthenticationFilter(
                jwtMagicLinkService, userDetailsService, eventPublisher);

        var accessCodeProvider = new AccessCodeAwareAuthenticationProvider(accessCodeValidator, userDetailsService);

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login/magic").permitAll()
            )
            .with(vaadin(), vaadin -> vaadin
                .loginView(LoginView.class)
            )
            .formLogin(form -> form
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/", true)
            )
            .addFilterBefore(magicLinkFilter, UsernamePasswordAuthenticationFilter.class)
            .authenticationProvider(accessCodeProvider);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

}
