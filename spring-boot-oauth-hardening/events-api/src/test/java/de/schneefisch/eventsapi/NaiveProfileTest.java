package de.schneefisch.eventsapi;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.test.context.ActiveProfiles;

import static de.schneefisch.eventsapi.TestTokens.accessToken;
import static de.schneefisch.eventsapi.TestTokens.clientRoles;
import static de.schneefisch.eventsapi.TestTokens.idToken;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The defaults: what they already block, and what gets through.
 */
@ActiveProfiles("naive")
class NaiveProfileTest extends ResourceServerTest {

    // --- what the defaults already block ---------------------------------------------------

    @Test
    void unsignedTokenWithAlgNone_isRejected() throws Exception {
        listEvents(accessToken().unsigned())
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Unsupported algorithm of none")));
    }

    @Test
    void hs256TokenSignedWithThePublicKey_isRejected() throws Exception {
        listEvents(accessToken().signedWithPublicKeyAsHmacSecret())
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Another algorithm expected")));
    }

    // --- what gets through -----------------------------------------------------------------

    @Test
    void tokenForAnotherService_isAccepted() throws Exception {
        listEvents(accessToken().claim("aud", List.of("notification-service")).signed())
                .andExpect(status().isOk());
    }

    @Test
    void idToken_isAccepted() throws Exception {
        listEvents(idToken().signed())
                .andExpect(status().isOk());
    }

    @Test
    void tokenWithoutExpiry_isAccepted() throws Exception {
        listEvents(accessToken().without("exp").signed())
                .andExpect(status().isOk());
    }

    @Test
    void adminRoleOfAnotherClient_grantsAdmin() throws Exception {
        String bob = accessToken()
                .claim("preferred_username", "bob")
                .claim("resource_access", clientRoles("notification-service", "admin"))
                .signed();

        whoami(bob).andExpect(jsonPath("$.authorities", hasItem("ROLE_admin")));
        deleteEvent(bob).andExpect(status().isNoContent());
    }
}
