package de.schneefisch.eventsapi;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.DelegatingJwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * What many teams ship: only {@code issuer-uri} in application.yml, plus the Keycloak role
 * converter from a tutorial. It turns the roles of every client into ROLE_xxx.
 */
@Configuration
@Profile("naive")
public class NaiveJwtConfig {

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                new DelegatingJwtGrantedAuthoritiesConverter(scopes, NaiveJwtConfig::allClientRoles));
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }

    // The problem: roles of *every* client end up here, not just the roles of events-api
    private static Collection<GrantedAuthority> allClientRoles(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess == null) {
            return List.of();
        }
        return resourceAccess.values().stream().flatMap(client -> roles(client).stream()).toList();
    }

    private static List<GrantedAuthority> roles(Object clientAccess) {
        if (!(clientAccess instanceof Map<?, ?> access) || !(access.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream().<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
    }
}
