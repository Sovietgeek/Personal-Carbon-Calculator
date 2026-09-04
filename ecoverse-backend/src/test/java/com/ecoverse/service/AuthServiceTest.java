package com.ecoverse.service;

import com.ecoverse.dto.auth.AuthResponse;
import com.ecoverse.dto.auth.LoginRequest;
import com.ecoverse.dto.auth.RegisterRequest;
import com.ecoverse.exception.BadRequestException;
import com.ecoverse.exception.ResourceNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import com.ecoverse.model.User;
import com.ecoverse.repository.UserRepository;
import com.ecoverse.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ecoverse.service.EmailService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for AuthService security-critical behavior.
 *
 * Phase C requirements verified:
 * - New users start active with enabled=true
 * - Registration does NOT return tokens (user signs in explicitly afterward)
 * - Disabled users cannot log in
 * - Active users can log in immediately after registration
 * - Legacy verification code cannot re-enable disabled accounts
 * - Login returns generic "Invalid email or password" for ALL failures (anti-enumeration)
 * - Non-existent email, wrong password, disabled, and locked all return the same message
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private AuditLogService auditLogService;
    @Mock private EmailService emailService;

    @InjectMocks private AuthService authService;

    private RegisterRequest registerRequest;
    private User disabledUser;
    private User verifiedUser;

    @BeforeEach
    void setUp() {
        registerRequest = RegisterRequest.builder()
                .name("Test User")
                .email("test@example.com")
                .password("Str0ngP@ss!")
                .country("US")
                .build();

        disabledUser = User.builder()
                .id(1L)
                .name("Test User")
                .email("test@example.com")
                .password("encoded-password")
                .enabled(false)
                .accountNonLocked(true)
                .failedLoginAttempts(0)
                .provider("LOCAL")
                .emailVerificationToken("verification-token-123")
                .verificationTokenExpiry(LocalDateTime.now().plusHours(24))
                .build();

        verifiedUser = User.builder()
                .id(2L)
                .name("Verified User")
                .email("verified@example.com")
                .password("encoded-password")
                .enabled(true)
                .accountNonLocked(true)
                .failedLoginAttempts(0)
                .provider("LOCAL")
                .build();
    }

    // ==================================================================
    // REGISTRATION TESTS
    // ==================================================================

    @Nested
    @DisplayName("Registration")
    class Registration {

        @Test
        @DisplayName("New user starts active (enabled=true)")
        void newUserStartsActive() {
            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(passwordEncoder.encode("Str0ngP@ss!")).thenReturn("encoded-password");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            AuthResponse response = authService.register(registerRequest);

            // New password accounts are active immediately; only an admin
            // disablement should make enabled=false.
            verify(userRepository).save(argThat(user ->
                    Boolean.TRUE.equals(user.getEnabled()) &&
                    Boolean.TRUE.equals(user.getAccountNonLocked()) &&
                    user.getFailedLoginAttempts() == 0 &&
                    user.getEmailVerificationToken() == null &&
                    user.getVerificationTokenExpiry() == null &&
                    user.getRole() != null && user.getRole().name().equals("USER")
            ));

            // Verify no tokens were returned
            assertThat(response.getAccessToken()).isNull();
            assertThat(response.getRefreshToken()).isNull();
            assertThat(response.getExpiresIn()).isNull();
        }

        @Test
        @DisplayName("Registration does NOT return access or refresh tokens")
        void registrationDoesNotReturnTokens() {
            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(passwordEncoder.encode("Str0ngP@ss!")).thenReturn("encoded-password");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            AuthResponse response = authService.register(registerRequest);

            // Registration is separate from login, so it still returns no tokens.
            assertThat(response.getAccessToken()).isNull();
            assertThat(response.getRefreshToken()).isNull();

            // JwtTokenProvider must NOT be called to generate tokens
            verify(jwtTokenProvider, never()).generateAccessToken(anyLong(), anyString());
            verify(jwtTokenProvider, never()).generateRefreshToken(anyLong());
        }

        @Test
        @DisplayName("Registration does not depend on verification email")
        void registrationDoesNotSendVerificationEmail() {
            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(passwordEncoder.encode("Str0ngP@ss!")).thenReturn("encoded-password");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            authService.register(registerRequest);

            verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
        }

        @Test
        @DisplayName("Registration with duplicate email throws BadRequestException")
        void duplicateEmailThrowsException() {
            when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(registerRequest))
                    .isInstanceOf(BadRequestException.class);

            // User must NOT be saved
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Registration does not create a verification token")
        void registrationDoesNotCreateVerificationToken() {
            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(passwordEncoder.encode("Str0ngP@ss!")).thenReturn("encoded-password");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            authService.register(registerRequest);

            verify(userRepository).save(argThat(user ->
                    user.getEmailVerificationToken() == null &&
                    user.getVerificationTokenExpiry() == null
            ));
        }

        @Test
        @DisplayName("Registration ALWAYS creates role=USER — client cannot escalate to ADMIN/SELLER")
        void registrationCannotEscalateRole() {
            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(passwordEncoder.encode("Str0ngP@ss!")).thenReturn("encoded-password");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            authService.register(registerRequest);

            // The role must be forced to USER regardless of any client-supplied value.
            // RegisterRequest has NO role field — escalation via JSON payload is impossible.
            verify(userRepository).save(argThat(user ->
                    user.getRole() != null &&
                    user.getRole().name().equals("USER")
            ));
        }

        @Test
        @DisplayName("Registration does not depend on SMTP availability")
        void registrationSucceedsWithoutEmailService() {
            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(passwordEncoder.encode("Str0ngP@ss!")).thenReturn("encoded-password");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            assertThatCode(() -> authService.register(registerRequest))
                    .doesNotThrowAnyException();

            verify(userRepository).save(any(User.class));
            verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
        }

        @Test
        @DisplayName("Registration with weak password (too short) throws BadRequestException")
        void weakPasswordRejected() {
            RegisterRequest weak = RegisterRequest.builder()
                    .name("Weak")
                    .email("weak@example.com")
                    .password("abc")  // < 8 chars
                    .country("US")
                    .build();

            when(userRepository.existsByEmail("weak@example.com")).thenReturn(false);

            assertThatThrownBy(() -> authService.register(weak))
                    .isInstanceOf(BadRequestException.class);

            // User must NOT be saved
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Registration with blacklisted common password throws BadRequestException")
        void blacklistedPasswordRejected() {
            RegisterRequest weak = RegisterRequest.builder()
                    .name("Weak")
                    .email("weak2@example.com")
                    .password("password123")  // blacklisted common password
                    .country("US")
                    .build();

            when(userRepository.existsByEmail("weak2@example.com")).thenReturn(false);

            assertThatThrownBy(() -> authService.register(weak))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    // ==================================================================
    // LOGIN TESTS
    // ==================================================================

    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        @DisplayName("Disabled user cannot log in — returns generic authentication failure")
        void disabledUserCannotLogin() {
            LoginRequest loginRequest = new LoginRequest("test@example.com", "Str0ngP@ss!");
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(disabledUser));

            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid email or password");

            verify(jwtTokenProvider, never()).generateAccessToken(anyLong(), anyString());
            verify(jwtTokenProvider, never()).generateRefreshToken(anyLong());
        }

        @Test
        @DisplayName("Active user can log in")
        void activeUserCanLogin() {
            LoginRequest loginRequest = new LoginRequest("verified@example.com", "Str0ngP@ss!");
            when(userRepository.findByEmail("verified@example.com")).thenReturn(Optional.of(verifiedUser));
            when(passwordEncoder.matches("Str0ngP@ss!", "encoded-password")).thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(2L, "verified@example.com")).thenReturn("access-token");
            when(jwtTokenProvider.generateRefreshToken(2L)).thenReturn("refresh-token");

            AuthResponse response = authService.login(loginRequest);

            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        }

        @Test
        @DisplayName("Disabled user check happens BEFORE password check")
        void disabledCheckBeforePasswordCheck() {
            LoginRequest loginRequest = new LoginRequest("test@example.com", "wrong-password");
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(disabledUser));

            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid email or password");

            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("Active account can log in immediately after registration")
        void activeAccountCanLogInImmediatelyAfterRegistration() {
            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(passwordEncoder.encode("Str0ngP@ss!")).thenReturn("encoded-password");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                if (saved.getId() == null) saved.setId(2L);
                return saved;
            });
            when(userRepository.findByEmail("test@example.com"))
                    .thenAnswer(invocation -> Optional.of(User.builder()
                            .id(2L).name("Test User").email("test@example.com")
                            .password("encoded-password").enabled(true)
                            .accountNonLocked(true).failedLoginAttempts(0).build()));
            when(passwordEncoder.matches("Str0ngP@ss!", "encoded-password")).thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(2L, "test@example.com")).thenReturn("access-token");
            when(jwtTokenProvider.generateRefreshToken(2L)).thenReturn("refresh-token");

            authService.register(registerRequest);
            AuthResponse response = authService.login(
                    new LoginRequest("test@example.com", "Str0ngP@ss!"));

            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        }

        @Test
        @DisplayName("Non-existent email returns generic message (anti-enumeration)")
        void nonExistentEmailReturnsGenericMessage() {
            LoginRequest loginRequest = new LoginRequest("nobody@example.com", "password");
            when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

            // Same generic message as wrong password — prevents email enumeration
            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid email or password");
        }

        @Test
        @DisplayName("Wrong password increments failed login attempts — returns generic message")
        void wrongPasswordIncrementsFailedAttempts() {
            User user = User.builder()
                    .id(2L).email("verified@example.com").password("encoded-password")
                    .enabled(true).accountNonLocked(true).failedLoginAttempts(0)
                    .build();

            LoginRequest loginRequest = new LoginRequest("verified@example.com", "wrong");
            when(userRepository.findByEmail("verified@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "encoded-password")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid email or password");

            verify(userRepository).save(argThat(u -> u.getFailedLoginAttempts() == 1));
        }

        @Test
        @DisplayName("Account locks after 5 failed login attempts — returns generic message")
        void accountLocksAfterFiveFailedAttempts() {
            User user = User.builder()
                    .id(2L).email("verified@example.com").password("encoded-password")
                    .enabled(true).accountNonLocked(true).failedLoginAttempts(4)
                    .build();

            LoginRequest loginRequest = new LoginRequest("verified@example.com", "wrong");
            when(userRepository.findByEmail("verified@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "encoded-password")).thenReturn(false);

            // Generic message — don't reveal that account is now locked
            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid email or password");

            verify(userRepository).save(argThat(u ->
                    !Boolean.TRUE.equals(u.getAccountNonLocked())
            ));
        }
    }

    // ==================================================================
    // EMAIL VERIFICATION TESTS
    // ==================================================================

    @Nested
    @DisplayName("Email Verification")
    class EmailVerification {

        @Test
        @DisplayName("Legacy verification token never enables a disabled account")
        void legacyVerificationDoesNotEnableDisabledUser() {
            when(userRepository.findByEmailVerificationToken("valid-token"))
                    .thenReturn(Optional.of(disabledUser));

            assertThat(authService.verifyEmail("valid-token")).isFalse();

            verify(userRepository).save(argThat(user ->
                    !Boolean.TRUE.equals(user.getEnabled()) &&
                    user.getEmailVerificationToken() == null &&
                    user.getVerificationTokenExpiry() == null
            ));
        }

        @Test
        @DisplayName("Verification clears the token and expiry")
        void verificationClearsTokenAndExpiry() {
            when(userRepository.findByEmailVerificationToken("valid-token"))
                    .thenReturn(Optional.of(disabledUser));

            authService.verifyEmail("valid-token");

            verify(userRepository).save(argThat(user ->
                    user.getEmailVerificationToken() == null &&
                    user.getVerificationTokenExpiry() == null
            ));
        }

        @Test
        @DisplayName("Invalid verification token throws BadRequestException")
        void invalidTokenThrowsException() {
            when(userRepository.findByEmailVerificationToken("invalid-token"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.verifyEmail("invalid-token"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid verification token");
        }

        @Test
        @DisplayName("Expired verification token throws BadRequestException")
        void expiredTokenThrowsException() {
            User userWithExpiredToken = User.builder()
                    .id(1L).email("test@example.com").enabled(false)
                    .emailVerificationToken("expired-token")
                    .verificationTokenExpiry(LocalDateTime.now().minusHours(25)) // expired
                    .build();

            when(userRepository.findByEmailVerificationToken("expired-token"))
                    .thenReturn(Optional.of(userWithExpiredToken));

            assertThatThrownBy(() -> authService.verifyEmail("expired-token"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("Legacy token for an active account is consumed without changing activation")
        void activeAccountLegacyTokenDoesNotChangeActivation() {
            verifiedUser.setEmailVerificationToken("already-used-token");
            verifiedUser.setVerificationTokenExpiry(LocalDateTime.now().plusHours(1));
            when(userRepository.findByEmailVerificationToken("already-used-token"))
                    .thenReturn(Optional.of(verifiedUser));

            assertThat(authService.verifyEmail("already-used-token")).isTrue();

            verify(userRepository).save(argThat(user ->
                    Boolean.TRUE.equals(user.getEnabled()) &&
                    user.getEmailVerificationToken() == null &&
                    user.getVerificationTokenExpiry() == null
            ));
        }
    }

    // ==================================================================
    // RESEND VERIFICATION TESTS
    // ==================================================================

    @Nested
    @DisplayName("Resend Verification")
    class ResendVerification {

        @Test
        @DisplayName("Legacy resend verification is a no-op and reveals no account state")
        void resendVerificationIsNoOp() {
            assertThatCode(() -> authService.resendVerification("nobody@example.com"))
                    .doesNotThrowAnyException();

            verifyNoInteractions(userRepository, emailService);
        }
    }

    // ==================================================================
    // OAUTH2 LOGIN TESTS
    // ==================================================================

    @Nested
    @DisplayName("OAuth2 Login (Google)")
    class OAuth2Login {

        @Test
        @DisplayName("Google OAuth users start with enabled=true (email already verified by Google)")
        void googleOAuthUsersStartEnabled() {
            when(userRepository.findByEmail("google@example.com")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                saved.setId(3L);
                return saved;
            });
            when(jwtTokenProvider.generateAccessToken(anyLong(), anyString())).thenReturn("access");
            when(jwtTokenProvider.generateRefreshToken(anyLong())).thenReturn("refresh");

            AuthResponse response = authService.processOAuthLogin(
                    "google@example.com", "Google User", "http://pic.jpg", "google-sub-123");

            // Verify the user was saved with enabled=true
            verify(userRepository).save(argThat(user ->
                    Boolean.TRUE.equals(user.getEnabled())
            ));

            // Tokens ARE returned for Google OAuth (email already verified by Google)
            assertThat(response.getAccessToken()).isEqualTo("access");
            assertThat(response.getRefreshToken()).isEqualTo("refresh");
        }

        @Test
        @DisplayName("Google OAuth without email throws BadRequestException")
        void googleOAuthWithoutEmailThrows() {
            assertThatThrownBy(() -> authService.processOAuthLogin(null, "User", null, "sub"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("email");
        }

        @Test
        @DisplayName("Google OAuth cannot bypass a disabled existing account")
        void googleOAuthCannotBypassDisabledAccount() {
            when(userRepository.findByEmail("disabled@example.com"))
                    .thenReturn(Optional.of(disabledUser));

            assertThatThrownBy(() -> authService.processOAuthLogin(
                    "disabled@example.com", "Disabled", null, "google-disabled"))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Authentication failed");

            verify(userRepository, never()).save(any(User.class));
            verify(jwtTokenProvider, never()).generateAccessToken(anyLong(), anyString());
            verify(jwtTokenProvider, never()).generateRefreshToken(anyLong());
        }
    }
}
