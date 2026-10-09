package de.schneefisch.eventsportal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * A typical first BFF setup: login works, everything else is left at the defaults.
 * state, nonce and PKCE are already on by default in Spring Security 7. The gaps: logout ends only
 * the local session, CSRF protection is off, and the principal is the mutable preferred_username.
 */
@Configuration
@Profile("naive")
public class NaiveSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(Customizer.withDefaults())
                // Copied from the API's config. Wrong here: this app authenticates with a session cookie.
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
