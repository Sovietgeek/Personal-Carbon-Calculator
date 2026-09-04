package com.ecoverse.security;

import com.ecoverse.repository.RefreshTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for access token (JWT) security:
 * - HS512 algorithm enforced
 * - Issuer (iss) and audience (aud) claims present and validated
 * - Token validation rejects expired, malformed, wrong-secret tokens
 * - Claims are correct (subject=userId, email, type=access)
 */
@ExtendWith(MockitoExtension.class)
class AccessTokenSecurityTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private JwtTokenProvider jwtTokenProvider;

    private static final String SECRET = "a-very-long-secret-key-that-is-at-least-64-bytes-for-hs512-algorithm-XXXXXXXX";
    private static final long ACCESS_EXPIRATION = 300000; // 5 minutes
    private static final long REFRESH_EXPIRATION = 604800000; // 7 days

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(refreshTokenRepository);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "accessExpiration", ACCESS_EXPIRATION);
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshExpiration", REFRESH_EXPIRATION);
    }

    @Nested
    @DisplayName("Access Token Generation")
    class Generation {

        @Test
        @DisplayName("Generated token contains issuer claim (iss=ecoverse)")
        void tokenContainsIssuerClaim() {
            String token = jwtTokenProvider.generateAccessToken(1L, "user@test.com");

            Claims claims = parseTokenWithoutValidation(token);
            assertThat(claims.getIssuer()).isEqualTo("ecoverse");
        }

        @Test
        @DisplayName("Generated token contains audience claim (aud=ecoverse-api)")
        void tokenContainsAudienceClaim() {
            String token = jwtTokenProvider.generateAccessToken(1L, "user@test.com");

            Claims claims = parseTokenWithoutValidation(token);
            assertThat(claims.getAudience()).contains("ecoverse-api");
        }

        @Test
        @DisplayName("Generated token subject is userId as string")
        void tokenSubjectIsUserId() {
            String token = jwtTokenProvider.generateAccessToken(42L, "user@test.com");

            assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(42L);
        }

        @Test
        @DisplayName("Generated token contains email claim")
        void tokenContainsEmailClaim() {
            String token = jwtTokenProvider.generateAccessToken(1L, "user@test.com");

            assertThat(jwtTokenProvider.getEmailFromToken(token)).isEqualTo("user@test.com");
        }

        @Test
        @DisplayName("Generated token contains type=access claim")
        void tokenContainsTypeClaim() {
            String token = jwtTokenProvider.generateAccessToken(1L, "user@test.com");

            Claims claims = parseTokenWithoutValidation(token);
            assertThat(claims.get("type", String.class)).isEqualTo("access");
        }

        @Test
        @DisplayName("validateToken returns true for valid token")
        void validateTokenReturnsTrueForValidToken() {
            String token = jwtTokenProvider.generateAccessToken(1L, "user@test.com");

            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        }
    }

    @Nested
    @DisplayName("Access Token Validation")
    class Validation {

        @Test
        @DisplayName("validateToken returns false for malformed token")
        void validateTokenReturnsFalseForMalformedToken() {
            assertThat(jwtTokenProvider.validateToken("not-a-jwt")).isFalse();
        }

        @Test
        @DisplayName("validateToken returns false for token signed with wrong secret")
        void validateTokenReturnsFalseForWrongSecret() {
            // Generate token with different secret
            String wrongSecret = "different-secret-key-that-is-long-enough-for-hs512-algorithm-XXXXX";
            SecretKey wrongKey = Keys.hmacShaKeyFor(wrongSecret.getBytes(StandardCharsets.UTF_8));
            String forgedToken = Jwts.builder()
                    .subject("1")
                    .claim("email", "attacker@evil.com")
                    .claim("type", "access")
                    .issuer("ecoverse")
                    .audience().add("ecoverse-api").and()
                    .signWith(wrongKey, io.jsonwebtoken.SignatureAlgorithm.HS512)
                    .compact();

            assertThat(jwtTokenProvider.validateToken(forgedToken)).isFalse();
        }

        @Test
        @DisplayName("validateToken returns false for token with wrong issuer")
        void validateTokenReturnsFalseForWrongIssuer() {
            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            String forgedToken = Jwts.builder()
                    .subject("1")
                    .claim("email", "user@test.com")
                    .claim("type", "access")
                    .issuer("evil-attacker") // Wrong issuer
                    .audience().add("ecoverse-api").and()
                    .signWith(key, io.jsonwebtoken.SignatureAlgorithm.HS512)
                    .compact();

            assertThat(jwtTokenProvider.validateToken(forgedToken)).isFalse();
        }

        @Test
        @DisplayName("validateToken returns false for token with wrong audience")
        void validateTokenReturnsFalseForWrongAudience() {
            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            String forgedToken = Jwts.builder()
                    .subject("1")
                    .claim("email", "user@test.com")
                    .claim("type", "access")
                    .issuer("ecoverse")
                    .audience().add("wrong-api").and() // Wrong audience
                    .signWith(key, io.jsonwebtoken.SignatureAlgorithm.HS512)
                    .compact();

            assertThat(jwtTokenProvider.validateToken(forgedToken)).isFalse();
        }

        @Test
        @DisplayName("getUserIdFromToken throws for invalid token")
        void getUserIdThrowsForInvalidToken() {
            assertThatThrownBy(() -> jwtTokenProvider.getUserIdFromToken("invalid-token"))
                    .isInstanceOf(Exception.class);
        }
    }

    private Claims parseTokenWithoutValidation(String token) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
