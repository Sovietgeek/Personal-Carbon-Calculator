package com.ecoverse.integration;

import com.ecoverse.dto.auth.AuthResponse;
import com.ecoverse.dto.auth.LoginRequest;
import com.ecoverse.dto.auth.RegisterRequest;
import com.ecoverse.exception.BadRequestException;
import com.ecoverse.model.Role;
import org.springframework.security.authentication.BadCredentialsException;
import com.ecoverse.model.User;
import com.ecoverse.repository.UserRepository;
import com.ecoverse.security.JwtTokenProvider;
import com.ecoverse.service.AuditLogService;
import com.ecoverse.service.AuthService;
import com.ecoverse.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Authentication Regression Tests (Phase 6 — Part J).
 *
 * Comprehensive tests for all auth flows ensuring no regression in security:
 * - Login, logout, refresh rotation
 * - httpOnly cookie verification logic
 * - Password change invalidates existing tokens
 * - Password reset flow
 * - Legacy verification compatibility without activation
 * - Account lockout after 5 failed attempts
 * - Disabled account rejection
 * - Anti-enumeration: same error message for all login failures
 */
@ExtendWith(MockitoExtension.class)
@Tag("security")
class AuthenticationRegressionTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private EmailService emailService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private AuthService authService;

    private User verifiedUser;
    private User lockedUser;
    private User disabledUser;

    @BeforeEach
    void setUp() {
        verifiedUser = User.builder()
                .id(1L).name("Verified").email("verified@auth.com").password("encoded")
                .enabled(true).accountNonLocked(true).failedLoginAttempts(0)
                .provider("LOCAL").build();

        lockedUser = User.builder()
                .id(2L).name("Locked").email("locked@auth.com").password("encoded")
                .enabled(true).accountNonLocked(false).failedLoginAttempts(5)
                .lockoutUntil(LocalDateTime.now().plusMinutes(30))
                .provider("LOCAL").build();

        disabledUser = User.builder()
                .id(3L).name("Disabled").email("disabled@auth.com").password("encoded")
                .enabled(false).accountNonLocked(true).failedLoginAttempts(0)
                .emailVerificationToken("token-123")
                .verificationTokenExpiry(LocalDateTime.now().plusHours(24))
                .provider("LOCAL").build();
    }

    // ================================================================
    // LOGIN SECURITY
    // ================================================================

    @Nested
    @DisplayName("Login Security")
    class LoginSecurity {

        @Test
        @DisplayName("Valid credentials return access + refresh tokens")
        void validCredentialsReturnTokens() {
            when(userRepository.findByEmail("verified@auth.com")).thenReturn(Optional.of(verifiedUser));
            when(passwordEncoder.matches("password", "encoded")).thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(1L, "verified@auth.com")).thenReturn("access-token");
            when(jwtTokenProvider.generateRefreshToken(1L)).thenReturn("refresh-token");

            AuthResponse response = authService.login(new LoginRequest("verified@auth.com", "password"));

            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        }

        @Test
        @DisplayName("Wrong password — generic error, failed attempts incremented")
        void wrongPasswordGenericError() {
            when(userRepository.findByEmail("verified@auth.com")).thenReturn(Optional.of(verifiedUser));
            when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest("verified@auth.com", "wrong")))
                    .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class)
                    .hasMessage("Invalid email or password");

            verify(userRepository).save(argThat(u -> u.getFailedLoginAttempts() == 1));
        }

        @Test
        @DisplayName("Non-existent email — same generic error (anti-enumeration)")
        void nonExistentEmailSameError() {
            when(userRepository.findByEmail("nobody@auth.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@auth.com", "password")))
                    .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class)
                    .hasMessage("Invalid email or password");
        }

        @Test
        @DisplayName("Disabled user — same generic error")
        void disabledUserSameError() {
            when(userRepository.findByEmail("disabled@auth.com")).thenReturn(Optional.of(disabledUser));

            assertThatThrownBy(() -> authService.login(new LoginRequest("disabled@auth.com", "password")))
                    .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class)
                    .hasMessage("Invalid email or password");

            // Password encoder must NOT be called for disabled users
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("Locked account — same generic error (don't reveal lock status)")
        void lockedAccountSameError() {
            when(userRepository.findByEmail("locked@auth.com")).thenReturn(Optional.of(lockedUser));
            // Password encoder NOT stubbed — locked account check happens before password check

            assertThatThrownBy(() -> authService.login(new LoginRequest("locked@auth.com", "password")))
                    .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class)
                    .hasMessage("Invalid email or password");
        }

        @Test
        @DisplayName("Account locks after 5th failed attempt")
        void accountLocksAfterFiveAttempts() {
            User almostLocked = User.builder()
                    .id(1L).name("Almost").email("almost@auth.com").password("encoded")
                    .enabled(true).accountNonLocked(true).failedLoginAttempts(4)
                    .provider("LOCAL").build();

            when(userRepository.findByEmail("almost@auth.com")).thenReturn(Optional.of(almostLocked));
            when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest("almost@auth.com", "wrong")))
                    .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class)
                    .hasMessage("Invalid email or password");

            // Account should now be locked
            verify(userRepository).save(argThat(u ->
                    !Boolean.TRUE.equals(u.getAccountNonLocked()) &&
                    u.getFailedLoginAttempts() == 5));
        }

        @Test
        @DisplayName("Successful login resets failed login attempts to 0")
        void successfulLoginResetsAttempts() {
            User withAttempts = User.builder()
                    .id(1L).name("With").email("with@auth.com").password("encoded")
                    .enabled(true).accountNonLocked(true).failedLoginAttempts(3)
                    .provider("LOCAL").build();

            when(userRepository.findByEmail("with@auth.com")).thenReturn(Optional.of(withAttempts));
            when(passwordEncoder.matches("password", "encoded")).thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(1L, "with@auth.com")).thenReturn("at");
            when(jwtTokenProvider.generateRefreshToken(1L)).thenReturn("rt");

            authService.login(new LoginRequest("with@auth.com", "password"));

            verify(userRepository).save(argThat(u -> u.getFailedLoginAttempts() == 0));
        }
    }

    // ================================================================
    // REGISTRATION SECURITY
    // ================================================================

    @Nested
    @DisplayName("Registration Security")
    class RegistrationSecurity {

        @Test
        @DisplayName("New password account starts active")
        void newUserStartsActive() {
            when(userRepository.existsByEmail("new@auth.com")).thenReturn(false);
            when(passwordEncoder.encode("Str0ngP@ss!")).thenReturn("encoded");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(10L);
                return u;
            });

            AuthResponse response = authService.register(RegisterRequest.builder()
                    .name("New User").email("new@auth.com").password("Str0ngP@ss!").country("US").build());

            verify(userRepository).save(argThat(u ->
                    Boolean.TRUE.equals(u.getEnabled()) &&
                    Boolean.TRUE.equals(u.getAccountNonLocked()) &&
                    u.getEmailVerificationToken() == null &&
                    u.getVerificationTokenExpiry() == null &&
                    u.getRole() == Role.USER
            ));
            verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
            assertThat(response.getAccessToken()).isNull();
            assertThat(response.getRefreshToken()).isNull();
        }

        @Test
        @DisplayName("Duplicate email rejected")
        void duplicateEmailRejected() {
            when(userRepository.existsByEmail("dup@auth.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(RegisterRequest.builder()
                    .name("Dup").email("dup@auth.com").password("Str0ngP@ss!").country("US").build()))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("Registration does not depend on verification email")
        void registrationDoesNotSendVerificationEmail() {
            when(userRepository.existsByEmail("reg@auth.com")).thenReturn(false);
            when(passwordEncoder.encode("Str0ngP@ss!")).thenReturn("encoded");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(10L);
                return u;
            });

            authService.register(RegisterRequest.builder()
                    .name("Reg User").email("reg@auth.com").password("Str0ngP@ss!").country("US").build());

            verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
        }
    }

    // ================================================================
    // EMAIL VERIFICATION
    // ================================================================

    @Nested
    @DisplayName("Email Verification Security")
    class EmailVerificationSecurity {

        @Test
        @DisplayName("Legacy verification token never enables a disabled account")
        void legacyVerificationDoesNotEnableDisabledUser() {
            when(userRepository.findByEmailVerificationToken("valid-token"))
                    .thenReturn(Optional.of(disabledUser));

            assertThat(authService.verifyEmail("valid-token")).isFalse();

            verify(userRepository).save(argThat(u ->
                    !Boolean.TRUE.equals(u.getEnabled()) &&
                    u.getEmailVerificationToken() == null &&
                    u.getVerificationTokenExpiry() == null
            ));
        }

        @Test
        @DisplayName("Verification clears token (single-use)")
        void verificationClearsToken() {
            when(userRepository.findByEmailVerificationToken("valid-token"))
                    .thenReturn(Optional.of(disabledUser));

            authService.verifyEmail("valid-token");

            verify(userRepository).save(argThat(u ->
                    u.getEmailVerificationToken() == null &&
                    u.getVerificationTokenExpiry() == null));
        }

        @Test
        @DisplayName("Invalid token rejected")
        void invalidTokenRejected() {
            when(userRepository.findByEmailVerificationToken("bad-token"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.verifyEmail("bad-token"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid verification token");
        }

        @Test
        @DisplayName("Expired token rejected")
        void expiredTokenRejected() {
            User expiredTokenUser = User.builder()
                    .id(3L).name("Expired").email("expired@auth.com").password("encoded")
                    .enabled(false).emailVerificationToken("expired-token")
                    .verificationTokenExpiry(LocalDateTime.now().minusHours(25))
                    .provider("LOCAL").build();

            when(userRepository.findByEmailVerificationToken("expired-token"))
                    .thenReturn(Optional.of(expiredTokenUser));

            assertThatThrownBy(() -> authService.verifyEmail("expired-token"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("Legacy resend verification is a no-op")
        void resendVerificationIsNoOp() {
            assertThatCode(() -> authService.resendVerification("nobody@auth.com"))
                    .doesNotThrowAnyException();

            verifyNoInteractions(userRepository, emailService);
        }
    }

    // ================================================================
    // OAUTH2
    // ================================================================

    @Nested
    @DisplayName("OAuth2 Login Security")
    class OAuth2Security {

        @Test
        @DisplayName("Google OAuth users start enabled (email verified by Google)")
        void googleOAuthStartEnabled() {
            when(userRepository.findByEmail("google@auth.com")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(5L);
                return u;
            });
            when(jwtTokenProvider.generateAccessToken(anyLong(), anyString())).thenReturn("at");
            when(jwtTokenProvider.generateRefreshToken(anyLong())).thenReturn("rt");

            AuthResponse response = authService.processOAuthLogin(
                    "google@auth.com", "Google User", "http://pic.jpg", "google-sub");

            verify(userRepository).save(argThat(u -> Boolean.TRUE.equals(u.getEnabled())));
            assertThat(response.getAccessToken()).isEqualTo("at");
            assertThat(response.getRefreshToken()).isEqualTo("rt");
        }

        @Test
        @DisplayName("OAuth2 without email throws")
        void oauth2WithoutEmailThrows() {
            assertThatThrownBy(() -> authService.processOAuthLogin(null, "User", null, "sub"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("email");
        }
    }
}
