package com.example.mailmoa.mailaccount.presentation.oauth2;

import com.example.mailmoa.mailaccount.application.port.OAuthStatePort;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final OAuthStatePort oAuthStatePort;
    private final ClientRegistrationRepository clientRegistrationRepository;

    private DefaultOAuth2AuthorizationRequestResolver defaultResolver() {
        return new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, "/oauth2/authorization");
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest authorizationRequest = defaultResolver().resolve(request);
        if (authorizationRequest != null) {
            saveState(request, authorizationRequest.getState());
        }
        return authorizationRequest;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest authorizationRequest = defaultResolver().resolve(request, clientRegistrationId);
        if (authorizationRequest != null) {
            saveState(request, authorizationRequest.getState());
        }
        return authorizationRequest;
    }

    private void saveState(HttpServletRequest request, String state) {
        String userId = request.getParameter("userId");
        if (userId != null) {
            oAuthStatePort.saveState(state, userId);
        }
    }
}
