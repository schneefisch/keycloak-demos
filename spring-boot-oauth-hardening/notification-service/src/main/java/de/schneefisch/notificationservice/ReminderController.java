package de.schneefisch.notificationservice;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Builds event reminders from events-api. The response shows how events-api treated the
 * client-credentials token: its /api/whoami answer, or the reason for a 401.
 */
@RestController
public class ReminderController {

    private final RestClient eventsApi;

    public ReminderController(RestClient eventsApi) {
        this.eventsApi = eventsApi;
    }

    @GetMapping("/reminders/preview")
    public Map<String, Object> preview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("whoami", call("/api/whoami"));
        result.put("events", call("/api/events"));
        return result;
    }

    private Map<String, Object> call(String path) {
        return this.eventsApi.get().uri(path).exchange((request, response) -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", response.getStatusCode().value());
            if (response.getStatusCode().is2xxSuccessful()) {
                result.put("body", response.bodyTo(Object.class));
            }
            else {
                result.put("wwwAuthenticate", response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
            }
            return result;
        });
    }
}
