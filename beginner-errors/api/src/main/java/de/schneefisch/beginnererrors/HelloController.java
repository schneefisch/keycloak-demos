package de.schneefisch.beginnererrors;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public Map<String, Object> hello(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Hello, " + jwt.getClaimAsString("preferred_username") + "!");
        body.put("iss", jwt.getClaimAsString("iss"));
        body.put("aud", jwt.getAudience());
        body.put("azp", jwt.getClaimAsString("azp"));
        return body;
    }
}
