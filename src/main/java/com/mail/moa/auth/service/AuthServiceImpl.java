package com.mail.moa.auth.service;

import com.mail.moa.auth.dto.SigninRequestDto;
import com.mail.moa.auth.dto.SignupRequestDto;
import com.mail.moa.domain.User;
import com.mail.moa.security.jwt.JwtTokenProvider;
import com.mail.moa.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService{

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional
    public void join(SignupRequestDto signupRequestDto) {
        validateDuplicateUser(signupRequestDto.userEmail());

        String encodedPassword = passwordEncoder.encode(signupRequestDto.password());
        User user = User.createUser(signupRequestDto.userEmail(), encodedPassword, signupRequestDto.name());

        userRepository.save(user);
    }

    @Transactional
    public String login(SigninRequestDto request) {
        // 1. 이메일 존재 확인
        User user = userRepository.findByUserEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 이메일입니다."));

        // 2. 비밀번호 일치 확인 (matches 메서드 필수)
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 3. JWT 토큰 생성 및 반환
        return jwtTokenProvider.createToken(user.getUserEmail());
    }

    private void validateDuplicateUser(String email) {
        userRepository.findByUserEmail(email).ifPresent(u -> {
            throw new IllegalStateException("이미 존재하는 회원입니다.");
        });
    }
}
