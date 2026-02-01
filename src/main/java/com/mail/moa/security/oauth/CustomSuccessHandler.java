package com.mail.moa.security.oauth;

import com.mail.moa.security.auth.PrincipalDetails;
import com.mail.moa.security.jwt.JwtTokenProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        // 1. 유저 정보 추출 (PrincipalDetails 활용)
        PrincipalDetails principal = (PrincipalDetails) authentication.getPrincipal();
        String username = principal.getUsername();

        // 2. JWT 토큰 생성
        String token = jwtTokenProvider.createToken(username);

        // 3. 리다이렉트 URL 생성 (UriComponentsBuilder로 쿼리 파라미터 안전하게 추가)
        String targetUrl = UriComponentsBuilder.fromUriString("http://localhost:8080/")
                .queryParam("token", token)
                .build().toUriString();

        log.info("tokenAS: {}", token);

        // 4. 리다이렉트 실행
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
        //OAuth2.0User
        /*
        PrincipalDetails principal = (PrincipalDetails) authentication.getPrincipal();

        String username = principal.getUsername();

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        Iterator<? extends GrantedAuthority> iterator = authorities.iterator();
        GrantedAuthority auth = iterator.next();
        String role = auth.getAuthority();

        String token = jwtTokenProvider.createToken(username);

        response.addHeader("Authorization", "Bearer " + token);
         */
    }
}
