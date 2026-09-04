package com.ecoverse.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Utility for creating and clearing httpOnly cookies for refresh token storage.
 *
 * SECURITY DESIGN:
 * - Refresh token stored in httpOnly/Secure/SameSite=Lax cookie
 * - JavaScript CANNOT access this cookie (XSS protection)
 * - SameSite=Lax prevents CSRF on POST requests (cookie not sent cross-origin)
 * - Access token stored in JS memory only (NOT sessionStorage/localStorage)
 * - Access token sent via Authorization: Bearer header
 */
@Component
public class CookieUtil {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "ecoverse_rt";
    private static final int REFRESH_TOKEN_MAX_AGE_SECONDS = 7 * 24 * 60 * 60; // 7 days

    @Value("${app.cookie.secure:true}")
    private boolean secureCookie;

    @Value("${app.cookie.domain:}")
    private String cookieDomain;

    /**
     * Set the refresh token as an httpOnly cookie on the response.
     *
     * @param response     the HTTP response to attach the cookie to
     * @param refreshToken the plaintext refresh token value
     */
    public void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookie);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(REFRESH_TOKEN_MAX_AGE_SECONDS);
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            cookie.setDomain(cookieDomain);
        }
        response.addCookie(cookie);

        // Also set SameSite attribute (Servlet Cookie API doesn't support it directly)
        // We use the Set-Cookie header approach for browsers that support SameSite
        String sameSiteHeader = String.format(
                "%s=%s; Path=/api/auth; Max-Age=%d; HttpOnly; SameSite=Lax%s",
                REFRESH_TOKEN_COOKIE_NAME,
                refreshToken,
                REFRESH_TOKEN_MAX_AGE_SECONDS,
                secureCookie ? "; Secure" : ""
        );
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            // Domain is not set via header for localhost compatibility
        }
        response.addHeader("Set-Cookie", sameSiteHeader);
    }

    /**
     * Clear the refresh token cookie (used during logout, password reset, account deletion).
     *
     * @param request  the HTTP request (to read the existing cookie)
     * @param response the HTTP response to clear the cookie on
     */
    public void clearRefreshTokenCookie(HttpServletRequest request, HttpServletResponse response) {
        // Clear via Servlet Cookie API
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookie);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(0); // Expire immediately
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            cookie.setDomain(cookieDomain);
        }
        response.addCookie(cookie);

        // Also clear via Set-Cookie header with SameSite
        String sameSiteHeader = String.format(
                "%s=; Path=/api/auth; Max-Age=0; HttpOnly; SameSite=Lax%s",
                REFRESH_TOKEN_COOKIE_NAME,
                secureCookie ? "; Secure" : ""
        );
        response.addHeader("Set-Cookie", sameSiteHeader);
    }

    /**
     * Read the refresh token from the httpOnly cookie.
     *
     * @param request the HTTP request
     * @return the refresh token value, or null if not present
     */
    public String getRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * Get the cookie name (for testing/reference).
     */
    public static String getCookieName() {
        return REFRESH_TOKEN_COOKIE_NAME;
    }
}
