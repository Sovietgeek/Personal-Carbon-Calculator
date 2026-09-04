package com.ecoverse.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for refresh token SHA-256 hashing:
 * - hashToken produces consistent SHA-256 hex output
 * - Same input always produces same hash
 * - Different inputs produce different hashes
 * - Original token cannot be recovered from hash
 */
class RefreshTokenHashTest {

    @Test
    @DisplayName("hashToken produces 64-character lowercase hex string (SHA-256)")
    void hashTokenProduces64CharHex() {
        String token = "550e8400-e29b-41d4-a716-446655440000";
        String hash = JwtTokenProvider.hashToken(token);

        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("Same token always produces same hash (deterministic)")
    void hashTokenIsDeterministic() {
        String token = "550e8400-e29b-41d4-a716-446655440000";
        String hash1 = JwtTokenProvider.hashToken(token);
        String hash2 = JwtTokenProvider.hashToken(token);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("Different tokens produce different hashes")
    void differentTokensDifferentHashes() {
        String token1 = "550e8400-e29b-41d4-a716-446655440000";
        String token2 = "650e8400-e29b-41d4-a716-446655440001";

        String hash1 = JwtTokenProvider.hashToken(token1);
        String hash2 = JwtTokenProvider.hashToken(token2);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("hashToken matches manual SHA-256 computation")
    void hashTokenMatchesManualComputation() throws Exception {
        String token = "test-token-value";
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] expectedBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
        String expectedHash = HexFormat.of().formatHex(expectedBytes);

        String actualHash = JwtTokenProvider.hashToken(token);

        assertThat(actualHash).isEqualTo(expectedHash);
    }
}
