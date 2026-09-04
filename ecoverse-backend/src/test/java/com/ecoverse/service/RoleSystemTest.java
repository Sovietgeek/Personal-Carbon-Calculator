package com.ecoverse.service;

import com.ecoverse.dto.auth.AuthResponse;
import com.ecoverse.dto.auth.LoginRequest;
import com.ecoverse.dto.auth.RegisterRequest;
import com.ecoverse.model.Role;
import com.ecoverse.model.User;
import com.ecoverse.repository.UserRepository;
import com.ecoverse.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the Role system (B1).
 *
 * Requirements verified:
 * - New users default to USER role
 * - Google OAuth users default to USER role
 * - Registration does NOT return tokens (must verify email first)
 * - No public endpoint can change a user's role
 */
@ExtendWith(MockitoExtension.class)
class RoleSystemTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private AuditLogService auditLogService;
    @Mock private JavaMailSender mailSender;

    @InjectMocks private AuthService authService;

    @Nested
    @DisplayName("Default Role Tests")
    class DefaultRole {

        @Test
        @DisplayName("New LOCAL user defaults to USER role")
        void newLocalUserDefaultsToUser() {
            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(passwordEncoder.encode("Str0ngP@ss!")).thenReturn("encoded-password");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            RegisterRequest req = RegisterRequest.builder()
                    .name("Test").email("test@example.com").password("Str0ngP@ss!").country("US").build();
            AuthResponse response = authService.register(req);

            // Verify user was saved with USER role
            verify(userRepository).save(argThat(user ->
                    user.getRole() == Role.USER
            ));

            // Registration remains separate from login, so it returns no tokens
            assertThat(response.getAccessToken()).isNull();
            assertThat(response.getRefreshToken()).isNull();
        }

        @Test
        @DisplayName("Google OAuth user defaults to USER role")
        void googleOAuthUserDefaultsToUser() {
            when(userRepository.findByEmail("google@example.com")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                saved.setId(2L);
                return saved;
            });
            when(jwtTokenProvider.generateAccessToken(anyLong(), anyString())).thenReturn("access");
            when(jwtTokenProvider.generateRefreshToken(anyLong())).thenReturn("refresh");

            AuthResponse response = authService.processOAuthLogin(
                    "google@example.com", "Google User", "http://pic.jpg", "sub123");

            // Verify user was saved with USER role
            verify(userRepository).save(argThat(user ->
                    user.getRole() == Role.USER
            ));

            // Google OAuth users DO get tokens (email already verified by Google)
            assertThat(response.getAccessToken()).isEqualTo("access");
            assertThat(response.getRefreshToken()).isEqualTo("refresh");
        }

        @Test
        @DisplayName("Existing user's role is preserved on Google OAuth login")
        void existingUserRolePreservedOnGoogleLogin() {
            User existingSeller = User.builder()
                    .id(3L).email("seller@example.com").password("encoded")
                    .enabled(true).accountNonLocked(true).role(Role.SELLER).provider("LOCAL")
                    .build();

            when(userRepository.findByEmail("seller@example.com")).thenReturn(Optional.of(existingSeller));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(jwtTokenProvider.generateAccessToken(anyLong(), anyString())).thenReturn("access");
            when(jwtTokenProvider.generateRefreshToken(anyLong())).thenReturn("refresh");

            authService.processOAuthLogin("seller@example.com", "Seller", null, "sub456");

            // Role should remain SELLER, not be changed to USER
            verify(userRepository).save(argThat(user ->
                    user.getRole() == Role.SELLER
            ));
        }
    }

    @Nested
    @DisplayName("Role Promotion Security Tests")
    class RolePromotionSecurity {

        @Test
        @DisplayName("Self-promotion to ADMIN is not possible via register endpoint")
        void selfPromotionViaRegisterBlocked() {
            when(userRepository.existsByEmail("attacker@example.com")).thenReturn(false);
            when(passwordEncoder.encode("Str0ngP@ss!")).thenReturn("encoded-password");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                saved.setId(10L);
                return saved;
            });

            // The RegisterRequest DTO has NO role field — it cannot set ADMIN
            RegisterRequest req = RegisterRequest.builder()
                    .name("Attacker").email("attacker@example.com").password("Str0ngP@ss!").country("US").build();
            authService.register(req);

            // User must be created with USER role regardless
            verify(userRepository).save(argThat(user ->
                    user.getRole() == Role.USER
            ));
        }

        @Test
        @DisplayName("Login endpoint does not change user role")
        void loginDoesNotChangeRole() {
            User user = User.builder()
                    .id(5L).email("user@example.com").password("encoded")
                    .enabled(true).accountNonLocked(true).role(Role.USER)
                    .failedLoginAttempts(0)
                    .build();

            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Str0ngP@ss!", "encoded")).thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(anyLong(), anyString())).thenReturn("access");
            when(jwtTokenProvider.generateRefreshToken(anyLong())).thenReturn("refresh");

            authService.login(new LoginRequest("user@example.com", "Str0ngP@ss!"));

            // Role must still be USER — login never promotes
            verify(userRepository).save(argThat(u ->
                    u.getRole() == Role.USER
            ));
        }
    }
}
