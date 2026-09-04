package com.ecoverse.service;

import com.ecoverse.dto.auth.AuthResponse;
import com.ecoverse.dto.auth.LoginRequest;
import com.ecoverse.dto.auth.RegisterRequest;
import com.ecoverse.exception.BadRequestException;
import com.ecoverse.exception.ResourceNotFoundException;
import com.ecoverse.model.User;
import com.ecoverse.repository.UserRepository;
import com.ecoverse.security.JwtTokenProvider;
import com.ecoverse.util.InputSanitizer;
import com.ecoverse.util.PasswordValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private EmailService emailService;

    @Value("${jwt.access-expiration}")
    private long accessExpiration;

    // ======================================================================
    // REGISTER — Email + Password
    // ======================================================================
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String name = InputSanitizer.sanitizeName(req.getName());
        String email = InputSanitizer.sanitizeEmail(req.getEmail());
        String country = InputSanitizer.sanitize(req.getCountry(), 100);

        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email is already registered: " + email);
        }

        PasswordValidator.ValidationResult validation = PasswordValidator.validate(req.getPassword());
        if (!validation.isValid()) {
            throw new BadRequestException(validation.getMessage());
        }

        // A valid password registration creates an immediately usable active
        // account. Email verification remains available only as legacy/future
        // functionality and is not an authentication prerequisite.
        User user = User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(req.getPassword()))
                .country(country)
                .carbonBudget(new BigDecimal("4.20"))
                .isPremium(false)
                .joinedDate(LocalDateTime.now())
                .goalsSteps(10000)
                .goalsSleep(8)
                .goalsWater(3)
                .goalsCalories(2000)
                .bestStreak(0)
                .failedLoginAttempts(0)
                .accountNonLocked(true)
                .enabled(true)
                .provider("LOCAL")
                .build();

        user = userRepository.save(user);
        auditLogService.log(user.getId(), "REGISTER", "/api/auth/register", null);

        // Registration deliberately does not auto-login or return tokens.
        // The user explicitly signs in next and receives the normal secure
        // access-token + httpOnly refresh-cookie pair from /login.
        return AuthResponse.builder()
                .accessToken(null)
                .refreshToken(null)
                .expiresIn(null)
                .user(buildUserDTO(user))
                .build();
    }

    // ======================================================================
    // LOGIN — Email + Password (with account lockout + anti-enumeration)
    // All login failure messages are identical to prevent username enumeration.
    // Failed-attempt and lockout updates must commit even when authentication fails.
    // ======================================================================
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public AuthResponse login(LoginRequest req) {
        String email = InputSanitizer.sanitizeEmail(req.getEmail());

        // Same message for all failures to prevent email enumeration.
        final String GENERIC_MESSAGE = "Invalid email or password";

        User user = userRepository.findByEmail(email).orElse(null);

        // If user not found, throw generic authentication failure.
        if (user == null) {
            // Log at DEBUG level — don't leak info about non-existent accounts in production logs.
            logger.debug("Login attempt for non-existent email: {}", email);
            throw new BadCredentialsException(GENERIC_MESSAGE);
        }

        // Check if account is locked.
        if (!Boolean.TRUE.equals(user.getAccountNonLocked())) {
            if (user.getLockoutUntil() != null && user.getLockoutUntil().isAfter(LocalDateTime.now())) {
                auditLogService.log(user.getId(), "LOGIN_BLOCKED", "/api/auth/login", "Account locked");
                // Generic message — don't reveal that the account exists and is locked.
                throw new BadCredentialsException(GENERIC_MESSAGE);
            } else {
                // Lock period expired — unlock.
                user.setAccountNonLocked(true);
                user.setFailedLoginAttempts(0);
                user.setLockoutUntil(null);
                userRepository.save(user);
            }
        }

        // enabled is the active-account flag. An administrator-disabled account
        // must remain unable to authenticate, independently of email delivery.
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            auditLogService.log(user.getId(), "LOGIN_DISABLED", "/api/auth/login", "Account disabled");
            throw new BadCredentialsException(GENERIC_MESSAGE);
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);

            if (attempts >= 5) {
                user.setAccountNonLocked(false);
                user.setLockoutUntil(LocalDateTime.now().plusMinutes(30));
                userRepository.save(user);
                auditLogService.log(user.getId(), "ACCOUNT_LOCKED", "/api/auth/login", "5 failed attempts");
                // Generic message — don't reveal lockout status.
                throw new BadCredentialsException(GENERIC_MESSAGE);
            }

            userRepository.save(user);
            auditLogService.log(user.getId(), "LOGIN_FAILED", "/api/auth/login", "Attempt " + attempts);
            throw new BadCredentialsException(GENERIC_MESSAGE);
        }

        // Reset failed attempts on success
        user.setFailedLoginAttempts(0);
        user.setAccountNonLocked(true);
        user.setLockoutUntil(null);
        userRepository.save(user);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        auditLogService.log(user.getId(), "LOGIN", "/api/auth/login", null);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(accessExpiration / 1000)
                .user(buildUserDTO(user))
                .build();
    }

    // ======================================================================
    // GOOGLE OAUTH2 LOGIN — Find or Create user
    // ======================================================================
    @Transactional
    public AuthResponse processOAuthLogin(String email, String name, String picture, String googleSub) {
        if (email == null || email.isEmpty()) {
            throw new BadRequestException("Google account did not provide an email address");
        }

        email = InputSanitizer.sanitizeEmail(email);
        name = InputSanitizer.sanitizeName(name);

        // Check if user exists with this email
        User user = userRepository.findByEmail(email).orElse(null);

        if (user != null) {
            // OAuth must not bypass an administrative disable or lockout.
            if (!Boolean.TRUE.equals(user.getEnabled()) ||
                    !Boolean.TRUE.equals(user.getAccountNonLocked())) {
                logger.warn("OAuth login blocked for userId={} because the account is disabled or locked",
                        user.getId());
                throw new BadCredentialsException("Authentication failed");
            }

            // User exists — update Google info if needed
            if (!"GOOGLE".equals(user.getProvider())) {
                // User was registered with email/password, link Google account
                user.setProvider("GOOGLE");
                user.setProviderId(googleSub);
            }
            if (picture != null && (user.getProfileImage() == null || user.getProfileImage().isEmpty())) {
                user.setProfileImage(picture);
            }
            user = userRepository.save(user);

            auditLogService.log(user.getId(), "GOOGLE_LOGIN", "/api/auth/oauth2", null);
        } else {
            // New user — create account via Google
            user = User.builder()
                    .name(name)
                    .email(email)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString())) // Random password (not used)
                    .carbonBudget(new BigDecimal("4.20"))
                    .isPremium(false)
                    .joinedDate(LocalDateTime.now())
                    .goalsSteps(10000)
                    .goalsSleep(8)
                    .goalsWater(3)
                    .goalsCalories(2000)
                    .bestStreak(0)
                    .failedLoginAttempts(0)
                    .accountNonLocked(true)
                    .enabled(true) // Google-verified email
                    .provider("GOOGLE")
                    .providerId(googleSub)
                    .profileImage(picture)
                    .build();

            user = userRepository.save(user);

            auditLogService.log(user.getId(), "GOOGLE_REGISTER", "/api/auth/oauth2", null);
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(accessExpiration / 1000)
                .user(buildUserDTO(user))
                .build();
    }

    // ======================================================================
    // VERIFY EMAIL — Legacy compatibility endpoint
    // ======================================================================
    /**
     * Verification tokens belong to the legacy registration flow. New local
     * accounts do not receive them. Most importantly, this endpoint must never
     * re-enable an account that an administrator has disabled.
     */
    @Transactional
    public boolean verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid verification token"));

        if (user.getVerificationTokenExpiry() != null && user.getVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Verification token has expired. Please request a new one.");
        }

        // Verification is legacy-only. Consume the token without changing the
        // active-account flag, so an email link can never override an admin
        // disablement. New password accounts never have such a token.
        user.setEmailVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);

        if (Boolean.TRUE.equals(user.getEnabled())) {
            auditLogService.log(user.getId(), "LEGACY_EMAIL_VERIFICATION_ATTEMPT",
                    "/api/auth/verify", "Account already active");
            return true;
        }

        auditLogService.log(user.getId(), "LEGACY_EMAIL_VERIFICATION_ATTEMPT",
                "/api/auth/verify", "Account remains disabled");
        return false;
    }

    // ======================================================================
    // FORGOT PASSWORD — Send reset link
    // ======================================================================
    @Transactional
    public void forgotPassword(String rawEmail) {
        String email = InputSanitizer.sanitizeEmail(rawEmail);

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            // Don't reveal that email doesn't exist (security)
            logger.debug("Password reset requested for non-existent email: {}", email);
            return;
        }

        if (!"LOCAL".equals(user.getProvider())) {
            throw new BadRequestException("This account uses Google login. Please sign in with Google.");
        }

        // Generate reset token
        String resetToken = UUID.randomUUID().toString();
        LocalDateTime resetExpiry = LocalDateTime.now().plusHours(1);

        user.setPasswordResetToken(resetToken);
        user.setResetTokenExpiry(resetExpiry);
        userRepository.save(user);

        // Send reset email
        try {
            sendPasswordResetEmail(email, resetToken);
        } catch (Exception e) {
            logger.error("Failed to send password reset email to {}: {}", email, e.getMessage());
        }

        auditLogService.log(user.getId(), "PASSWORD_RESET_REQUESTED", "/api/auth/forgot-password", null);
    }

    // ======================================================================
    // RESET PASSWORD — Verify token and set new password
    // ======================================================================
    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByPasswordResetToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid reset token"));

        if (user.getResetTokenExpiry() != null && user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Reset token has expired. Please request a new one.");
        }

        PasswordValidator.ValidationResult validation = PasswordValidator.validate(newPassword);
        if (!validation.isValid()) {
            throw new BadRequestException(validation.getMessage());
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setResetTokenExpiry(null);
        user.setFailedLoginAttempts(0);
        user.setAccountNonLocked(true);
        user.setLockoutUntil(null);
        userRepository.save(user);

        // Revoke all refresh tokens (force re-login on all devices)
        jwtTokenProvider.revokeAllUserTokens(user.getId());

        auditLogService.log(user.getId(), "PASSWORD_RESET", "/api/auth/reset-password", null);
    }

    // ======================================================================
    // CHANGE PASSWORD — Authenticated user changes their own password
    // ======================================================================
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // Verify current password
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }

        // Validate new password strength
        PasswordValidator.ValidationResult validation = PasswordValidator.validate(newPassword);
        if (!validation.isValid()) {
            throw new BadRequestException(validation.getMessage());
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Revoke ALL refresh tokens (force re-login on all devices)
        jwtTokenProvider.revokeAllUserTokens(user.getId());

        auditLogService.log(user.getId(), "PASSWORD_CHANGE", "/api/auth/change-password", null);
    }

    // ======================================================================
    // CHECK PASSWORD — Verify current password without changing it (for account deletion)
    // ======================================================================
    public boolean checkPassword(Long userId, String rawPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }

    // ======================================================================
    // GET CURRENT USER
    // ======================================================================
    public User getCurrentUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    // ======================================================================
    // LEGACY RESEND VERIFICATION EMAIL
    // ======================================================================
    /**
     * Retained for compatibility with old clients. New registrations do not
     * require email verification, so no new verification token is generated.
     */
    @Transactional
    public void resendVerification(String rawEmail) {
        String email = InputSanitizer.sanitizeEmail(rawEmail);
        logger.debug("Legacy verification resend requested for email={}", email);
    }

    // ======================================================================
    // PRIVATE HELPERS
    // ======================================================================

    private void sendPasswordResetEmail(String toEmail, String token) {
        emailService.sendPasswordResetEmail(toEmail, token);
    }

    private AuthResponse.UserDTO buildUserDTO(User user) {
        return AuthResponse.UserDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .country(user.getCountry())
                .city(user.getCity())
                .state(user.getState())
                .carbonBudget(user.getCarbonBudget())
                .isPremium(user.getIsPremium())
                .joinedDate(user.getJoinedDate() != null ? user.getJoinedDate().toLocalDate() : null)
                .profileImage(user.getProfileImage())
                .provider(user.getProvider())
                .role(user.getRole() != null ? user.getRole().name() : "USER")
                .build();
    }
}
