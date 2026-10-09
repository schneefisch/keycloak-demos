package de.schneefisch.eventsportal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

/**
 * RFC 9207: rejects an authorization response whose {@code iss} doesn't match the issuer the request
 * was sent to. Spring Security doesn't check it. Only needed when the app trusts several IdPs
 * (mix-up attacks); with a single Keycloak it is harmless.
 */
class IssuerCheckingAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private final AuthorizationRequestRepository<OAuth2AuthorizationRequest> delegate =
            new HttpSessionOAuth2AuthorizationRequestRepository();

    private final ClientRegistrationRepository registrations;

    IssuerCheckingAuthorizationRequestRepository(ClientRegistrationRepository registrations) {
        this.registrations = registrations;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return this.delegate.loadAuthorizationRequest(request);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request, HttpServletResponse response) {
        this.delegate.saveAuthorizationRequest(authorizationRequest, request, response);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = this.delegate.removeAuthorizationRequest(request, response);
        if (authorizationRequest == null) {
            return null;
        }
        ClientRegistration registration = this.registrations
                .findByRegistrationId(authorizationRequest.getAttribute(OAuth2ParameterNames.REGISTRATION_ID));
        String expected = registration.getProviderDetails().getIssuerUri();
        String actual = request.getParameter("iss");
        // An IdP that announces the parameter must send it (RFC 9207 §2.4)
        boolean required = Boolean.TRUE.equals(registration.getProviderDetails().getConfigurationMetadata()
                .get("authorization_response_iss_parameter_supported"));
        if ((actual == null && required) || (actual != null && !actual.equals(expected))) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST,
                    "iss in the authorization response is " + actual + ", expected " + expected, null));
        }
        return authorizationRequest;
    }
}
