package de.schneefisch.stepup.controller;

import de.schneefisch.stepup.acr.RequireAcr;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DemoController {

    @GetMapping("/api/public/info")
    public Map<String, String> publicInfo() {
        return Map.of(
                "status", "public",
                "description", "This endpoint requires no authentication"
        );
    }

    @GetMapping("/api/user/profile")
    public Map<String, Object> userProfile(JwtAuthenticationToken auth) {
        return Map.of(
                "subject", auth.getToken().getSubject(),
                "acr", String.valueOf(auth.getToken().getClaimAsString("acr"))
        );
    }

    @RequireAcr(2)
    @GetMapping("/api/admin/sensitive")
    public Map<String, Object> sensitiveData(JwtAuthenticationToken auth) {
        return Map.of(
                "message", "sensitive data",
                "subject", auth.getToken().getSubject(),
                "acr", String.valueOf(auth.getToken().getClaimAsString("acr"))
        );
    }
}
