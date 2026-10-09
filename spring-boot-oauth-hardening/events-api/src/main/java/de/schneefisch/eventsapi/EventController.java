package de.schneefisch.eventsapi;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class EventController {

    public record Event(int id, String title, String date) {
    }

    public record NewEvent(String title, String date) {
    }

    private final Map<Integer, Event> events = new ConcurrentSkipListMap<>(Map.of(
            1, new Event(1, "Spring Meetup", "2026-11-12"),
            2, new Event(2, "Keycloak Workshop", "2026-11-19")));

    private final AtomicInteger nextId = new AtomicInteger(3);

    @GetMapping("/events")
    public Collection<Event> list() {
        return events.values();
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public Event create(@RequestBody NewEvent event) {
        Event created = new Event(nextId.getAndIncrement(), event.title(), event.date());
        events.put(created.id(), created);
        return created;
    }

    // Idempotent on purpose: the scripts repeat the role checks, and only the status code matters
    @DeleteMapping("/events/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable int id) {
        events.remove(id);
    }

    // Shows what the API made of the token: the demo scripts print this to explain each result
    @GetMapping("/whoami")
    public Map<String, Object> whoami(JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("principal", authentication.getName());
        result.put("authorities", authorities);
        result.put("typ", jwt.getClaimAsString("typ"));
        result.put("aud", jwt.getAudience());
        result.put("azp", jwt.getClaimAsString("azp"));
        result.put("scope", jwt.getClaimAsString("scope"));
        return result;
    }
}
