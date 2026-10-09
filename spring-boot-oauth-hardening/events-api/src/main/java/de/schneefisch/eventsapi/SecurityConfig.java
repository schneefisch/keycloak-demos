package de.schneefisch.eventsapi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Authorization rules shared by both profiles. The profiles only differ in how a token is
 * validated and turned into authorities: see {@link NaiveJwtConfig} and {@link HardenedJwtConfig}.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/events").hasAuthority("SCOPE_events:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/events/**").hasRole("admin")
                        // Reading events is open to every authenticated caller, users and services alike
                        .anyRequest().authenticated())
                // Picks up the JwtAuthenticationConverter bean of the active profile
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                // Stateless bearer-token API: no session, no cookies, so no CSRF token needed
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
