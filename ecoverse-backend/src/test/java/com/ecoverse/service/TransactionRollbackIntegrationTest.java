package com.ecoverse.service;

import com.ecoverse.dto.auth.AuthResponse;
import com.ecoverse.dto.auth.LoginRequest;
import com.ecoverse.dto.auth.RegisterRequest;
import com.ecoverse.exception.BadRequestException;
import com.ecoverse.model.RefreshToken;
import com.ecoverse.model.User;
import com.ecoverse.repository.RefreshTokenRepository;
import com.ecoverse.repository.UserRepository;
import com.ecoverse.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for transaction rollback in auth operations.
 * Uses the default H2 in-memory profile (no PostgreSQL needed).
 *
 * Verifies that when an operation fails partway through, all database
 * changes are rolled back to maintain consistency.
 */
@SpringBootTest
@ActiveProfiles("default")
@TestPropertySource(properties = {
    "jwt.secret=test-secret-key-that-is-at-least-64-bytes-long-for-hs512-algorithm-testing-XXXXXX"
})
class TransactionRollbackIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private PasswordEncoder passwordEncoder;

    @Nested
    @DisplayName("Login Transaction Rollback")
    class LoginRollback {

        @Test
        @DisplayName("Failed login does not create orphan tokens")
        void failedLoginDoesNotCreateOrphanTokens() {
            // Register a verified user
            RegisterRequest regReq = RegisterRequest.builder()
                    .name("Test User").email("rollback@test.com")
                    .password("Str0ngP@ss!").country("US").build();

            AuthResponse regResponse = authService.register(regReq);
            Long userId = regResponse.getUser().getId();

            // Verify the user manually
            User user = userRepository.findById(userId).orElseThrow();
            user.setEnabled(true);
            userRepository.save(user);

            // Verify no refresh tokens exist before login attempt
            long tokensBefore = refreshTokenRepository.findByUserId(userId).size();

            // Attempt login with wrong password
            assertThatThrownBy(() -> authService.login(
                    new LoginRequest("rollback@test.com", "WrongP@ss1")))
                    .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);

            // No new tokens should have been created (login failed before token generation)
            long tokensAfter = refreshTokenRepository.findByUserId(userId).size();
            assertThat(tokensAfter).isEqualTo(tokensBefore);
        }

        @Test
        @DisplayName("Successful login creates exactly one refresh token")
        void successfulLoginCreatesOneRefreshToken() {
            // Register and verify a user
            RegisterRequest regReq = RegisterRequest.builder()
                    .name("Token User").email("tokens@test.com")
                    .password("Str0ngP@ss!").country("US").build();

            AuthResponse regResponse = authService.register(regReq);
            Long userId = regResponse.getUser().getId();

            User user = userRepository.findById(userId).orElseThrow();
            user.setEnabled(true);
            userRepository.save(user);

            // Login successfully
            AuthResponse loginResponse = authService.login(
                    new LoginRequest("tokens@test.com", "Str0ngP@ss!"));

            assertThat(loginResponse.getAccessToken()).isNotNull();
            assertThat(loginResponse.getRefreshToken()).isNotNull();

            // Verify exactly one non-revoked refresh token exists
            long activeTokens = refreshTokenRepository.countByUserIdAndRevokedFalse(userId);
            assertThat(activeTokens).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Password Reset Transaction Rollback")
    class PasswordResetRollback {

        @Test
        @DisplayName("Password reset clears reset token even if token revocation fails")
        void passwordResetClearsResetToken() {
            // Register and verify a user
            RegisterRequest regReq = RegisterRequest.builder()
                    .name("Reset User").email("reset@test.com")
                    .password("Str0ngP@ss!").country("US").build();

            AuthResponse regResponse = authService.register(regReq);
            Long userId = regResponse.getUser().getId();

            User user = userRepository.findById(userId).orElseThrow();
            user.setEnabled(true);
            userRepository.save(user);

            // First, do a successful login to create a refresh token
            authService.login(new LoginRequest("reset@test.com", "Str0ngP@ss!"));
            assertThat(refreshTokenRepository.countByUserIdAndRevokedFalse(userId)).isGreaterThanOrEqualTo(1);

            // Request password reset
            authService.forgotPassword("reset@test.com");

            // Get the reset token
            user = userRepository.findById(userId).orElseThrow();
            String resetToken = user.getPasswordResetToken();
            assertThat(resetToken).isNotNull();

            // Reset the password
            authService.resetPassword(resetToken, "NewStr0ngP@ss1!");

            // Verify reset token is cleared
            user = userRepository.findById(userId).orElseThrow();
            assertThat(user.getPasswordResetToken()).isNull();

            // Verify all refresh tokens are revoked
            assertThat(refreshTokenRepository.countByUserIdAndRevokedFalse(userId)).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Registration Transaction Consistency")
    class RegistrationRollback {

        @Test
        @DisplayName("Duplicate email registration does not create partial user")
        void duplicateEmailDoesNotCreatePartialUser() {
            RegisterRequest req1 = RegisterRequest.builder()
                    .name("First User").email("dup@test.com")
                    .password("Str0ngP@ss!").country("US").build();

            authService.register(req1);

            long userCountBefore = userRepository.count();

            // Try to register with the same email
            RegisterRequest req2 = RegisterRequest.builder()
                    .name("Second User").email("dup@test.com")
                    .password("An0therP@ss!").country("UK").build();

            assertThatThrownBy(() -> authService.register(req2))
                    .isInstanceOf(BadRequestException.class);

            // No new user should have been created
            long userCountAfter = userRepository.count();
            assertThat(userCountAfter).isEqualTo(userCountBefore);
        }
    }
}
