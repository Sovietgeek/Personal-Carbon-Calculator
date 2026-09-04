package com.ecoverse.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for InputSanitizer URL validation methods.
 *
 * Verifies:
 * - http:// and https:// URLs are accepted
 * - javascript: URLs are rejected
 * - data: URLs are rejected
 * - null/empty returns null (safe — no image)
 * - Non-URL strings are rejected
 */
class ImageUrlValidatorTest {

    @Test
    @DisplayName("http:// URL is valid")
    void httpUrlIsValid() {
        assertThat(InputSanitizer.validateImageUrl("http://example.com/image.png")).isEqualTo("http://example.com/image.png");
    }

    @Test
    @DisplayName("https:// URL is valid")
    void httpsUrlIsValid() {
        assertThat(InputSanitizer.validateImageUrl("https://example.com/image.png")).isEqualTo("https://example.com/image.png");
    }

    @Test
    @DisplayName("javascript: URL is rejected (returns null)")
    void javascriptUrlIsRejected() {
        assertThat(InputSanitizer.validateImageUrl("javascript:alert('xss')")).isNull();
    }

    @Test
    @DisplayName("data: URL is rejected (returns null)")
    void dataUrlIsRejected() {
        assertThat(InputSanitizer.validateImageUrl("data:text/html,<script>alert('xss')</script>")).isNull();
    }

    @Test
    @DisplayName("vbscript: URL is rejected (returns null)")
    void vbscriptUrlIsRejected() {
        assertThat(InputSanitizer.validateImageUrl("vbscript:msgbox")).isNull();
    }

    @Test
    @DisplayName("null URL returns null")
    void nullUrlReturnsNull() {
        assertThat(InputSanitizer.validateImageUrl(null)).isNull();
    }

    @Test
    @DisplayName("empty URL returns null")
    void emptyUrlReturnsNull() {
        assertThat(InputSanitizer.validateImageUrl("")).isNull();
    }

    @Test
    @DisplayName("blank URL returns null")
    void blankUrlReturnsNull() {
        assertThat(InputSanitizer.validateImageUrl("   ")).isNull();
    }

    @Test
    @DisplayName("Non-URL string returns null")
    void nonUrlStringReturnsNull() {
        assertThat(InputSanitizer.validateImageUrl("not-a-url")).isNull();
    }

    @Test
    @DisplayName("isUrlSafe returns true for http/https")
    void isUrlSafeTrueForHttpHttps() {
        assertThat(InputSanitizer.isUrlSafe("http://example.com")).isTrue();
        assertThat(InputSanitizer.isUrlSafe("https://example.com")).isTrue();
    }

    @Test
    @DisplayName("isUrlSafe returns false for javascript/data")
    void isUrlSafeFalseForDangerous() {
        assertThat(InputSanitizer.isUrlSafe("javascript:alert(1)")).isFalse();
        assertThat(InputSanitizer.isUrlSafe("data:text/html,<h1>hi</h1>")).isFalse();
    }

    @Test
    @DisplayName("isUrlSafe returns true for null/empty")
    void isUrlSafeTrueForNullOrEmpty() {
        assertThat(InputSanitizer.isUrlSafe(null)).isTrue();
        assertThat(InputSanitizer.isUrlSafe("")).isTrue();
        assertThat(InputSanitizer.isUrlSafe("  ")).isTrue();
    }
}
