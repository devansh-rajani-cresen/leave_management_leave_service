package com.cresensolutions.leaveservice.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Date;

@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secretKey;

    // Extract all claims from JWT token
    private Claims getClaims(String token) {

        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(secretKey.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public Long extractUserId(String token) {

        Object userId = getClaims(token).get("userId");

        return userId != null
                ? Long.parseLong(userId.toString())
                : null;
    }

    public String extractEmail(String token) {
        return getClaims(token).get("emailId").toString();
    }

    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return getClaims(token).get("role").toString();
    }

    public String extractfullName(String token) {
        return getClaims(token).get("fullName").toString();
    }

    // Validate token expiration
    public boolean validateToken(String token) {
        return getClaims(token)
                .getExpiration()
                .after(new Date());
    }
}
