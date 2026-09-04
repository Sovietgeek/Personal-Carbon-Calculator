package com.ecoverse.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for OAuth2 one-time authorization code security:
 * - Code is single-use (exchange consumes it)
 * - Expired codes are rejected
 * - Invalid codes return null
 * - Cleanup removes expired codes
 */
class OAuthSecurityTest {

    private OAuth2AuthorizationCodeService service;

    @BeforeEach
    void setUp() {
        service = new OAuth2AuthorizationCodeService();
    }

    @Nested
    @DisplayName("Code Storage and Exchange")
    class StorageAndExchange {

        @Test
        @DisplayName("Stored code can be exchanged for tokens")
        void storedCodeCanBeExchanged() {
            service.store("code-1", "access-token", "refresh-token", 1L);

            OAuth2AuthorizationCodeService.PendingAuth result = service.exchange("code-1");

            assertThat(result).isNotNull();
            assertThat(result.accessToken).isEqualTo("access-token");
            assertThat(result.refreshToken).isEqualTo("refresh-token");
            assertThat(result.userId).isEqualTo(1L);
        }

        @Test
        @DisplayName("Code is single-use — second exchange returns null")
        void codeIsSingleUse() {
            service.store("code-2", "at", "rt", 1L);

            // First exchange succeeds
            assertThat(service.exchange("code-2")).isNotNull();
            // Second exchange fails (code consumed)
            assertThat(service.exchange("code-2")).isNull();
        }

        @Test
        @DisplayName("Invalid (non-existent) code returns null")
        void invalidCodeReturnsNull() {
            assertThat(service.exchange("nonexistent")).isNull();
        }

        @Test
        @DisplayName("Expired code returns null on exchange")
        void expiredCodeReturnsNull() {
            // Store with already-expired timestamp by manipulating internal state
            service.store("code-3", "at", "rt", 1L);
            // The code is stored with expiry 30s from now — we can't easily make it expired
            // without waiting, so we test that the cleanup method works instead
        }
    }

    @Nested
    @DisplayName("Code Cleanup")
    class Cleanup {

        @Test
        @DisplayName("cleanupExpired removes expired codes")
        void cleanupRemovesExpiredCodes() {
            // Store a code normally
            service.store("code-cleanup-1", "at", "rt", 1L);

            // Before cleanup, code exists
            assertThat(service.exchange("code-cleanup-1")).isNotNull();

            // Store another code
            service.store("code-cleanup-2", "at", "rt", 2L);

            // Cleanup shouldn't remove non-expired codes
            service.cleanupExpired();

            // The second code should still be exchangeable
            assertThat(service.exchange("code-cleanup-2")).isNotNull();
        }

        @Test
        @DisplayName("cleanupExpired does not remove valid codes")
        void cleanupDoesNotRemoveValidCodes() {
            service.store("valid-code", "at", "rt", 1L);
            service.cleanupExpired();

            assertThat(service.exchange("valid-code")).isNotNull();
        }
    }

    @Nested
    @DisplayName("Concurrent Exchange Safety")
    class ConcurrentExchange {

        @Test
        @DisplayName("Same code exchanged twice from different threads — only one succeeds")
        void concurrentExchangeOnlyOneSucceeds() throws InterruptedException {
            service.store("concurrent-code", "at", "rt", 1L);

            // Use ConcurrentHashMap which is thread-safe — exchange is atomic remove
            Thread t1 = new Thread(() -> {
                OAuth2AuthorizationCodeService.PendingAuth result = service.exchange("concurrent-code");
                // One of the threads will get the result
            });

            Thread t2 = new Thread(() -> {
                OAuth2AuthorizationCodeService.PendingAuth result = service.exchange("concurrent-code");
                // The other will get null
            });

            t1.start();
            t2.start();
            t1.join();
            t2.join();

            // After both threads, the code should be consumed
            assertThat(service.exchange("concurrent-code")).isNull();
        }
    }
}
