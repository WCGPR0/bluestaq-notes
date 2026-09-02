package com.bluestaq.notesapi.auth;

import com.bluestaq.notesapi.user.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET_A = "test-secret-a-0123456789abcdef0123456789abcdef";
    private static final String SECRET_B = "test-secret-b-fedcba9876543210fedcba9876543210";

    private JwtService serviceWithExpiration(long expirationSeconds) {
        return new JwtService(new JwtProperties(SECRET_A, expirationSeconds));
    }

    private User userWithId(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    @Test
    void generateToken_thenParseToken_roundTripsUserIdAndScopes() {
        JwtService jwtService = serviceWithExpiration(3600);
        User user = userWithId("user-123");

        String token = jwtService.generateToken(user);
        AuthenticatedUser authenticatedUser = jwtService.parseToken(token);

        assertEquals("user-123", authenticatedUser.userId());
        assertEquals(Set.of(Scopes.ALL.split(" ")), authenticatedUser.scopes());
    }

    @Test
    void parseToken_grantsAllScopes() {
        JwtService jwtService = serviceWithExpiration(3600);
        User user = userWithId("user-1");

        AuthenticatedUser authenticatedUser = jwtService.parseToken(jwtService.generateToken(user));

        assertTrue(authenticatedUser.hasScope(Scopes.PROFILE_READ));
        assertTrue(authenticatedUser.hasScope(Scopes.TEAMS_WRITE));
        assertFalse(authenticatedUser.hasScope("not:a-real-scope"));
    }

    @Test
    void getExpirationSeconds_returnsConfiguredValue() {
        JwtService jwtService = serviceWithExpiration(1800);

        assertEquals(1800L, jwtService.getExpirationSeconds());
    }

    @Test
    void parseToken_withExpiredToken_throwsJwtException() {
        JwtService jwtService = serviceWithExpiration(-60);
        User user = userWithId("user-1");
        String token = jwtService.generateToken(user);

        assertThrows(JwtException.class, () -> jwtService.parseToken(token));
    }

    @Test
    void parseToken_withTokenSignedByDifferentSecret_throwsJwtException() {
        JwtService issuer = serviceWithExpiration(3600);
        JwtService verifier = new JwtService(new JwtProperties(SECRET_B, 3600));
        User user = userWithId("user-1");
        String token = issuer.generateToken(user);

        assertThrows(JwtException.class, () -> verifier.parseToken(token));
    }

    @Test
    void parseToken_withTamperedToken_throwsJwtException() {
        // Flipping the very last base64url character can land on padding bits that don't change
        // the decoded signature bytes, so tamper a character in the middle of the signature segment
        // instead, which always changes the decoded bytes and must invalidate the signature.
        JwtService jwtService = serviceWithExpiration(3600);
        User user = userWithId("user-1");
        String token = jwtService.generateToken(user);
        int signatureStart = token.lastIndexOf('.') + 1;
        int tamperIndex = signatureStart + (token.length() - signatureStart) / 2;
        char original = token.charAt(tamperIndex);
        char replacement = original == 'a' ? 'b' : 'a';
        String tampered = token.substring(0, tamperIndex) + replacement + token.substring(tamperIndex + 1);

        assertThrows(JwtException.class, () -> jwtService.parseToken(tampered));
    }

    @Test
    void parseToken_withMalformedToken_throwsJwtException() {
        JwtService jwtService = serviceWithExpiration(3600);

        assertThrows(JwtException.class, () -> jwtService.parseToken("not-a-jwt"));
    }
}
