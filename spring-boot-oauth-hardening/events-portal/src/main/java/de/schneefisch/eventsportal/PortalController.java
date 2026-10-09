package de.schneefisch.eventsportal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PortalController {

    private static final List<String> SHOWN_CLAIMS = List.of(
            "sub", "preferred_username", "email", "email_verified", "auth_time", "acr", "sid");

    private final RestClient eventsApi;

    private final Environment environment;

    public PortalController(RestClient eventsApi, Environment environment) {
        this.eventsApi = eventsApi;
        this.environment = environment;
    }

    @GetMapping("/")
    public String index(@AuthenticationPrincipal OidcUser user, Authentication authentication, Model model) {
        model.addAttribute("mode", String.join(",", this.environment.getActiveProfiles()));
        if (user == null) {
            return "index";
        }
        Map<String, Object> claims = new LinkedHashMap<>();
        SHOWN_CLAIMS.forEach(name -> claims.put(name, user.getClaims().get(name)));

        model.addAttribute("name", authentication.getName());
        model.addAttribute("claims", claims);
        model.addAttribute("authorities",
                authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList());
        model.addAttribute("events", call("/api/events"));
        return "index";
    }

    @PostMapping("/events/{id}/delete")
    public String delete(@PathVariable int id, RedirectAttributes redirect) {
        Integer status = this.eventsApi.delete().uri("/api/events/{id}", id)
                .exchange((request, response) -> response.getStatusCode().value());
        redirect.addFlashAttribute("deleteResult", "DELETE /api/events/" + id + " → HTTP " + status);
        return "redirect:/";
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
