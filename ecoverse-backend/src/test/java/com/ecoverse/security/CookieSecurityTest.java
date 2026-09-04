package com.ecoverse.security;

import com.ecoverse.util.CookieUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for cookie security (httpOnly refresh token):
 * - Cookie name is ecoverse_rt
 * - Cookie path is /api/auth (not root)
 * - Cookie has httpOnly flag
 * - Cookie has SameSite=Lax
 * - Cookie can be read from request
 * - Cookie can be cleared (Max-Age=0)
 */
class CookieSecurityTest {

    private CookieUtil cookieUtil;

    @BeforeEach
    void setUp() {
        cookieUtil = new CookieUtil();
        ReflectionTestUtils.setField(cookieUtil, "secureCookie", false); // false for testing
        ReflectionTestUtils.setField(cookieUtil, "cookieDomain", "");
    }

    @Nested
    @DisplayName("Cookie Creation")
    class Creation {

        @Test
        @DisplayName("setRefreshTokenCookie adds cookie with correct name")
        void setCookieAddsCorrectName() {
            MockHttpServletResponse response = new MockHttpServletResponse();
            cookieUtil.setRefreshTokenCookie(response, "test-refresh-token");

            Cookie[] cookies = response.getCookies();
            assertThat(cookies).isNotEmpty();
            assertThat(cookies[0].getName()).isEqualTo("ecoverse_rt");
        }

        @Test
        @DisplayName("setRefreshTokenCookie sets httpOnly flag")
        void setCookieSetsHttpOnly() {
            MockHttpServletResponse response = new MockHttpServletResponse();
            cookieUtil.setRefreshTokenCookie(response, "test-refresh-token");

            Cookie[] cookies = response.getCookies();
            assertThat(cookies[0].isHttpOnly()).isTrue();
        }

        @Test
        @DisplayName("setRefreshTokenCookie sets path to /api/auth")
        void setCookieSetsPathToApiAuth() {
            MockHttpServletResponse response = new MockHttpServletResponse();
            cookieUtil.setRefreshTokenCookie(response, "test-refresh-token");

            Cookie[] cookies = response.getCookies();
            assertThat(cookies[0].getPath()).isEqualTo("/api/auth");
        }

        @Test
        @DisplayName("setRefreshTokenCookie sets Max-Age to 7 days")
        void setCookieSetsMaxAge7Days() {
            MockHttpServletResponse response = new MockHttpServletResponse();
            cookieUtil.setRefreshTokenCookie(response, "test-refresh-token");

            Cookie[] cookies = response.getCookies();
            assertThat(cookies[0].getMaxAge()).isEqualTo(7 * 24 * 60 * 60); // 7 days in seconds
        }

        @Test
        @DisplayName("Set-Cookie header includes SameSite=Lax")
        void setCookieHeaderIncludesSameSiteLax() {
            MockHttpServletResponse response = new MockHttpServletResponse();
            cookieUtil.setRefreshTokenCookie(response, "test-refresh-token");

            // Check the Set-Cookie header (not the Cookie API)
            boolean hasSameSiteLax = response.getHeaders("Set-Cookie").stream()
                    .anyMatch(h -> h.contains("SameSite=Lax"));
            assertThat(hasSameSiteLax).isTrue();
        }
    }

    @Nested
    @DisplayName("Cookie Reading")
    class Reading {

        @Test
        @DisplayName("getRefreshTokenFromCookie returns token value when present")
        void getCookieReturnsTokenWhenPresent() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(new Cookie("ecoverse_rt", "my-refresh-token"));

            String token = cookieUtil.getRefreshTokenFromCookie(request);

            assertThat(token).isEqualTo("my-refresh-token");
        }

        @Test
        @DisplayName("getRefreshTokenFromCookie returns null when no cookies")
        void getCookieReturnsNullWhenNoCookies() {
            MockHttpServletRequest request = new MockHttpServletRequest();

            String token = cookieUtil.getRefreshTokenFromCookie(request);

            assertThat(token).isNull();
        }

        @Test
        @DisplayName("getRefreshTokenFromCookie returns null when cookie not found")
        void getCookieReturnsNullWhenNotFound() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(new Cookie("other_cookie", "value"));

            String token = cookieUtil.getRefreshTokenFromCookie(request);

            assertThat(token).isNull();
        }
    }

    @Nested
    @DisplayName("Cookie Clearing")
    class Clearing {

        @Test
        @DisplayName("clearRefreshTokenCookie sets Max-Age to 0")
        void clearCookieSetsMaxAgeZero() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            cookieUtil.clearRefreshTokenCookie(request, response);

            Cookie[] cookies = response.getCookies();
            assertThat(cookies).isNotEmpty();
            assertThat(cookies[0].getMaxAge()).isEqualTo(0);
            assertThat(cookies[0].getValue()).isEmpty();
        }

        @Test
        @DisplayName("Clear cookie header includes SameSite=Lax")
        void clearCookieHeaderIncludesSameSiteLax() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            cookieUtil.clearRefreshTokenCookie(request, response);

            boolean hasSameSiteLax = response.getHeaders("Set-Cookie").stream()
                    .anyMatch(h -> h.contains("SameSite=Lax"));
            assertThat(hasSameSiteLax).isTrue();
        }
    }
}
