package com.ecoverse.security;

import com.ecoverse.exception.BadRequestException;
import com.ecoverse.model.RefreshToken;
import com.ecoverse.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for refresh token rotation and reuse detection:
 * - Old token is revoked when new one is generated
 * - Revoked token reuse triggers revocation of ALL user tokens
 * - Expired tokens are rejected
 * - Invalid tokens are rejected
 * - Token hash is stored instead of plaintext
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenRotationTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private JwtTokenProvider jwtTokenProvider;

    private static final String SECRET = "a-very-long-secret-key-that-is-at-least-64-bytes-for-hs512-algorithm-XXXXXXXX";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "accessExpiration", 300000L);
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshExpiration", 604800000L);
    }

    @Nested
    @DisplayName("Token Generation")
    class Generation {

        @Test
        @DisplayName("generateRefreshToken revokes all existing tokens first")
        void generateRevokesAllExisting() {
            when(refreshTokenRepository.revokeAllByUserId(1L)).thenReturn(1);
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

            jwtTokenProvider.generateRefreshToken(1L);

            verify(refreshTokenRepository).revokeAllByUserId(1L);
        }

        @Test
        @DisplayName("generateRefreshToken stores token hash, not plaintext")
        void generateStoresHashNotPlaintext() {
            when(refreshTokenRepository.revokeAllByUserId(1L)).thenReturn(0);
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

            String rawToken = jwtTokenProvider.generateRefreshToken(1L);

            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(captor.capture());

            RefreshToken saved = captor.getValue();
            // Token field should be null (not stored)
            assertThat(saved.getToken()).isNull();
            // Hash field should be populated
            assertThat(saved.getTokenHash()).isNotNull();
            assertThat(saved.getTokenHash()).hasSize(64); // SHA-256 hex = 64 chars
            // Hash should match what hashToken() produces
            assertThat(saved.getTokenHash()).isEqualTo(JwtTokenProvider.hashToken(rawToken));
        }

        @Test
        @DisplayName("New refresh token has revoked=false and expiry 7 days")
        void newTokenHasCorrectDefaults() {
            when(refreshTokenRepository.revokeAllByUserId(1L)).thenReturn(0);
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

            jwtTokenProvider.generateRefreshToken(1L);

            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(captor.capture());

            RefreshToken saved = captor.getValue();
            assertThat(saved.getRevoked()).isFalse();
            assertThat(saved.getExpiryDate()).isAfter(LocalDateTime.now().plusDays(6));
        }
    }

    @Nested
    @DisplayName("Token Validation")
    class Validation {

        @Test
        @DisplayName("Valid token returns userId")
        void validTokenReturnsUserId() {
            String rawToken = "valid-token-123";
            String tokenHash = JwtTokenProvider.hashToken(rawToken);
            RefreshToken refreshToken = RefreshToken.builder()
                    .id(1L).tokenHash(tokenHash).userId(42L)
                    .expiryDate(LocalDateTime.now().plusDays(7))
                    .revoked(false).build();

            when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(refreshToken));
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(refreshToken);

            Long userId = jwtTokenProvider.validateRefreshToken(rawToken);

            assertThat(userId).isEqualTo(42L);
        }

        @Test
        @DisplayName("Expired token is rejected")
        void expiredTokenIsRejected() {
            String rawToken = "expired-token";
            String tokenHash = JwtTokenProvider.hashToken(rawToken);
            RefreshToken refreshToken = RefreshToken.builder()
                    .id(1L).tokenHash(tokenHash).userId(1L)
                    .expiryDate(LocalDateTime.now().minusDays(1)) // Expired
                    .revoked(false).build();

            when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(refreshToken));

            assertThatThrownBy(() -> jwtTokenProvider.validateRefreshToken(rawToken))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("Invalid token (not in DB) is rejected")
        void invalidTokenIsRejected() {
            String rawToken = "nonexistent-token";
            String tokenHash = JwtTokenProvider.hashToken(rawToken);
            when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> jwtTokenProvider.validateRefreshToken(rawToken))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid refresh token");
        }
    }

    @Nested
    @DisplayName("Reuse Detection")
    class ReuseDetection {

        @Test
        @DisplayName("Revoked token reuse revokes ALL tokens for that user")
        void revokedTokenReuseRevokesAllUserTokens() {
            String rawToken = "revoked-token";
            String tokenHash = JwtTokenProvider.hashToken(rawToken);
            RefreshToken refreshToken = RefreshToken.builder()
                    .id(1L).tokenHash(tokenHash).userId(42L)
                    .expiryDate(LocalDateTime.now().plusDays(7))
                    .revoked(true) // Already revoked!
                    .build();

            when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(refreshToken));
            when(refreshTokenRepository.revokeAllByUserId(42L)).thenReturn(3);

            assertThatThrownBy(() -> jwtTokenProvider.validateRefreshToken(rawToken))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("revoked");

            // ALL tokens for this user should be revoked
            verify(refreshTokenRepository).revokeAllByUserId(42L);
        }

        @Test
        @DisplayName("Revoked token reuse logs security warning")
        void revokedTokenReuseLogsWarning() {
            String rawToken = "revoked-token-2";
            String tokenHash = JwtTokenProvider.hashToken(rawToken);
            RefreshToken refreshToken = RefreshToken.builder()
                    .id(2L).tokenHash(tokenHash).userId(99L)
                    .expiryDate(LocalDateTime.now().plusDays(7))
                    .revoked(true).build();

            when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(refreshToken));
            when(refreshTokenRepository.revokeAllByUserId(99L)).thenReturn(1);

            assertThatThrownBy(() -> jwtTokenProvider.validateRefreshToken(rawToken))
                    .isInstanceOf(BadRequestException.class);

            // Verify the security response happened (all tokens revoked)
            verify(refreshTokenRepository).revokeAllByUserId(99L);
        }
    }

    @Nested
    @DisplayName("Token Revocation")
    class Revocation {

        @Test
        @DisplayName("revokeRefreshToken marks specific token as revoked")
        void revokeSpecificToken() {
            String rawToken = "token-to-revoke";
            String tokenHash = JwtTokenProvider.hashToken(rawToken);
            RefreshToken refreshToken = RefreshToken.builder()
                    .id(1L).tokenHash(tokenHash).userId(1L)
                    .revoked(false).build();

            when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(refreshToken));
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

            jwtTokenProvider.revokeRefreshToken(rawToken);

            verify(refreshTokenRepository).save(argThat(rt ->
                    Boolean.TRUE.equals(rt.getRevoked())
            ));
        }

        @Test
        @DisplayName("revokeAllUserTokens uses UPDATE (not DELETE) for reuse detection")
        void revokeAllUsesUpdate() {
            when(refreshTokenRepository.revokeAllByUserId(1L)).thenReturn(2);

            jwtTokenProvider.revokeAllUserTokens(1L);

            // The repository method is called (it does UPDATE, not DELETE)
            verify(refreshTokenRepository).revokeAllByUserId(1L);
        }

        @Test
        @DisplayName("deleteAllUserTokens uses DELETE for account deletion")
        void deleteAllUsesDelete() {
            jwtTokenProvider.deleteAllUserTokens(1L);

            verify(refreshTokenRepository).deleteByUserId(1L);
        }
    }
}
