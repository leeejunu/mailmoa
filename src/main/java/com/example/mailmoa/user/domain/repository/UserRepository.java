package com.example.mailmoa.user.domain.repository;

import com.example.mailmoa.user.domain.model.User;

import java.util.Optional;

public interface UserRepository {
    User save(User user);

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);
}
