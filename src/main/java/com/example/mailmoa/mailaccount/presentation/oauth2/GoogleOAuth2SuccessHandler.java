package com.example.mailmoa.mailaccount.presentation.oauth2;

import com.example.mailmoa.mailaccount.application.dto.SaveMailAccountCommand;
import com.example.mailmoa.mailaccount.application.port.OAuthStatePort;
import com.example.mailmoa.mailaccount.application.usecase.MailAccountUseCase;
import com.example.mailmoa.mailaccount.domain.model.MailProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final MailAccountUseCase mailAccountUseCase;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final OAuthStatePort oAuthStatePort;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;

        Long userId = extractUserIdFromState(request, response);
        if (userId == null) return;

        String emailAddress = extractEmail(oauthToken);
        OAuth2AuthorizedClient authorizedClient = loadAuthorizedClient(oauthToken);
        SaveMailAccountCommand command = buildCommand(userId, emailAddress, authorizedClient);

        mailAccountUseCase.saveMailAccount(command);

        response.sendRedirect(frontendUrl + "/mail-accounts?connected=true");
    }

    private Long extractUserIdFromState(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String state = request.getParameter("state");
        String userId = oAuthStatePort.getUserId(state);

        if (userId == null) {
            response.sendRedirect(frontendUrl + "/error?message=session_expired");
            return null;
        }

        oAuthStatePort.deleteState(state);
        return Long.parseLong(userId);
    }

    private String extractEmail(OAuth2AuthenticationToken oauthToken) {
        OAuth2User oAuth2User = oauthToken.getPrincipal();
        return oAuth2User.getAttribute("email");
    }

    private OAuth2AuthorizedClient loadAuthorizedClient(OAuth2AuthenticationToken oauthToken) {
        return authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(),
                oauthToken.getName()
        );
    }

    private SaveMailAccountCommand buildCommand(Long userId, String emailAddress, OAuth2AuthorizedClient authorizedClient) {
        OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
        OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();

        LocalDateTime tokenExpiresAt = accessToken.getExpiresAt() != null
                ? LocalDateTime.ofInstant(accessToken.getExpiresAt(), ZoneId.systemDefault())
                : null;

        return new SaveMailAccountCommand(
                userId,
                emailAddress,
                MailProvider.GMAIL,
                accessToken.getTokenValue(),
                refreshToken != null ? refreshToken.getTokenValue() : null,
                tokenExpiresAt
        );
    }
}
