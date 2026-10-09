package de.schneefisch.eventsportal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

@Configuration
public class EventsApiConfig {

    // Calls events-api with the logged-in user's access token. The token stays on the server:
    // the browser only holds the session cookie (BFF pattern). Expired tokens are refreshed.
    @Bean
    public RestClient eventsApi(RestClient.Builder builder, OAuth2AuthorizedClientManager authorizedClientManager,
                                @Value("${demo.events-api.url}") String eventsApiUrl) {
        OAuth2ClientHttpRequestInterceptor oauth2 = new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);
        oauth2.setClientRegistrationIdResolver(request -> "keycloak");
        return builder.baseUrl(eventsApiUrl).requestInterceptor(oauth2).build();
    }
}
