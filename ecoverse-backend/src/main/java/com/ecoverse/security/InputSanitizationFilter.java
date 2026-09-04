package com.ecoverse.security;

import com.ecoverse.util.InputSanitizer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Input Sanitization Filter — Checks ALL incoming requests for malicious content.
 * Blocks requests containing XSS, SQL injection, or path traversal patterns.
 *
 * Uses the canonical com.ecoverse.util.InputSanitizer utility class.
 */
@Component
public class InputSanitizationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InputSanitizationFilter.class);

    /** Paths whose query strings are not checked (controllers handle their own validation). */
    private static final Set<String> QUERY_ALLOWLIST = Set.of("/api/ai/stream");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Check URL path for path traversal and injection
        if (InputSanitizer.containsDangerousContent(path)) {
            log.warn("Blocked malicious request path: {}", path);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Invalid request.\",\"data\":null}");
            return;
        }

        // Check query parameters for XSS and injection
        // Skip allowlisted paths where controllers handle their own validation
        String queryString = request.getQueryString();
        if (queryString != null && !QUERY_ALLOWLIST.contains(path)
                && InputSanitizer.containsDangerousContent(queryString)) {
            log.warn("Blocked malicious query string from IP: {}", request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Invalid request parameters.\",\"data\":null}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
