package com.ecoverse.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * CSRF Origin/Referer Validation Filter — Defense-in-depth for cookie-relying endpoints.
 *
 * ARCHITECTURE ANALYSIS:
 * =====================
 * This application uses a dual-token authentication pattern:
 * - Access token: sent in Authorization: Bearer header (NOT auto-attached by browser)
 * - Refresh token: stored in httpOnly cookie with SameSite=Lax
 *
 * CSRF is NOT feasible in this architecture because:
 * 1. SameSite=Lax: The refresh cookie is NOT sent on cross-origin POST/PUT/DELETE requests
 * 2. Bearer token: The access token is not auto-attached by the browser
 * 3. An attacker cannot construct a valid Authorization header from a different origin
 *
 * However, as defense-in-depth, this filter validates Origin/Referer headers on
 * endpoints that rely on the httpOnly cookie (refresh, logout). This protects against
 * theoretical SameSite bypasses or browser bugs.
 *
 * This filter does NOT protect the webhook endpoint (/api/payments/webhook) because:
 * - Webhooks use Razorpay's HMAC-SHA256 signature, not cookies
 * - The webhook endpoint has its own signature verification
 */
@Component
public class CsrfOriginValidationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CsrfOriginValidationFilter.class);

    /**
     * Endpoints that rely on httpOnly cookies and need Origin/Referer validation.
     * All other endpoints are protected by Bearer token (not vulnerable to CSRF).
     */
    private static final Set<String> COOKIE_RELYING_PATHS = Set.of(
            "/api/auth/refresh",
            "/api/auth/logout"
    );

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5500,http://127.0.0.1:5500,http://localhost:8081,http://127.0.0.1:8081}")
    private String allowedOrigins;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Only validate state-changing requests to cookie-relying endpoints
        if (!"OPTIONS".equals(method) && COOKIE_RELYING_PATHS.contains(path)) {
            String origin = request.getHeader("Origin");
            String referer = request.getHeader("Referer");

            if (origin != null) {
                if (!isAllowedOrigin(origin)) {
                    log.warn("CSRF validation failed: Origin [{}] not allowed for path [{}]", origin, path);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"message\":\"Invalid request origin\"}");
                    return;
                }
            } else if (referer != null) {
                // Extract origin from referer (e.g., "http://localhost:8081/page" → "http://localhost:8081")
                String refererOrigin = extractOrigin(referer);
                if (refererOrigin == null || !isAllowedOrigin(refererOrigin)) {
                    log.warn("CSRF validation failed: Referer [{}] not allowed for path [{}]", referer, path);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"message\":\"Invalid request origin\"}");
                    return;
                }
            }
            // If neither Origin nor Referer is present, we allow the request through.
            // SameSite=Lax provides the primary CSRF protection.
            // Some legitimate clients (e.g., API tools, server-to-server) may not send these headers.
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Check if the given origin is in the allowed origins list.
     */
    private boolean isAllowedOrigin(String origin) {
        if (origin == null || origin.isBlank()) return false;

        String[] origins = allowedOrigins.split(",");
        for (String allowed : origins) {
            if (origin.trim().equalsIgnoreCase(allowed.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract the origin (scheme + host + port) from a Referer URL.
     * E.g., "http://localhost:8081/some/page?query=1" → "http://localhost:8081"
     */
    private String extractOrigin(String referer) {
        try {
            int schemeEnd = referer.indexOf("://");
            if (schemeEnd < 0) return null;

            int pathStart = referer.indexOf('/', schemeEnd + 3);
            if (pathStart > 0) {
                return referer.substring(0, pathStart);
            }
            return referer; // No path — the whole thing is the origin
        } catch (Exception e) {
            return null;
        }
    }
}
