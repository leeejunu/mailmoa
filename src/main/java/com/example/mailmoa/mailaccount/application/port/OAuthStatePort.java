package com.example.mailmoa.mailaccount.application.port;

public interface OAuthStatePort {
    void saveState(String state, String userId);
    String getUserId(String state);
    void deleteState(String state);
}
