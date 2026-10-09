package de.schneefisch.eventsapi;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.test.context.ActiveProfiles;

import static de.schneefisch.eventsapi.TestTokens.accessToken;
import static de.schneefisch.eventsapi.TestTokens.clientRoles;
import static de.schneefisch.eventsapi.TestTokens.idToken;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("hardened")
class HardenedProfileTest extends ResourceServerTest {

    @Test
    void accessTokenForThisApi_isAccepted() throws Exception {
        listEvents(accessToken().signed())
                .andExpect(status().isOk());
    }

    @Test
    void tokenForAnotherService_isRejected() throws Exception {
        listEvents(accessToken().claim("aud", List.of("notification-service")).signed())
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("The aud claim is not valid")));
    }

    @Test
    void idTokenWithMatchingAudience_isRejected() throws Exception {
        // Even if an ID token carried the API's audience, its payload "typ" gives it away
        listEvents(idToken().claim("aud", "events-api").signed())
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("The typ claim is not valid")));
    }

    @Test
    void tokenWithoutExpiry_isRejected() throws Exception {
        listEvents(accessToken().without("exp").signed())
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("exp is required")));
    }

    @Test
    void adminRoleOfAnotherClient_isIgnored() throws Exception {
        String bob = accessToken()
                .claim("sub", "e5f6a7b8-bob")
                .claim("preferred_username", "bob")
                .claim("resource_access", clientRoles("notification-service", "admin"))
                .signed();

        whoami(bob).andExpect(jsonPath("$.authorities", not(hasItem("ROLE_admin"))));
        deleteEvent(bob).andExpect(status().isForbidden());
    }

    @Test
    void adminRoleOfThisApi_isMapped() throws Exception {
        String alice = accessToken().claim("resource_access", clientRoles("events-api", "admin")).signed();

        deleteEvent(alice).andExpect(status().isNoContent());
    }

    @Test
    void scopesStillWork() throws Exception {
        createEvent(accessToken().claim("scope", "openid events:write").signed())
                .andExpect(status().isCreated());
        createEvent(accessToken().signed())
                .andExpect(status().isForbidden());
    }

    @Test
    void principalIsTheStableSubject() throws Exception {
        whoami(accessToken().signed())
                .andExpect(jsonPath("$.principal").value("a1b2c3d4-alice"));
    }
}
