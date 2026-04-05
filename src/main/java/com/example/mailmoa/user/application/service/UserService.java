package com.example.mailmoa.user.application.service;

import com.example.mailmoa.global.util.JwtProvider;
import com.example.mailmoa.user.application.dto.LoginCommand;
import com.example.mailmoa.user.application.dto.LoginResult;
import com.example.mailmoa.user.application.dto.SignUpCommand;
import com.example.mailmoa.user.application.dto.SignUpResult;
import com.example.mailmoa.user.application.exception.DuplicateEmailException;
import com.example.mailmoa.user.application.exception.InvalidCredentialsException;
import com.example.mailmoa.user.application.port.RefreshTokenPort;
import com.example.mailmoa.user.application.usecase.UserUseCase;

import com.example.mailmoa.user.domain.model.User;
import com.example.mailmoa.user.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService implements UserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenPort refreshTokenPort;

    @Override
    @Transactional
    public SignUpResult signUp(SignUpCommand command) {
        if (userRepository.existsByEmail(command.email())) {
            throw new DuplicateEmailException();
        }

        User user = User.create(
                command.email(),
                passwordEncoder.encode(command.password()),
                command.name()
        );

        return SignUpResult.from(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResult login(LoginCommand command) {
        User user = userRepository.findByEmail(command.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(command.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String userId = user.getId().toString();
        String accessToken = jwtProvider.generateAccessToken(userId);
        String refreshToken = jwtProvider.generateRefreshToken(userId);

        refreshTokenPort.saveRefreshToken(userId, refreshToken, jwtProvider.getRefreshExpirationMs());

        return new LoginResult(accessToken);
    }
}
