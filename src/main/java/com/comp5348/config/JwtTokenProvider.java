package com.comp5348.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT Token Provider for generating and validating JWT tokens.
 * 
 * Provides methods to:
 * - Generate JWT tokens for authenticated users
 * - Extract username from JWT tokens
 * - Validate JWT tokens
 */
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expirationTime;

    public JwtTokenProvider(
            @Value("${jwt.secret:your-secret-key-change-this-in-production-at-least-32-characters-long}") String secret,
            @Value("${jwt.expiration:86400000}") long expirationTime) {
        // Ensure secret is at least 32 characters for HS256
        if (secret.length() < 32) {
            secret = secret + "0".repeat(Math.max(0, 32 - secret.length()));
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationTime = expirationTime; // Default: 24 hours in milliseconds
    }

    /**
     * Generate a JWT token for the given username.
     *
     * @param username the username to encode in the token
     * @return JWT token string
     */
    public String generateToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);

        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Extract username from JWT token.
     *
     * @param token JWT token string
     * @return username encoded in the token
     */
    public String getUsernameFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        return claims.getSubject();
    }

    /**
     * Validate JWT token.
     *
     * @param token JWT token string
     * @return true if token is valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extract all claims from JWT token.
     *
     * @param token JWT token string
     * @return Claims object
     */
    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

