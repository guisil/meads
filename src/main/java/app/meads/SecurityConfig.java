package app.meads;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ott.InMemoryOneTimeTokenService;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.ott.OneTimeTokenGenerationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            OneTimeTokenGenerationSuccessHandler tokenSuccessHandler) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login/**", "/ott/**", "/VAADIN/**", "/favicon.ico").permitAll()
                .anyRequest().authenticated()
            )
            .csrf(csrf -> csrf.disable())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.permitAll())
            .oneTimeTokenLogin(ott -> ott
                .tokenGenerationSuccessHandler(tokenSuccessHandler)
            );

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> User.withUsername(username)
                .password("{noop}unused")
                .authorities("ROLE_USER")
                .build();
    }

    @Bean
    public OneTimeTokenService oneTimeTokenService() {
        return new InMemoryOneTimeTokenService();
    }
}
