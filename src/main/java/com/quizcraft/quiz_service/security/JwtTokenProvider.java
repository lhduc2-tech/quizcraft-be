package com.quizcraft.quiz_service.security;

import com.quizcraft.quiz_service.entity.User;
import com.quizcraft.quiz_service.service.RedisBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final RedisBlacklistService redisBlacklistService;

    public JwtTokenProvider(RedisBlacklistService redisBlacklistService) {
        this.redisBlacklistService = redisBlacklistService;
    }

    private String getJwtSecret() {
        String secret = System.getProperty("JWT_SECRET");
        if (secret == null || secret.trim().isEmpty()) {
            secret = System.getenv("JWT_SECRET");
        }
        if (secret == null || secret.trim().isEmpty()) {
            // Default fallback key with at least 256 bits
            secret = "b7c9f1a4e2d8c3f9a6b5d4e1f8c7a2b9d3e6f1a0c4b8d9e2f7a6c3b1d5e8f9a3";
        }
        return secret.trim();
    }

    private long getJwtExpirationMs() {
        String exp = System.getProperty("JWT_EXPIRATION_MS");
        if (exp == null || exp.trim().isEmpty()) {
            exp = System.getenv("JWT_EXPIRATION_MS");
        }
        if (exp != null) {
            try {
                return Long.parseLong(exp.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return 86400000L; // 24 hours
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = getJwtSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + getJwtExpirationMs());

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername() != null ? user.getUsername() : "")
                .claim("email", user.getEmail() != null ? user.getEmail() : "")
                .claim("fullName", user.getFullName() != null ? user.getFullName() : "")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return Long.parseLong(claims.getSubject());
    }

    public long getRemainingSeconds(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            long remainingMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
            return Math.max(remainingMillis / 1000L, 0L);
        } catch (Exception e) {
            return 86400L; // Default 24h
        }
    }

    public boolean validateToken(String token) {
        try {
            // 1. Check Redis blacklist
            if (redisBlacklistService.isBlacklisted(token)) {
                log.warn("Token has been revoked/blacklisted in Redis");
                return false;
            }

            // 2. Verify signature and expiration
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);

            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT token: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("JWT validation error: {}", e.getMessage());
        }
        return false;
    }
}
