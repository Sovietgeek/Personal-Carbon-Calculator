package com.ecoverse.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Request ID Filter — adds a unique correlation ID to every request.
 *
 * HOW IT WORKS:
 * - Generates a UUID for each incoming request
 * - Sets it as the X-Request-Id response header (so clients can trace)
 * - Puts it in MDC (Mapped Diagnostic Context) so all log statements include it
 * - Accepts X-Request-Id from incoming request if provided (for distributed tracing)
 *
 * USAGE IN LOGS:
 * - Configure logging pattern to include %X{requestId} for MDC inclusion
 * - All log statements within the request scope will include the request ID
 *
 * OBSERVABILITY:
 * - Payment operations: orderId, amount, and status transitions are logged with request ID
 * - Auth events: login failures, locked accounts logged with request ID
 * - API errors: all errors logged with request ID for correlation
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Use provided request ID or generate a new one
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        // Set in MDC for structured logging
        MDC.put(MDC_KEY, requestId);

        // Set in response header for client-side correlation
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Always clean up MDC to prevent memory leaks
            MDC.remove(MDC_KEY);
        }
    }
}
