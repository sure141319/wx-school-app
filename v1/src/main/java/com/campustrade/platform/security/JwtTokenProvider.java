package com.campustrade.platform.security;

import com.campustrade.platform.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final AppProperties appProperties;

    public JwtTokenProvider(AppProperties appProperties) {
        this.appProperties = appProperties;
        byte[] keyBytes = resolveKeyBytes(appProperties.getJwtSecret().trim());
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes (256 bits).");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createToken(Long userId, String email) {
        Instant now = Instant.now();
        Instant expiration = now.plus(appProperties.getJwtExpirationMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(key)
                .compact();
    }

    public UserPrincipal parseToken(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        Long userId = Long.parseLong(claims.getSubject());
        String email = claims.get("email", String.class);
        return new UserPrincipal(userId, email);
    }

    private byte[] resolveKeyBytes(String secret) {
        try {
            byte[] decoded = Decoders.BASE64.decode(secret);
            if (decoded.length > 0) {
                return decoded;
            }
        } catch (RuntimeException ignored) {
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}

