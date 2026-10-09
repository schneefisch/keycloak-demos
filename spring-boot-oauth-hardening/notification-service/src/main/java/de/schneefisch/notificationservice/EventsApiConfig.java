package de.schneefisch.notificationservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

// Outgoing: notification-service calls events-api with client credentials
@Configuration
public class EventsApiConfig {

    // Not bound to an HTTP request or session: works in scheduled jobs and reuses the token until
    // shortly before it expires (60 s clock skew). Client credentials have no refresh token.
    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository registrations,
                                                                 OAuth2AuthorizedClientService authorizedClients) {
        return new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
    }

    @Bean
    public RestClient eventsApi(RestClient.Builder builder, OAuth2AuthorizedClientManager authorizedClientManager,
                                @Value("${demo.events-api.url}") String eventsApiUrl) {
        OAuth2ClientHttpRequestInterceptor oauth2 = new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);
        // Every request of this client uses the "events-api" registration from application.yml
        oauth2.setClientRegistrationIdResolver(request -> "events-api");
        return builder.baseUrl(eventsApiUrl).requestInterceptor(oauth2).build();
    }
}
