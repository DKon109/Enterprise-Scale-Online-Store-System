package com.comp5348.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(
                "your-secret-key-change-this-in-production-at-least-32-characters-long",
                86400000);
    }

    @Test
    void generateTokenCreatesValidToken() {
        String username = "testuser";
        String token = tokenProvider.generateToken(username);

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void getUsernameFromTokenExtractsCorrectUsername() {
        String username = "testuser";
        String token = tokenProvider.generateToken(username);

        String extractedUsername = tokenProvider.getUsernameFromToken(token);

        assertEquals(username, extractedUsername);
    }

    @Test
    void validateTokenReturnsTrueForValidToken() {
        String token = tokenProvider.generateToken("testuser");

        assertTrue(tokenProvider.validateToken(token));
    }

    @Test
    void validateTokenReturnsFalseForInvalidToken() {
        String invalidToken = "invalid.token.here";

        assertFalse(tokenProvider.validateToken(invalidToken));
    }

    @Test
    void validateTokenReturnsFalseForEmptyToken() {
        assertFalse(tokenProvider.validateToken(""));
    }

    @Test
    void generateTokenWithDifferentUsersCreatesDifferentTokens() {
        String token1 = tokenProvider.generateToken("user1");
        String token2 = tokenProvider.generateToken("user2");

        assertNotEquals(token1, token2);
    }
}

