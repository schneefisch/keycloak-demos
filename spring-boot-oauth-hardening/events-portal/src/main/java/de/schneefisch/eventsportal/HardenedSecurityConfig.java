package de.schneefisch.eventsportal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
@Profile("hardened")
public class HardenedSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ClientRegistrationRepository registrations)
            throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/error").permitAll()
                        .anyRequest().authenticated())
                // state, nonce and PKCE are on by default (PKCE for confidential clients since Spring Security 7.0).
                // The iss check (RFC 9207) is not: only needed with several IdPs, see the repository class.
                .oauth2Login(login -> login.authorizationEndpoint(endpoint -> endpoint
                        .authorizationRequestRepository(new IssuerCheckingAuthorizationRequestRepository(registrations))))
                // Ends the Keycloak SSO session too, not only the local one (RP-initiated logout)
                .logout(logout -> logout.logoutSuccessHandler(rpInitiatedLogout(registrations)))
                // Keycloak notifies the app when the SSO session ends elsewhere (back-channel logout)
                .oidcLogout(oidc -> oidc.backChannel(Customizer.withDefaults()));
        // CSRF protection stays on (the default): this app authenticates with a session cookie
        return http.build();
    }

    private static LogoutSuccessHandler rpInitiatedLogout(ClientRegistrationRepository registrations) {
        OidcClientInitiatedLogoutSuccessHandler handler = new OidcClientInitiatedLogoutSuccessHandler(registrations);
        handler.setPostLogoutRedirectUri("{baseUrl}/");
        return handler;
    }

    // Lets the back-channel logout support track and clean up sessions
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
