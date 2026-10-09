package de.schneefisch.eventsapi;

import org.junit.jupiter.api.Test;

import org.springframework.test.context.TestPropertySource;

import static de.schneefisch.eventsapi.TestTokens.accessToken;
import static de.schneefisch.eventsapi.TestTokens.clientRoles;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring Boot 4.1's {@code authorities-claim-expressions} (no profile: Boot's own converter). It reads
 * nested Keycloak roles without Java code, but replaces the scope mapping. Without
 * {@code authority-prefix} the roles would become SCOPE_admin.
 */
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.authorities-claim-expressions=[resource_access]['events-api'][roles]",
        "spring.security.oauth2.resourceserver.jwt.authority-prefix=ROLE_" })
class AuthoritiesClaimExpressionsTest extends ResourceServerTest {

    @Test
    void rolesAreMappedButScopesAreGone() throws Exception {
        String alice = accessToken()
                .claim("scope", "openid events:write")
                .claim("resource_access", clientRoles("events-api", "admin"))
                .signed();

        whoami(alice)
                .andExpect(jsonPath("$.authorities", hasItem("ROLE_admin")))
                .andExpect(jsonPath("$.authorities", not(hasItem("SCOPE_events:write"))));
        createEvent(alice).andExpect(status().isForbidden());
    }
}
