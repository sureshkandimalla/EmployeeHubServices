package com.employeehub;

import com.employeehub.config.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The session token is the only thing standing between an anonymous request
 * and every API endpoint, so both halves (issue, verify) are covered —
 * including the negative paths, which must return null rather than throw.
 *
 * Secrets here are throwaway test strings, never the deployed app.jwt-secret.
 */
class JwtServiceTest {

    // HS256 needs >= 256 bits of key material.
    private static final String SECRET = "test-only-secret-key-for-unit-tests-0123456789";
    private static final String OTHER_SECRET = "a-completely-different-test-only-secret-key-9876543210";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET);
    }

    @Test
    @DisplayName("issueToken then verifyToken: round-trips the email as the token's subject")
    void roundTrip_preservesEmail() {
        String token = jwtService.issueToken("hr@example.com", "intellan");

        Claims claims = jwtService.verifyToken(token);

        assertNotNull(claims);
        assertEquals("hr@example.com", claims.getSubject());
    }

    @Test
    @DisplayName("issueToken then verifyToken: round-trips the tenant claim")
    void roundTrip_preservesTenant() {
        String token = jwtService.issueToken("hr@example.com", "intellan");

        Claims claims = jwtService.verifyToken(token);

        assertEquals("intellan", claims.get("tenant", String.class));
    }

    @Test
    @DisplayName("issueToken: sets an expiry 12 hours after issue")
    void issueToken_expiresIn12Hours() {
        String token = jwtService.issueToken("hr@example.com", "intellan");

        Claims claims = jwtService.verifyToken(token);
        long lifetimeMillis = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();

        assertEquals(12L * 60 * 60 * 1000, lifetimeMillis);
    }

    @Test
    @DisplayName("issueToken: the token is not already expired when issued")
    void issueToken_isNotImmediatelyExpired() {
        String token = jwtService.issueToken("hr@example.com", "intellan");

        Claims claims = jwtService.verifyToken(token);

        assertTrue(claims.getExpiration().after(new Date()));
    }

    @Test
    @DisplayName("verifyToken: returns null for a token signed with a different secret")
    void verifyToken_rejectsTokenFromAnotherSecret() {
        String foreignToken = new JwtService(OTHER_SECRET).issueToken("attacker@example.com", "intellan");

        assertNull(jwtService.verifyToken(foreignToken));
    }

    @Test
    @DisplayName("verifyToken: returns null when the payload has been tampered with")
    void verifyToken_rejectsTamperedPayload() {
        String token = jwtService.issueToken("hr@example.com", "intellan");
        String[] parts = token.split("\\.");
        String tamperedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"admin@example.com\",\"tenant\":\"intellan\"}".getBytes());

        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertNull(jwtService.verifyToken(tampered));
    }

    @Test
    @DisplayName("verifyToken: returns null for a structurally malformed token")
    void verifyToken_rejectsMalformedToken() {
        assertNull(jwtService.verifyToken("this.is.not-a-jwt"));
    }

    @Test
    @DisplayName("verifyToken: returns null for an empty token")
    void verifyToken_rejectsEmptyToken() {
        assertNull(jwtService.verifyToken(""));
    }

    @Test
    @DisplayName("verifyToken: returns null for a null token rather than throwing")
    void verifyToken_rejectsNullToken() {
        assertNull(jwtService.verifyToken(null));
    }

    @Test
    @DisplayName("verifyToken: returns null for an unsigned (alg=none) token")
    void verifyToken_rejectsUnsignedToken() {
        String unsigned = io.jsonwebtoken.Jwts.builder()
                .setSubject("admin@example.com")
                .claim("tenant", "intellan")
                .compact();

        assertNull(jwtService.verifyToken(unsigned));
    }

    @Test
    @DisplayName("issueToken: two different users get two different tokens")
    void issueToken_differsPerSubject() {
        String a = jwtService.issueToken("a@example.com", "intellan");
        String b = jwtService.issueToken("b@example.com", "intellan");

        assertNotEquals(a, b);
    }
}
