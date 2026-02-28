package app.meads.identity.internal;

import static com.vaadin.flow.spring.security.VaadinSecurityConfigurer.vaadin;

import app.meads.identity.AccessCodeValidator;
import app.meads.identity.JwtMagicLinkService;
import app.meads.identity.LoginView;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Optional;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtMagicLinkService jwtMagicLinkService,
                                                   UserDetailsService userDetailsService,
                                                   ApplicationEventPublisher eventPublisher,
                                                   Optional<AccessCodeValidator> accessCodeValidator) throws Exception {
        var magicLinkFilter = new MagicLinkAuthenticationFilter(
                jwtMagicLinkService, userDetailsService, eventPublisher);

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
            .addFilterBefore(magicLinkFilter, UsernamePasswordAuthenticationFilter.class);

        accessCodeValidator.ifPresent(validator -> {
            var accessCodeProvider = new AccessCodeAuthenticationProvider(validator, userDetailsService);
            http.authenticationProvider(accessCodeProvider);
        });

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
