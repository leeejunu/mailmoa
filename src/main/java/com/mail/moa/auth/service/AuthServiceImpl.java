package com.mail.moa.auth.service;

import com.mail.moa.auth.dto.SignupRequestDto;
import com.mail.moa.domain.User;
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

    @Override
    @Transactional
    public void join(SignupRequestDto signupRequestDto) {
        validateDuplicateUser(signupRequestDto.userEmail());

        String encodedPassword = passwordEncoder.encode(signupRequestDto.password());
        User user = User.createUser(signupRequestDto.userEmail(), encodedPassword, signupRequestDto.name());

        userRepository.save(user);
    }

    private void validateDuplicateUser(String email) {
        userRepository.findByUserEmail(email).ifPresent(u -> {
            throw new IllegalStateException("이미 존재하는 회원입니다.");
        });
    }
}
