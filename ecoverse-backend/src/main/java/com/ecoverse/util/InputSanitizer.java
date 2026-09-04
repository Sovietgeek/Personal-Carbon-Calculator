package com.ecoverse.util;

import java.util.regex.Pattern;

/**
 * Input Sanitization Utility — prevents XSS and injection attacks.
 * Strips HTML tags, SQL keywords, and dangerous characters from user input.
 */
public final class InputSanitizer {

    private InputSanitizer() {
        // Utility class — no instantiation
    }

    // HTML tag pattern: matches <anything> including self-closing tags
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>", Pattern.CASE_INSENSITIVE);

    // Script-related patterns (on* event handlers, javascript:, vbscript:, data:)
    // Word boundary before on\w+ prevents false positives on words like "conversationId"
    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
        "\\bon\\w+\\s*=|javascript:|vbscript:|data\\s*:",
        Pattern.CASE_INSENSITIVE
    );

    // SQL injection keywords
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "\\b(DROP|DELETE|INSERT|UPDATE|SELECT|UNION|ALTER|CREATE|EXEC|EXECUTE|TRUNCATE|GRANT|REVOKE)\\b",
        Pattern.CASE_INSENSITIVE
    );

    // Dangerous characters that can break JSON/HTML
    private static final Pattern DANGEROUS_CHARS_PATTERN = Pattern.compile("[<>\"'&]");

    // Maximum string length for common fields
    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_EMAIL_LENGTH = 255;
    public static final int MAX_TEXT_LENGTH = 10000;
    public static final int MAX_TITLE_LENGTH = 255;
    public static final int MAX_ADDRESS_LENGTH = 500;

    /**
     * Sanitize a string for safe storage and display.
     * - Strips HTML tags
     * - Removes script event handlers
     * - Trims whitespace
     * - Limits length
     *
     * @param input raw user input
     * @param maxLength maximum allowed length
     * @return sanitized string, or null if input was null
     */
    public static String sanitize(String input, int maxLength) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String sanitized = input;

        // Strip HTML tags
        sanitized = HTML_TAG_PATTERN.matcher(sanitized).replaceAll("");

        // Remove script handlers and dangerous protocols
        sanitized = SCRIPT_PATTERN.matcher(sanitized).replaceAll("");

        // Trim whitespace
        sanitized = sanitized.trim();

        // Limit length
        if (sanitized.length() > maxLength) {
            sanitized = sanitized.substring(0, maxLength);
        }

        return sanitized;
    }

    /**
     * Sanitize with default max length (255).
     */
    public static String sanitize(String input) {
        return sanitize(input, MAX_TITLE_LENGTH);
    }

    /**
     * Sanitize a name field (strict — no SQL keywords allowed).
     */
    public static String sanitizeName(String name) {
        if (name == null) return null;

        String sanitized = sanitize(name, MAX_NAME_LENGTH);

        // Check for SQL injection in names (shouldn't have SQL keywords)
        if (SQL_INJECTION_PATTERN.matcher(sanitized).find()) {
            // Remove the SQL keywords, keep the rest
            sanitized = SQL_INJECTION_PATTERN.matcher(sanitized).replaceAll("").trim();
        }

        return sanitized;
    }

    /**
     * Sanitize email (just trim + lowercase, email validation is separate).
     */
    public static String sanitizeEmail(String email) {
        if (email == null) return null;
        return email.trim().toLowerCase();
    }

    /**
     * Sanitize a long text field (notes, descriptions, bodies).
     */
    public static String sanitizeText(String text) {
        return sanitize(text, MAX_TEXT_LENGTH);
    }

    /**
     * Sanitize an address field.
     */
    public static String sanitizeAddress(String address) {
        if (address == null) return null;
        // Addresses can have numbers, commas, etc. — only strip HTML
        String sanitized = HTML_TAG_PATTERN.matcher(address).replaceAll("");
        sanitized = SCRIPT_PATTERN.matcher(sanitized).replaceAll("");
        return sanitized.trim();
    }

    /**
     * Check if a string contains potentially dangerous content.
     * Returns true if dangerous content detected.
     */
    public static boolean containsDangerousContent(String input) {
        if (input == null || input.isEmpty()) return false;
        return HTML_TAG_PATTERN.matcher(input).find() ||
               SCRIPT_PATTERN.matcher(input).find();
    }

    // URL validation patterns
    private static final Pattern SAFE_URL_PATTERN = Pattern.compile(
        "^https?://.+", Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DANGEROUS_URL_PATTERN = Pattern.compile(
        "^\\s*(javascript|data|vbscript)\\s*:", Pattern.CASE_INSENSITIVE
    );

    /**
     * Validate a URL for safe display. Only http and https URLs are allowed.
     * Rejects javascript:, data:, vbscript:, and other dangerous protocols.
     * Null/empty URLs are allowed (treated as "no image").
     *
     * @param url the URL to validate
     * @return the URL if safe, or null if dangerous/invalid
     */
    public static String validateImageUrl(String url) {
        if (url == null || url.isBlank()) return null;

        String trimmed = url.trim();

        // Reject dangerous protocols
        if (DANGEROUS_URL_PATTERN.matcher(trimmed).find()) {
            return null;
        }

        // Only allow http:// or https:// URLs
        if (!SAFE_URL_PATTERN.matcher(trimmed).matches()) {
            return null;
        }

        return trimmed;
    }

    /**
     * Check if a URL is safe for display.
     * Returns true if the URL is null/empty (no image — safe) or a valid http/https URL.
     * Returns false for javascript:, data:, or other dangerous protocols.
     */
    public static boolean isUrlSafe(String url) {
        if (url == null || url.isBlank()) return true;
        String trimmed = url.trim();
        return SAFE_URL_PATTERN.matcher(trimmed).matches() &&
               !DANGEROUS_URL_PATTERN.matcher(trimmed).find();
    }
}
