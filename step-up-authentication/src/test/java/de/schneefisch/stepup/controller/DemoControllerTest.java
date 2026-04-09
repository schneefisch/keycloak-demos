package de.schneefisch.stepup.controller;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Nested
    class PublicEndpoint {

        @Test
        void returnsInfoWithoutAuthentication() throws Exception {
            mockMvc.perform(get("/api/public/info"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("public"));
        }
    }

    @Nested
    class UserEndpoint {

        @Test
        void rejectsUnauthenticatedRequests() throws Exception {
            mockMvc.perform(get("/api/user/profile"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void allowsAnyAuthenticatedUser() throws Exception {
            mockMvc.perform(get("/api/user/profile")
                            .with(jwt().jwt(j -> j.claim("acr", "password").subject("testuser"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.subject").value("testuser"));
        }
    }

    @Nested
    class SensitiveEndpoint {

        @Test
        void rejectsPasswordAcrWithStepUpChallenge() throws Exception {
            mockMvc.perform(get("/api/admin/sensitive")
                            .with(jwt().jwt(j -> j.claim("acr", "password").subject("testuser"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate",
                            org.hamcrest.Matchers.containsString("insufficient_authentication_level")))
                    .andExpect(jsonPath("$.acr_values").value("mfa"));
        }

        @Test
        void allowsMfaAcr() throws Exception {
            mockMvc.perform(get("/api/admin/sensitive")
                            .with(jwt().jwt(j -> j.claim("acr", "mfa").subject("testuser"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("sensitive data"));
        }

        @Test
        void rejectsUnauthenticatedRequests() throws Exception {
            mockMvc.perform(get("/api/admin/sensitive"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
