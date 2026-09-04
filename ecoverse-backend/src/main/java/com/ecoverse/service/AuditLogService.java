package com.ecoverse.service;

import com.ecoverse.model.AuditLog;
import com.ecoverse.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Async Audit Logging Service.
 * Logs security events without impacting API response times.
 */
@Service
public class AuditLogService {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);

    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * Async log a security event.
     * Does NOT block the calling thread.
     *
     * @param userId   the user who performed the action (null if anonymous)
     * @param action   what happened (LOGIN, LOGOUT, REGISTER, DELETE_ENTRY, etc.)
     * @param resource the API resource involved (/api/auth/login, /api/carbon/entries/5)
     * @param details  optional additional info
     */
    @Async
    public void log(Long userId, String action, String resource, String details) {
        try {
            String ipAddress = getClientIpAddress();
            String userAgent = getUserAgent();

            AuditLog auditLog = AuditLog.builder()
                    .userId(userId)
                    .action(action)
                    .resource(resource)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .details(details)
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            // Audit logging should NEVER break the application
            logger.error("Failed to create audit log: {}", e.getMessage());
        }
    }

    /**
     * Convenience method without details.
     */
    @Async
    public void log(Long userId, String action, String resource) {
        log(userId, action, resource, null);
    }

    /**
     * Extract client IP from the current request context.
     */
    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) return "unknown";

            HttpServletRequest request = attributes.getRequest();
            String[] headerNames = {
                "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP",
                "WL-Proxy-Client-IP", "HTTP_X_FORWARDED_FOR"
            };

            for (String header : headerNames) {
                String ip = request.getHeader(header);
                if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                    if (ip.contains(",")) ip = ip.split(",")[0].trim();
                    return ip;
                }
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Extract User-Agent from the current request context.
     */
    private String getUserAgent() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) return "unknown";

            HttpServletRequest request = attributes.getRequest();
            String userAgent = request.getHeader("User-Agent");
            return userAgent != null ? userAgent.substring(0, Math.min(userAgent.length(), 500)) : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}
