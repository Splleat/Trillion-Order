package com.nhnacademy.order.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {
    private final SecretKey secretKey;
    private final Long expiration;

    public JwtUtil(@Value("${jwt.secret}") String secretKey, @Value("${jwt.expiration}") Long expiration) {
        this.secretKey = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    // 사용자 ID와 역할을 포함한 JWT 토큰 생성
    public String generateToken(Long userId, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .claim("userId", userId)             // 사용자 ID를 Claim에 추가
                .claim("role", role)                 // 사용자 역할을 Claim에 추가
                .claim(Claims.ISSUED_AT, now)           // 토큰 발행 시간
                .claim(Claims.EXPIRATION, expiryDate)   // 토큰 만료 시간
                .signWith(secretKey)                    // 비밀 키로 서명
                .compact();
    }

    // JWT 토큰에서 클레임 추출
    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // JWT 토큰에서 사용자 ID 추출
    public Long getUserId(String token) {
        return getClaims(token).get("userId", Long.class);
    }

    // JWT 토큰에서 사용자 역할 추출
    public String getUserRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    // JWT 토큰의 유효성 검사
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            log.debug("JWT 토큰 처리 오류: {}", e.getMessage());
            return false;
        }
    }
}
