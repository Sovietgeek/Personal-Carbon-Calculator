package com.ecoverse.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized Email Notification Service.
 *
 * SECURITY:
 * - All emails sent asynchronously (never blocks API response)
 * - No secrets, tokens, or passwords in email content
 * - No duplicate notifications from duplicate webhooks (tracked via sentEventIds)
 * - Retry handling: logged on failure, never throws to caller
 * - Email failure never breaks business logic
 *
 * EMAILS SENT:
 * - Email verification (existing, from AuthService)
 * - Password reset (existing, from AuthService)
 * - Payment confirmation
 * - Payment failure
 * - Order confirmation
 * - Order shipped
 * - Order delivered
 * - Refund completed
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.from-address:noreply@ecoverse.app}")
    private String fromAddress;

    @Value("${app.email.notifications-enabled:true}")
    private boolean notificationsEnabled;

    /**
     * Track recently sent event IDs to prevent duplicate emails from duplicate webhooks.
     * Key: eventType + ":" + entityId (e.g., "payment.confirmed:order_123")
     * Value: timestamp when the email was sent
     */
    private final Map<String, Long> sentEventIds = new ConcurrentHashMap<>();

    private static final long DEDUP_WINDOW_MS = 60_000; // 1 minute dedup window

    // ================================================================
    // EMAIL VERIFICATION (called from AuthService)
    // ================================================================

    /**
     * Send email verification link.
     * This is NOT deduplicated — each call sends a new verification email.
     */
    @Async
    public void sendVerificationEmail(String toEmail, String token) {
        if (!notificationsEnabled) {
            log.debug("Email notifications disabled — skipping verification email to {}", toEmail);
            return;
        }
        try {
            String subject = "EcoVerse — Verify Your Email";
            String verificationUrl = String.format("%s/?verify_token=%s",
                    getAppBaseUrl(), token);
            String html = buildTemplate(subject,
                    "Welcome to EcoVerse!",
                    "Please verify your email address to activate your account.",
                    "Verify Email", verificationUrl,
                    "This link expires in 24 hours. If you did not create an account, please ignore this email.");
            sendHtmlEmail(toEmail, subject, html);
        } catch (Exception e) {
            log.warn("Failed to send verification email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ================================================================
    // PASSWORD RESET (called from AuthService)
    // ================================================================

    /**
     * Send password reset link.
     */
    @Async
    public void sendPasswordResetEmail(String toEmail, String token) {
        if (!notificationsEnabled) {
            log.debug("Email notifications disabled — skipping password reset email to {}", toEmail);
            return;
        }
        try {
            String subject = "EcoVerse — Reset Your Password";
            String resetUrl = String.format("%s/?reset_token=%s",
                    getAppBaseUrl(), token);
            String html = buildTemplate(subject,
                    "Password Reset Request",
                    "We received a request to reset your password.",
                    "Reset Password", resetUrl,
                    "This link expires in 1 hour. If you did not request a password reset, please ignore this email.");
            sendHtmlEmail(toEmail, subject, html);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ================================================================
    // PAYMENT NOTIFICATIONS
    // ================================================================

    /**
     * Send payment confirmation email (after successful payment verification).
     * Deduplicated by orderId to prevent duplicate emails from webhook + verify.
     */
    @Async
    public void sendPaymentConfirmation(String toEmail, Long orderId, String amount) {
        sendDeduplicated("payment.confirmed", String.valueOf(orderId), toEmail,
                "EcoVerse — Payment Confirmed",
                "Payment Confirmed!",
                "Your payment of " + amount + " for order #" + orderId + " has been confirmed.",
                null, null,
                "Thank you for your purchase!");
    }

    /**
     * Send payment failure notification.
     */
    @Async
    public void sendPaymentFailure(String toEmail, Long orderId, String amount) {
        sendDeduplicated("payment.failed", String.valueOf(orderId), toEmail,
                "EcoVerse — Payment Failed",
                "Payment Failed",
                "Your payment of " + amount + " for order #" + orderId + " could not be processed.",
                null, null,
                "Please try again or use a different payment method.");
    }

    // ================================================================
    // ORDER NOTIFICATIONS
    // ================================================================

    /**
     * Send order confirmation email.
     */
    @Async
    public void sendOrderConfirmation(String toEmail, Long orderId, String total) {
        sendDeduplicated("order.confirmed", String.valueOf(orderId), toEmail,
                "EcoVerse — Order #" + orderId + " Confirmed",
                "Order Confirmed!",
                "Your order #" + orderId + " for " + total + " has been placed successfully.",
                null, null,
                "You can track your order in the EcoVerse app.");
    }

    /**
     * Send order shipped notification.
     */
    @Async
    public void sendOrderShipped(String toEmail, Long orderId) {
        sendDeduplicated("order.shipped", String.valueOf(orderId), toEmail,
                "EcoVerse — Order #" + orderId + " Shipped",
                "Order Shipped!",
                "Your order #" + orderId + " is on its way!",
                null, null,
                "Track your delivery in the EcoVerse app.");
    }

    /**
     * Send order delivered notification.
     */
    @Async
    public void sendOrderDelivered(String toEmail, Long orderId) {
        sendDeduplicated("order.delivered", String.valueOf(orderId), toEmail,
                "EcoVerse — Order #" + orderId + " Delivered",
                "Order Delivered!",
                "Your order #" + orderId + " has been delivered. We hope you enjoy your eco-friendly purchase!",
                null, null,
                "Thank you for choosing sustainability!");
    }

    // ================================================================
    // REFUND NOTIFICATIONS
    // ================================================================

    /**
     * Send refund completed notification.
     */
    @Async
    public void sendRefundCompleted(String toEmail, Long orderId, String refundAmount) {
        sendDeduplicated("refund.completed", String.valueOf(orderId), toEmail,
                "EcoVerse — Refund Processed for Order #" + orderId,
                "Refund Processed",
                "A refund of " + refundAmount + " for order #" + orderId + " has been processed.",
                null, null,
                "The refund will reflect in your account within 5-7 business days.");
    }

    // ================================================================
    // CORE EMAIL SENDING
    // ================================================================

    /**
     * Send an HTML email. All emails are async and failures are logged, not thrown.
     */
    private void sendHtmlEmail(String toEmail, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true = HTML
            mailSender.send(message);
            log.info("Email sent: [{}] to {}", subject, maskEmail(toEmail));
        } catch (MessagingException e) {
            log.warn("Failed to construct email [{}] to {}: {}", subject, maskEmail(toEmail), e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to send email [{}] to {}: {}", subject, maskEmail(toEmail), e.getMessage());
        }
    }

    /**
     * Send a deduplicated email. If the same event was sent recently (within DEDUP_WINDOW_MS),
     * skip sending. This prevents duplicate emails from webhook + verify race conditions.
     */
    private void sendDeduplicated(String eventType, String entityId, String toEmail,
                                   String subject, String title, String message,
                                   String ctaText, String ctaUrl, String footer) {
        if (!notificationsEnabled) {
            log.debug("Email notifications disabled — skipping {} email to {}", eventType, maskEmail(toEmail));
            return;
        }

        String dedupKey = eventType + ":" + entityId;
        Long lastSent = sentEventIds.get(dedupKey);
        if (lastSent != null && (System.currentTimeMillis() - lastSent) < DEDUP_WINDOW_MS) {
            log.debug("Skipping duplicate email for {} — already sent within {}ms", dedupKey, DEDUP_WINDOW_MS);
            return;
        }

        try {
            String html = buildTemplate(subject, title, message, ctaText, ctaUrl, footer);
            sendHtmlEmail(toEmail, subject, html);
            sentEventIds.put(dedupKey, System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("Failed to send {} email to {}: {}", eventType, maskEmail(toEmail), e.getMessage());
        }

        // Clean up old dedup entries periodically
        if (sentEventIds.size() > 1000) {
            long cutoff = System.currentTimeMillis() - DEDUP_WINDOW_MS;
            sentEventIds.entrySet().removeIf(e -> e.getValue() < cutoff);
        }
    }

    // ================================================================
    // EMAIL TEMPLATE BUILDER
    // ================================================================

    /**
     * Build a simple HTML email template.
     * No secrets, tokens, or passwords are included in the email body.
     * Tokens are only in the verification/reset URLs (standard practice).
     */
    private String buildTemplate(String subject, String title, String message,
                                  String ctaText, String ctaUrl, String footer) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body ");
        sb.append("style='margin:0;padding:0;background:#f0fdf4;font-family:Arial,sans-serif;'>");
        sb.append("<table width='100%' cellpadding='0' cellspacing='0'><tr><td align='center'>");
        sb.append("<table width='600' cellpadding='0' cellspacing='0' style='margin:20px auto;'>");

        // Header
        sb.append("<tr><td style='background:linear-gradient(135deg,#059669,#064e3b);padding:30px;text-align:center;'>");
        sb.append("<h1 style='color:#fff;margin:0;font-size:24px;'>EcoVerse</h1>");
        sb.append("<p style='color:rgba(255,255,255,0.8);margin:5px 0 0;'>Carbon Intelligence Platform</p>");
        sb.append("</td></tr>");

        // Body
        sb.append("<tr><td style='background:#fff;padding:30px;border-radius:0 0 8px 8px;'>");
        sb.append("<h2 style='color:#064e3b;margin:0 0 15px;'>").append(escapeHtml(title)).append("</h2>");
        sb.append("<p style='color:#374151;font-size:16px;line-height:1.6;'>").append(escapeHtml(message)).append("</p>");

        // CTA button
        if (ctaText != null && ctaUrl != null) {
            sb.append("<div style='text-align:center;margin:25px 0;'>");
            sb.append("<a href='").append(escapeHtml(ctaUrl)).append("' ");
            sb.append("style='background:#059669;color:#fff;padding:12px 30px;border-radius:8px;");
            sb.append("text-decoration:none;font-weight:600;display:inline-block;'>");
            sb.append(escapeHtml(ctaText)).append("</a></div>");
        }

        // Footer
        if (footer != null) {
            sb.append("<p style='color:#6b7280;font-size:14px;margin-top:20px;padding-top:15px;");
            sb.append("border-top:1px solid #e5e7eb;'>").append(escapeHtml(footer)).append("</p>");
        }

        sb.append("</td></tr>");

        // Bottom
        sb.append("<tr><td style='text-align:center;padding:20px;color:#9ca3af;font-size:12px;'>");
        sb.append("EcoVerse — Making sustainability simple<br>");
        sb.append("This is an automated email. Please do not reply.</td></tr>");

        sb.append("</table></td></tr></table></body></html>");
        return sb.toString();
    }

    /**
     * Escape HTML special characters to prevent XSS in email content.
     */
    private String escapeHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    /**
     * Mask email for logging (e.g., "j***@gmail.com").
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@");
        if (parts[0].length() <= 2) return "***@" + parts[1];
        return parts[0].charAt(0) + "***@" + parts[1];
    }

    /**
     * Get the application base URL for building email links.
     */
    @Value("${app.email.verification-base-url:http://localhost:8081}")
    private String appBaseUrl;

    private String getAppBaseUrl() {
        return appBaseUrl;
    }
}
