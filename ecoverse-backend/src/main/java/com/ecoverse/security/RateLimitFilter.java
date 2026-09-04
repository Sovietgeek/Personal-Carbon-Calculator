package com.ecoverse.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Rate Limiting Filter — checks request rate per IP before processing.
 * Adds X-RateLimit-* headers to every response.
 * Returns 429 Too Many Requests when limit exceeded.
 *
 * Rate limit tiers:
 * - Login/Register: 5/min
 * - Password reset: 3/hour
 * - Token refresh: 30/min
 * - Resend verification: 5/min
 * - OAuth exchange: 10/min
 * - General API: 60/min
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String ipAddress = getClientIpAddress(request);
        String path = request.getRequestURI();
        boolean allowed = true;
        long remaining = 60;

        // Apply different rate limits based on endpoint
        if (path.startsWith("/api/auth/login") || path.startsWith("/api/auth/register")) {
            allowed = rateLimitService.allowLogin(ipAddress);
            remaining = rateLimitService.getLoginRemaining(ipAddress);
        } else if (path.startsWith("/api/auth/forgot-password") || path.startsWith("/api/auth/reset-password")) {
            allowed = rateLimitService.allowPasswordReset(ipAddress);
        } else if (path.startsWith("/api/auth/refresh")) {
            allowed = rateLimitService.allowRefresh(ipAddress);
            remaining = rateLimitService.getRefreshRemaining(ipAddress);
        } else if (path.startsWith("/api/auth/resend-verification")) {
            allowed = rateLimitService.allowResendVerification(ipAddress);
        } else if (path.startsWith("/api/auth/oauth2/exchange")) {
            allowed = rateLimitService.allowOAuthExchange(ipAddress);
        } else if (path.startsWith("/api/payments/create-order") || path.startsWith("/api/payments/retry")) {
            // Payment order creation: 10/min per IP
            allowed = rateLimitService.allowPaymentCreate(ipAddress);
        } else if (path.startsWith("/api/payments/verify")) {
            // Payment verification: 10/min per IP
            allowed = rateLimitService.allowPaymentVerify(ipAddress);
        } else if (path.startsWith("/api/payments/refund")) {
            // Refund requests: 5/min per IP
            allowed = rateLimitService.allowRefundRequest(ipAddress);
        } else if (path.startsWith("/api/payments/webhook")) {
            // Webhook endpoint: generous limit for Razorpay retries (100/min per IP)
            allowed = rateLimitService.allowWebhook(ipAddress);
        } else if (path.startsWith("/api/")) {
            allowed = rateLimitService.allowApiRequest(ipAddress);
            remaining = rateLimitService.getApiRemaining(ipAddress);
        }

        // Add rate limit headers to response
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));

        if (!allowed) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", "60");

            Map<String, Object> errorResponse = Map.of(
                "success", false,
                "message", "Too many requests. Please try again later.",
                "data", Map.of("retryAfterSeconds", 60)
            );

            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract client IP address, handling proxy headers.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED"
        };

        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Multiple IPs in X-Forwarded-For — take the first one
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }

        return request.getRemoteAddr();
    }
}
