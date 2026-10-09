package de.schneefisch.eventsapi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
abstract class ResourceServerTest {

    @Autowired
    MockMvc mvc;

    @DynamicPropertySource
    static void jwkSetUri(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", TestTokens::jwkSetUri);
    }

    ResultActions listEvents(String token) throws Exception {
        return mvc.perform(get("/api/events").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    ResultActions createEvent(String token) throws Exception {
        return mvc.perform(post("/api/events").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"OAuth Night\", \"date\": \"2026-12-03\"}"));
    }

    ResultActions deleteEvent(String token) throws Exception {
        return mvc.perform(delete("/api/events/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    ResultActions whoami(String token) throws Exception {
        return mvc.perform(get("/api/whoami").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
