package com.example.mailmoa.user.application.port;

public interface RefreshTokenPort {
    void saveRefreshToken(String userId, String refreshToken, long expirationMs);
    String getRefreshToken(String userId);
    void deleteRefreshToken(String userId);
}
