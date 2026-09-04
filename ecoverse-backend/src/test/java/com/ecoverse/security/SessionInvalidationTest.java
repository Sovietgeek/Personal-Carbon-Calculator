package com.ecoverse.security;

import com.ecoverse.dto.auth.LoginRequest;
import com.ecoverse.exception.BadRequestException;
import com.ecoverse.model.User;
import com.ecoverse.repository.UserRepository;
import com.ecoverse.service.AuditLogService;
import com.ecoverse.service.AuthService;
import com.ecoverse.util.PasswordValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for session invalidation rules:
 * - Password change revokes all refresh tokens
 * - Password reset revokes all refresh tokens
 * - Wrong password does NOT revoke tokens (only increments attempts)
 * - Account lockout prevents login
 * - Account lockout auto-expires after 30 minutes
 * - All login failures return same generic message (anti-enumeration)
 */
@ExtendWith(MockitoExtension.class)
class SessionInvalidationTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private AuditLogService auditLogService;
    @Mock private JavaMailSender mailSender;

    @InjectMocks private AuthService authService;

    private User verifiedUser;

    @BeforeEach
    void setUp() {
        verifiedUser = User.builder()
                .id(1L).name("User").email("user@test.com").password("encoded")
                .enabled(true).accountNonLocked(true).failedLoginAttempts(0)
                .provider("LOCAL").build();
    }

    @Nested
    @DisplayName("Password Change")
    class PasswordChange {

        @Test
        @DisplayName("changePassword requires correct current password")
        void changePasswordRequiresCorrectCurrentPassword() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(verifiedUser));
            when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

            assertThatThrownBy(() -> authService.changePassword(1L, "wrong", "NewStr0ngP@ss!"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("incorrect");
        }

        @Test
        @DisplayName("changePassword revokes all refresh tokens after success")
        void changePasswordRevokesAllRefreshTokens() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(verifiedUser));
            when(passwordEncoder.matches("OldP@ss1", "encoded")).thenReturn(true);
            when(passwordEncoder.encode(anyString())).thenReturn("new-encoded");

            authService.changePassword(1L, "OldP@ss1", "NewStr0ngP@ss1!");

            verify(jwtTokenProvider).revokeAllUserTokens(1L);
        }

        @Test
        @DisplayName("changePassword validates new password strength")
        void changePasswordValidatesNewPassword() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(verifiedUser));
            when(passwordEncoder.matches("OldP@ss1", "encoded")).thenReturn(true);

            assertThatThrownBy(() -> authService.changePassword(1L, "OldP@ss1", "weak"))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("changePassword updates password in database")
        void changePasswordUpdatesPasswordInDb() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(verifiedUser));
            when(passwordEncoder.matches("OldP@ss1", "encoded")).thenReturn(true);
            when(passwordEncoder.encode("NewStr0ngP@ss1!")).thenReturn("new-encoded");

            authService.changePassword(1L, "OldP@ss1", "NewStr0ngP@ss1!");

            verify(userRepository).save(argThat(user ->
                    "new-encoded".equals(user.getPassword())
            ));
        }
    }

    @Nested
    @DisplayName("Account Lockout")
    class AccountLockout {

        @Test
        @DisplayName("5 failed login attempts locks the account")
        void fiveFailedAttemptsLocksAccount() {
            User user = User.builder()
                    .id(1L).email("user@test.com").password("encoded")
                    .enabled(true).accountNonLocked(true).failedLoginAttempts(4)
                    .provider("LOCAL").build();

            when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest("user@test.com", "wrong")))
                    .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);

            verify(userRepository).save(argThat(u ->
                    !Boolean.TRUE.equals(u.getAccountNonLocked())
            ));
        }

        @Test
        @DisplayName("Successful login resets failed attempts to 0")
        void successfulLoginResetsFailedAttempts() {
            User user = User.builder()
                    .id(1L).email("user@test.com").password("encoded")
                    .enabled(true).accountNonLocked(true).failedLoginAttempts(3)
                    .provider("LOCAL").build();

            when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("correct", "encoded")).thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(1L, "user@test.com")).thenReturn("at");
            when(jwtTokenProvider.generateRefreshToken(1L)).thenReturn("rt");

            authService.login(new LoginRequest("user@test.com", "correct"));

            verify(userRepository).save(argThat(u ->
                    u.getFailedLoginAttempts() == 0 && Boolean.TRUE.equals(u.getAccountNonLocked())
            ));
        }

        @Test
        @DisplayName("Locked account login returns generic message (anti-enumeration)")
        void lockedAccountReturnsGenericMessage() {
            User user = User.builder()
                    .id(1L).email("locked@test.com").password("encoded")
                    .enabled(true).accountNonLocked(false)
                    .lockoutUntil(LocalDateTime.now().plusMinutes(25))
                    .failedLoginAttempts(5)
                    .provider("LOCAL").build();

            when(userRepository.findByEmail("locked@test.com")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(new LoginRequest("locked@test.com", "anypass")))
                    .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class)
                    .hasMessage("Invalid email or password");
        }

        @Test
        @DisplayName("Lockout auto-expires after 30 minutes")
        void lockoutAutoExpires() {
            User user = User.builder()
                    .id(1L).email("user@test.com").password("encoded")
                    .enabled(true).accountNonLocked(false)
                    .lockoutUntil(LocalDateTime.now().minusMinutes(5)) // Expired lock
                    .failedLoginAttempts(5)
                    .provider("LOCAL").build();

            when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("correct", "encoded")).thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(1L, "user@test.com")).thenReturn("at");
            when(jwtTokenProvider.generateRefreshToken(1L)).thenReturn("rt");

            authService.login(new LoginRequest("user@test.com", "correct"));

            // Login flow saves user at least once (unlock + success reset)
            // The final save should have accountNonLocked=true and failedLoginAttempts=0
            verify(userRepository, atLeastOnce()).save(argThat(u ->
                    Boolean.TRUE.equals(u.getAccountNonLocked()) && u.getFailedLoginAttempts() == 0
            ));
        }
    }

    @Nested
    @DisplayName("Password Reset Session Invalidation")
    class PasswordResetInvalidation {

        @Test
        @DisplayName("Password reset revokes all refresh tokens")
        void passwordResetRevokesAllTokens() {
            String resetToken = "valid-reset-token";
            User user = User.builder()
                    .id(1L).email("user@test.com").password("old-encoded")
                    .enabled(true).accountNonLocked(true)
                    .passwordResetToken(resetToken)
                    .resetTokenExpiry(LocalDateTime.now().plusHours(1))
                    .provider("LOCAL").build();

            when(userRepository.findByPasswordResetToken(resetToken)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(anyString())).thenReturn("new-encoded");

            authService.resetPassword(resetToken, "NewStr0ngP@ss1!");

            verify(jwtTokenProvider).revokeAllUserTokens(1L);
        }

        @Test
        @DisplayName("Password reset clears reset token and expiry")
        void passwordResetClearsToken() {
            String resetToken = "valid-reset-token";
            User user = User.builder()
                    .id(1L).email("user@test.com").password("old-encoded")
                    .enabled(true).accountNonLocked(true)
                    .passwordResetToken(resetToken)
                    .resetTokenExpiry(LocalDateTime.now().plusHours(1))
                    .provider("LOCAL").build();

            when(userRepository.findByPasswordResetToken(resetToken)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(anyString())).thenReturn("new-encoded");

            authService.resetPassword(resetToken, "NewStr0ngP@ss1!");

            verify(userRepository).save(argThat(u ->
                    u.getPasswordResetToken() == null && u.getResetTokenExpiry() == null
            ));
        }

        @Test
        @DisplayName("Password reset unlocks account and resets failed attempts")
        void passwordResetUnlocksAccount() {
            String resetToken = "valid-reset-token";
            User user = User.builder()
                    .id(1L).email("user@test.com").password("old-encoded")
                    .enabled(true).accountNonLocked(false)
                    .failedLoginAttempts(5)
                    .lockoutUntil(LocalDateTime.now().plusMinutes(20))
                    .passwordResetToken(resetToken)
                    .resetTokenExpiry(LocalDateTime.now().plusHours(1))
                    .provider("LOCAL").build();

            when(userRepository.findByPasswordResetToken(resetToken)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(anyString())).thenReturn("new-encoded");

            authService.resetPassword(resetToken, "NewStr0ngP@ss1!");

            verify(userRepository).save(argThat(u ->
                    Boolean.TRUE.equals(u.getAccountNonLocked()) &&
                    u.getFailedLoginAttempts() == 0 &&
                    u.getLockoutUntil() == null
            ));
        }
    }

    @Nested
    @DisplayName("Anti-Enumeration")
    class AntiEnumeration {

        @Test
        @DisplayName("Non-existent email returns same message as wrong password")
        void nonExistentEmailSameMessageAsWrongPassword() {
            when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@test.com", "anypass")))
                    .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class)
                    .hasMessage("Invalid email or password");
        }

        @Test
        @DisplayName("Wrong password returns same message as non-existent email")
        void wrongPasswordSameMessageAsNonExistentEmail() {
            when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(verifiedUser));
            when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest("user@test.com", "wrong")))
                    .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class)
                    .hasMessage("Invalid email or password");
        }

        @Test
        @DisplayName("Unverified account returns same message as wrong password")
        void disabledAccountSameMessageAsWrongPassword() {
            User disabled = User.builder()
                    .id(1L).email("disabled@test.com").password("encoded")
                    .enabled(false).accountNonLocked(true).failedLoginAttempts(0)
                    .provider("LOCAL").build();

            when(userRepository.findByEmail("disabled@test.com")).thenReturn(Optional.of(disabled));

            assertThatThrownBy(() -> authService.login(new LoginRequest("disabled@test.com", "anypass")))
                    .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class)
                    .hasMessage("Invalid email or password");
        }

        @Test
        @DisplayName("Locked account returns same message as wrong password")
        void lockedAccountSameMessageAsWrongPassword() {
            User locked = User.builder()
                    .id(1L).email("locked@test.com").password("encoded")
                    .enabled(true).accountNonLocked(false)
                    .lockoutUntil(LocalDateTime.now().plusMinutes(25))
                    .failedLoginAttempts(5).provider("LOCAL").build();

            when(userRepository.findByEmail("locked@test.com")).thenReturn(Optional.of(locked));

            assertThatThrownBy(() -> authService.login(new LoginRequest("locked@test.com", "anypass")))
                    .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class)
                    .hasMessage("Invalid email or password");
        }
    }
}
