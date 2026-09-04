package com.ecoverse.config;

import com.ecoverse.model.Role;
import com.ecoverse.model.User;
import com.ecoverse.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Tests for AdminBootstrap (B2).
 *
 * Requirements verified:
 * - ADMIN_EMAIL env var promotes existing verified user to ADMIN
 * - Unverified users are NOT promoted
 * - Already-ADMIN users are not re-promoted
 * - Missing ADMIN_EMAIL does nothing
 * - Non-existent email does nothing (safe failure)
 */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock private UserRepository userRepository;

    @InjectMocks private AdminBootstrap adminBootstrap;

    private User verifiedUser;
    private User disabledUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        verifiedUser = User.builder()
                .id(1L).email("admin@ecoverse.app").password("encoded")
                .enabled(true).accountNonLocked(true).role(Role.USER)
                .build();

        disabledUser = User.builder()
                .id(2L).email("unverified@ecoverse.app").password("encoded")
                .enabled(false).accountNonLocked(true).role(Role.USER)
                .build();

        adminUser = User.builder()
                .id(3L).email("admin@ecoverse.app").password("encoded")
                .enabled(true).accountNonLocked(true).role(Role.ADMIN)
                .build();
    }

    @Nested
    @DisplayName("Admin Bootstrap from ADMIN_EMAIL")
    class Bootstrap {

        @Test
        @DisplayName("ADMIN_EMAIL promotes verified USER to ADMIN")
        void promotesVerifiedUserToAdmin() {
            setField(adminBootstrap, "adminEmail", "admin@ecoverse.app");
            when(userRepository.findByEmail("admin@ecoverse.app"))
                    .thenReturn(Optional.of(verifiedUser));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            adminBootstrap.bootstrapAdmin();

            verify(userRepository).save(argThat(user ->
                    user.getRole() == Role.ADMIN
            ));
        }

        @Test
        @DisplayName("ADMIN_EMAIL does NOT promote disabled user")
        void doesNotPromoteDisabledUser() {
            setField(adminBootstrap, "adminEmail", "unverified@ecoverse.app");
            when(userRepository.findByEmail("unverified@ecoverse.app"))
                    .thenReturn(Optional.of(disabledUser));

            adminBootstrap.bootstrapAdmin();

            // User must NOT be saved with ADMIN role
            verify(userRepository, never()).save(argThat(user ->
                    user.getRole() == Role.ADMIN
            ));
        }

        @Test
        @DisplayName("Already-ADMIN user is not modified")
        void alreadyAdminNotModified() {
            setField(adminBootstrap, "adminEmail", "admin@ecoverse.app");
            when(userRepository.findByEmail("admin@ecoverse.app"))
                    .thenReturn(Optional.of(adminUser));

            adminBootstrap.bootstrapAdmin();

            // No save should happen
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Missing ADMIN_EMAIL does nothing")
        void missingAdminEmailDoesNothing() {
            setField(adminBootstrap, "adminEmail", "");

            adminBootstrap.bootstrapAdmin();

            // No DB queries at all
            verify(userRepository, never()).findByEmail(anyString());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Non-existent email does nothing (safe failure)")
        void nonExistentEmailDoesNothing() {
            setField(adminBootstrap, "adminEmail", "nobody@ecoverse.app");
            when(userRepository.findByEmail("nobody@ecoverse.app"))
                    .thenReturn(Optional.empty());

            adminBootstrap.bootstrapAdmin();

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("SELLER user is promoted to ADMIN")
        void sellerPromotedToAdmin() {
            User seller = User.builder()
                    .id(4L).email("seller@ecoverse.app").password("encoded")
                    .enabled(true).accountNonLocked(true).role(Role.SELLER)
                    .build();

            setField(adminBootstrap, "adminEmail", "seller@ecoverse.app");
            when(userRepository.findByEmail("seller@ecoverse.app"))
                    .thenReturn(Optional.of(seller));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            adminBootstrap.bootstrapAdmin();

            verify(userRepository).save(argThat(user ->
                    user.getRole() == Role.ADMIN
            ));
        }
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}
