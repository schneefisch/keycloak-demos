package de.schneefisch.eventsapi;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.server.resource.authentication.DelegatingJwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Hardened token handling. Together with {@code audiences} in application-hardened.yml.
 * Spring Boot adds every {@link OAuth2TokenValidator} bean to the validators of its JwtDecoder.
 */
@Configuration
@Profile("hardened")
public class HardenedJwtConfig {

    private static final String CLIENT_ID = "events-api";

    // Keycloak writes "typ": "JWT" into the header of every token (access, ID, refresh).
    // Only the payload claim tells an access token ("Bearer") from an ID token ("ID").
    // Keycloak-specific: the RFC 9068 way is the header "typ": "at+jwt" (a Keycloak client option,
    // off by default). Defense in depth: an ID token usually fails the aud check already.
    @Bean
    public OAuth2TokenValidator<Jwt> accessTokensOnly() {
        return new JwtClaimValidator<String>("typ", "Bearer"::equals);
    }

    // By default Spring accepts a JWT without "exp". Such a token would never expire.
    // Defense in depth: Keycloak always sets "exp", so this guards against other or misconfigured issuers.
    @Bean
    public OAuth2TokenValidator<Jwt> expiryRequired() {
        JwtTimestampValidator timestamps = new JwtTimestampValidator();
        timestamps.setAllowEmptyExpiryClaim(false);
        return timestamps;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // "scope" claim -> SCOPE_xxx, as Spring does by default
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                new DelegatingJwtGrantedAuthoritiesConverter(scopes, HardenedJwtConfig::ownClientRoles));
        // The principal stays "sub" (default): stable and unique per issuer, unlike preferred_username
        return converter;
    }

    // Only resource_access.events-api.roles -> ROLE_xxx. Roles of other clients are ignored.
    private static Collection<GrantedAuthority> ownClientRoles(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess == null) {
            return List.of();
        }
        return roles(resourceAccess.get(CLIENT_ID));
    }

    private static List<GrantedAuthority> roles(Object clientAccess) {
        if (!(clientAccess instanceof Map<?, ?> access) || !(access.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream().<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
    }
}
