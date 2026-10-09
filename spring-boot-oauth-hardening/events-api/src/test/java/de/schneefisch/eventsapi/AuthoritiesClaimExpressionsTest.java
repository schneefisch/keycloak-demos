package de.schneefisch.eventsapi;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.test.context.TestPropertySource;

import static de.schneefisch.eventsapi.TestTokens.accessToken;
import static de.schneefisch.eventsapi.TestTokens.clientRoles;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring Boot 4.1's {@code authorities-claim-expressions} (no profile: Boot's own converter).
 * It reads nested Keycloak roles without Java code, but replaces the scope mapping.
 */
class AuthoritiesClaimExpressionsTest {

    static final String ALICE_WITH_WRITE_SCOPE = accessToken()
            .claim("scope", "openid events:write")
            .claim("resource_access", clientRoles("events-api", "admin"))
            .signed();

    @Nested
    @TestPropertySource(properties = "spring.security.oauth2.resourceserver.jwt.authorities-claim-expressions=[resource_access]['events-api'][roles]")
    class WithoutPrefix extends ResourceServerTest {

        @Test
        void rolesGetTheScopePrefixAndScopesDisappear() throws Exception {
            whoami(ALICE_WITH_WRITE_SCOPE)
                    .andExpect(jsonPath("$.authorities", hasItem("SCOPE_admin")))
                    .andExpect(jsonPath("$.authorities", not(hasItem("SCOPE_events:write"))));
            createEvent(ALICE_WITH_WRITE_SCOPE)
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @TestPropertySource(properties = {
            "spring.security.oauth2.resourceserver.jwt.authorities-claim-expressions=[resource_access]['events-api'][roles]",
            "spring.security.oauth2.resourceserver.jwt.authority-prefix=ROLE_" })
    class WithRolePrefix extends ResourceServerTest {

        @Test
        void rolesAreMappedButScopesAreStillGone() throws Exception {
            whoami(ALICE_WITH_WRITE_SCOPE)
                    .andExpect(jsonPath("$.authorities", hasItem("ROLE_admin")))
                    .andExpect(jsonPath("$.authorities", not(hasItem("SCOPE_events:write"))));
            createEvent(ALICE_WITH_WRITE_SCOPE)
                    .andExpect(status().isForbidden());
        }
    }
}
