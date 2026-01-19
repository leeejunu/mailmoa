package com.mail.moa.security.oauth;

import com.mail.moa.domain.EmailAccount;
import com.mail.moa.domain.User;
import com.mail.moa.emailAcccount.EmailAccountRepository;
import com.mail.moa.security.auth.PrincipalDetails;
import com.mail.moa.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // 로그 라이브러리 추가
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j // 1. 로그 작성을 위한 어노테이션 추가
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final EmailAccountRepository emailAccountRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // 2. 부모 클래스의 loadUser를 호출하여 유저 정보를 가져옴
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // 3. 로그 출력: 구글로부터 받은 원본 데이터 확인
        log.info("========================================");
        log.info("OAuth2 로그인 시도 - 서비스: {}", userRequest.getClientRegistration().getRegistrationId());
        log.info("Google Access Token: {}", userRequest.getAccessToken().getTokenValue());
        log.info("User Attributes: {}", oAuth2User.getAttributes());
        log.info("========================================");

        String accessToken = userRequest.getAccessToken().getTokenValue();
        String email = oAuth2User.getAttribute("email");

        // 4. 유저 존재 여부 확인 로그
        User user = userRepository.findByUserEmail(email)
                .orElseGet(() -> {
                    log.info("신규 유저 자동 가입 진행: {}", email);
                    User oAuthUser = User.createUser(email, null);
                    return userRepository.save(oAuthUser);
                });

        log.info("연동 대상 유저 확인 완료: {}", user.getUserEmail());

        // 5. EmailAccount 생성 및 저장
        EmailAccount account = EmailAccount.createEmailAccount(
                email,
                accessToken,
                "google",
                user
        );

        EmailAccount savedAccount = emailAccountRepository.save(account);
//        log.info("연동 성공: DB에 EmailAccount(ID: {}) 저장 완료", savedAccount.getId());

        return new PrincipalDetails(user, oAuth2User.getAttributes());
    }
}