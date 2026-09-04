package com.ecoverse.util;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Password strength validator.
 * Enforces minimum security standards for user passwords.
 */
public final class PasswordValidator {

    private PasswordValidator() {}

    // Minimum 8 chars, at least 1 uppercase, 1 lowercase, 1 digit, 1 special char
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).{8,}$"
    );

    // Common weak passwords that should never be allowed
    private static final Set<String> BLACKLISTED_PASSWORDS = Set.of(
        "password", "password1", "password123", "12345678", "123456789",
        "1234567890", "qwerty123", "qwertyuiop", "abcdefgh", "iloveyou",
        "sunshine", "princess", "football", "baseball", "dragon123",
        "letmein", "welcome1", "monkey123", "shadow12", "trustno1",
        "passw0rd", "P@ssw0rd", "admin123", "root1234", "test1234"
    );

    public static ValidationResult validate(String password) {
        if (password == null || password.isEmpty()) {
            return ValidationResult.invalid("Password is required");
        }

        if (password.length() < 8) {
            return ValidationResult.invalid("Password must be at least 8 characters long");
        }

        if (password.length() > 128) {
            return ValidationResult.invalid("Password must be less than 128 characters");
        }

        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            StringBuilder requirements = new StringBuilder("Password must contain: ");
            boolean hasReq = false;

            if (!password.chars().anyMatch(Character::isUpperCase)) {
                requirements.append("1 uppercase letter");
                hasReq = true;
            }
            if (!password.chars().anyMatch(Character::isLowerCase)) {
                if (hasReq) requirements.append(", ");
                requirements.append("1 lowercase letter");
                hasReq = true;
            }
            if (!password.chars().anyMatch(Character::isDigit)) {
                if (hasReq) requirements.append(", ");
                requirements.append("1 number");
                hasReq = true;
            }
            if (!password.chars().anyMatch(c -> "!@#$%^&*()_+-=[]{};':\"\\|,.<>/?".indexOf(c) >= 0)) {
                if (hasReq) requirements.append(", ");
                requirements.append("1 special character (!@#$%^&*)");
                hasReq = true;
            }

            return ValidationResult.invalid(requirements.toString());
        }

        // Check blacklisted passwords
        if (BLACKLISTED_PASSWORDS.contains(password.toLowerCase())) {
            return ValidationResult.invalid("This password is too common. Please choose a stronger one.");
        }

        return ValidationResult.valid();
    }

    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, "Password is valid");
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
    }
}
