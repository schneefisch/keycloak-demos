package de.schneefisch.notificationservice;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {

    public record Notification(String recipient, String message) {
    }

    // Called by reporting-job with a token for notification-service (scope notifications:send)
    @PostMapping("/api/notifications")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> send(@RequestBody Notification notification, JwtAuthenticationToken authentication) {
        return Map.of("queued", true, "recipient", notification.recipient(),
                "caller", authentication.getToken().getClaimAsString("azp"));
    }
}
