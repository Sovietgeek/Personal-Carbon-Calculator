package com.ecoverse.controller;

import com.ecoverse.dto.ApiResponse;
import com.ecoverse.dto.auth.AuthResponse;
import com.ecoverse.dto.auth.ChangePasswordRequest;
import com.ecoverse.dto.auth.LoginRequest;
import com.ecoverse.dto.auth.RegisterRequest;
import com.ecoverse.model.User;
import com.ecoverse.security.JwtTokenProvider;
import com.ecoverse.security.OAuth2AuthorizationCodeService;
import com.ecoverse.service.AuditLogService;
import com.ecoverse.service.AuthService;
import com.ecoverse.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private OAuth2AuthorizationCodeService oauthCodeService;

    @Autowired
    private CookieUtil cookieUtil;

    @Value("${spring.security.oauth2.client.registration.google.client-id:dummy-id}")
    private String googleClientId;

    /**
     * Public OAuth availability status — lets the frontend hide/disable social
     * login buttons when no real OAuth credentials are configured (avoids
     * clicking "Google" and hitting a broken dummy-id flow / CSP error).
     */
    @GetMapping("/oauth-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> oauthStatus() {
        boolean googleEnabled = googleClientId != null
                && !googleClientId.isBlank()
                && !"dummy-id".equals(googleClientId);
        return ResponseEntity.ok(ApiResponse.success("OAuth status", Map.of(
                "googleEnabled", googleEnabled
        )));
    }

    /**
     * Register a new active local account with email + password.
     * Registration is intentionally separate from login and returns no tokens.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Account created successfully. Please sign in with your email and password.",
                        response));
    }

    /**
     * Login with email + password.
     * Returns access token in response body; sets refresh token as httpOnly cookie.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request);

        // Set refresh token as httpOnly cookie (NOT in response body)
        if (authResponse.getRefreshToken() != null) {
            cookieUtil.setRefreshTokenCookie(response, authResponse.getRefreshToken());
        }

        // Return access token only (refresh token is in the cookie)
        AuthResponse safeResponse = AuthResponse.builder()
                .accessToken(authResponse.getAccessToken())
                .refreshToken(null) // Never send refresh token in JSON body
                .expiresIn(authResponse.getExpiresIn())
                .user(authResponse.getUser())
                .build();

        return ResponseEntity.ok(ApiResponse.success("Login successful", safeResponse));
    }

    /**
     * Refresh access token using the httpOnly refresh token cookie.
     * On success, rotates the refresh token (old one is revoked, new one set as cookie).
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(HttpServletRequest request,
                                                                    HttpServletResponse response) {
        // Read refresh token from httpOnly cookie
        String refreshToken = cookieUtil.getRefreshTokenFromCookie(request);

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("No refresh token provided"));
        }

        Long userId;
        try {
            userId = jwtTokenProvider.validateRefreshToken(refreshToken);
        } catch (Exception e) {
            // Invalid/expired/revoked token — clear the cookie
            cookieUtil.clearRefreshTokenCookie(request, response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid or expired refresh token. Please log in again."));
        }

        User user = authService.getCurrentUser(userId);

        // SECURITY: Prevent token refresh for disabled or locked accounts.
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            cookieUtil.clearRefreshTokenCookie(request, response);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Account is disabled. Please contact support."));
        }
        if (!Boolean.TRUE.equals(user.getAccountNonLocked())) {
            cookieUtil.clearRefreshTokenCookie(request, response);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Account is locked. Please try again later."));
        }

        // Revoke the old refresh token (rotation)
        jwtTokenProvider.revokeRefreshToken(refreshToken);

        // Generate new token pair
        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // Set new refresh token cookie
        cookieUtil.setRefreshTokenCookie(response, newRefreshToken);

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(null) // Never send refresh token in JSON body
                .expiresIn(null)
                .user(AuthResponse.UserDTO.builder()
                        .id(user.getId()).name(user.getName()).email(user.getEmail())
                        .country(user.getCountry()).carbonBudget(user.getCarbonBudget())
                        .isPremium(user.getIsPremium())
                        .joinedDate(user.getJoinedDate() != null ? user.getJoinedDate().toLocalDate() : null)
                        .profileImage(user.getProfileImage()).provider(user.getProvider())
                        .role(user.getRole() != null ? user.getRole().name() : "USER")
                        .timezone(user.getTimezone())
                        .build())
                .build();

        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", authResponse));
    }

    /**
     * Exchange a one-time OAuth2 authorization code for tokens.
     * Sets refresh token as httpOnly cookie; returns access token in body.
     */
    @PostMapping("/oauth2/exchange")
    public ResponseEntity<ApiResponse<AuthResponse>> exchangeOAuthCode(@RequestBody Map<String, String> body,
                                                                       HttpServletRequest request,
                                                                       HttpServletResponse response) {
        String code = body.get("code");
        if (code == null || code.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Authorization code is required"));
        }

        OAuth2AuthorizationCodeService.PendingAuth pending = oauthCodeService.exchange(code);
        if (pending == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid or expired authorization code. Please try logging in again."));
        }

        // Get user info for the response
        User user = authService.getCurrentUser(pending.userId);

        // Set refresh token as httpOnly cookie
        if (pending.refreshToken != null) {
            cookieUtil.setRefreshTokenCookie(response, pending.refreshToken);
        }

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(pending.accessToken)
                .refreshToken(null) // Never send refresh token in JSON body
                .user(AuthResponse.UserDTO.builder()
                        .id(user.getId()).name(user.getName()).email(user.getEmail())
                        .country(user.getCountry()).carbonBudget(user.getCarbonBudget())
                        .isPremium(user.getIsPremium())
                        .joinedDate(user.getJoinedDate() != null ? user.getJoinedDate().toLocalDate() : null)
                        .profileImage(user.getProfileImage()).provider(user.getProvider())
                        .role(user.getRole() != null ? user.getRole().name() : "USER")
                        .timezone(user.getTimezone())
                        .build())
                .build();

        auditLogService.log(pending.userId, "OAUTH2_CODE_EXCHANGED", "/api/auth/oauth2/exchange");

        return ResponseEntity.ok(ApiResponse.success("OAuth2 authentication successful", authResponse));
    }

    /**
     * Logout — revoke the refresh token and clear the httpOnly cookie.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = cookieUtil.getRefreshTokenFromCookie(request);

        if (refreshToken != null) {
            jwtTokenProvider.revokeRefreshToken(refreshToken);
        }

        // Always clear the cookie, even if the token was already invalid
        cookieUtil.clearRefreshTokenCookie(request, response);

        Long userId = getCurrentUserIdOrNull();
        if (userId != null) {
            auditLogService.log(userId, "LOGOUT", "/api/auth/logout");
        }

        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    /**
     * Legacy email-verification endpoint retained for old links. Normal
     * password registration does not create verification tokens.
     */
    @GetMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam String token) {
        boolean active = authService.verifyEmail(token);
        String message = active
                ? "Account is already active. Email verification is not required."
                : "Account remains disabled. Email verification cannot activate it.";
        return ResponseEntity.ok(ApiResponse.success(message, null));
    }

    /**
     * Forgot password — send reset link to email.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Email is required"));
        }
        authService.forgotPassword(email);
        // Always return success to prevent email enumeration
        return ResponseEntity.ok(ApiResponse.success("If an account exists with this email, a reset link has been sent.", null));
    }

    /**
     * Reset password — set new password using reset token.
     * Revokes all refresh tokens and clears cookie.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@RequestBody Map<String, String> body,
                                                            HttpServletRequest request,
                                                            HttpServletResponse response) {
        String token = body.get("token");
        String newPassword = body.get("password");

        if (token == null || token.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Reset token is required"));
        }
        if (newPassword == null || newPassword.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("New password is required"));
        }

        authService.resetPassword(token, newPassword);

        // Clear refresh token cookie (all tokens were revoked server-side)
        cookieUtil.clearRefreshTokenCookie(request, response);

        return ResponseEntity.ok(ApiResponse.success("Password reset successfully. Please log in with your new password.", null));
    }

    /**
     * Legacy verification endpoint retained for old clients. Normal password
     * registration does not require email verification.
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Email is required"));
        }
        authService.resendVerification(email);
        return ResponseEntity.ok(ApiResponse.success(
                "Email verification is not required for password accounts.", null));
    }

    /**
     * Change password — requires current password + new password.
     * Revokes all refresh tokens and clears cookie.
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                             HttpServletRequest httpRequest,
                                                             HttpServletResponse httpResponse) {
        Long userId = getCurrentUserId();
        authService.changePassword(userId, request.getCurrentPassword(), request.getNewPassword());

        // Clear refresh token cookie (all tokens were revoked server-side)
        cookieUtil.clearRefreshTokenCookie(httpRequest, httpResponse);

        return ResponseEntity.ok(ApiResponse.success("Password changed successfully. Please log in with your new password.", null));
    }

    /**
     * Get current authenticated user's info.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthResponse.UserDTO>> getCurrentUser() {
        Long userId = getCurrentUserId();
        User user = authService.getCurrentUser(userId);
        AuthResponse.UserDTO userDTO = AuthResponse.UserDTO.builder()
                .id(user.getId()).name(user.getName()).email(user.getEmail())
                .country(user.getCountry()).carbonBudget(user.getCarbonBudget())
                .isPremium(user.getIsPremium())
                .joinedDate(user.getJoinedDate() != null ? user.getJoinedDate().toLocalDate() : null)
                .profileImage(user.getProfileImage()).provider(user.getProvider())
                .role(user.getRole() != null ? user.getRole().name() : "USER")
                .timezone(user.getTimezone())
                .build();
        return ResponseEntity.ok(ApiResponse.success(userDTO));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getPrincipal().toString());
    }

    /**
     * Get current user ID, or null if not authenticated (for logout endpoint).
     */
    private Long getCurrentUserIdOrNull() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() != null && !"anonymousUser".equals(auth.getPrincipal())) {
                return Long.parseLong(auth.getPrincipal().toString());
            }
        } catch (Exception e) {
            // Not authenticated — that's fine for logout
        }
        return null;
    }
}
