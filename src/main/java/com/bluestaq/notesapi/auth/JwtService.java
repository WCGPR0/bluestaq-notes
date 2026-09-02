package com.bluestaq.notesapi.auth;

import com.bluestaq.notesapi.user.Role;
import com.bluestaq.notesapi.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class JwtService {

    private static final String AUDIENCE = "notes-api";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.expirationSeconds());
        List<String> roleNames = user.getRoles().stream().map(Role::name).toList();

        return Jwts.builder()
                .subject(user.getId())
                .claim("aud", AUDIENCE)
                .claim("scope", Scopes.ALL)
                .claim("roles", roleNames)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public long getExpirationSeconds() {
        return properties.expirationSeconds();
    }

    public AuthenticatedUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String userId = claims.getSubject();
        List<?> roleNames = claims.get("roles", List.class);
        Set<Role> roles = roleNames.stream()
                .map(String.class::cast)
                .map(Role::valueOf)
                .collect(Collectors.toSet());
        String scopeClaim = claims.get("scope", String.class);
        Set<String> scopes = Set.of(scopeClaim.split(" "));

        return new AuthenticatedUser(userId, roles, scopes);
    }
}
