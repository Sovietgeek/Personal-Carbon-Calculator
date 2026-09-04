package com.ecoverse.security;

import com.ecoverse.model.User;
import com.ecoverse.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Tests for CustomUserDetailsService security-critical behavior.
 *
 * Phase A requirements verified:
 * - Disabled users (enabled=false) return a UserDetails with isEnabled()=false
 * - Active users (enabled=true) return a UserDetails with isEnabled()=true
 * - Locked users (accountNonLocked=false) return a UserDetails with isAccountNonLocked()=false
 * - Non-existent email throws UsernameNotFoundException
 */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock private UserRepository userRepository;

    @InjectMocks private CustomUserDetailsService service;

    private User disabledUser;
    private User verifiedUser;
    private User lockedUser;

    @BeforeEach
    void setUp() {
        disabledUser = User.builder()
                .id(1L)
                .email("disabled@example.com")
                .password("encoded-password")
                .enabled(false)
                .accountNonLocked(true)
                .build();

        verifiedUser = User.builder()
                .id(2L)
                .email("verified@example.com")
                .password("encoded-password")
                .enabled(true)
                .accountNonLocked(true)
                .build();

        lockedUser = User.builder()
                .id(3L)
                .email("locked@example.com")
                .password("encoded-password")
                .enabled(true)
                .accountNonLocked(false)
                .lockoutUntil(LocalDateTime.now().plusMinutes(30))
                .build();
    }

    @Test
    @DisplayName("Disabled user has isEnabled()=false in UserDetails")
    void disabledUserHasDisabledUserDetails() {
        when(userRepository.findByEmail("disabled@example.com"))
                .thenReturn(Optional.of(disabledUser));

        UserDetails details = service.loadUserByUsername("disabled@example.com");

        assertThat(details.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Verified user has isEnabled()=true in UserDetails")
    void verifiedUserHasEnabledUserDetails() {
        when(userRepository.findByEmail("verified@example.com"))
                .thenReturn(Optional.of(verifiedUser));

        UserDetails details = service.loadUserByUsername("verified@example.com");

        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Locked user has isAccountNonLocked()=false in UserDetails")
    void lockedUserHasNonLockedFalse() {
        when(userRepository.findByEmail("locked@example.com"))
                .thenReturn(Optional.of(lockedUser));

        UserDetails details = service.loadUserByUsername("locked@example.com");

        assertThat(details.isAccountNonLocked()).isFalse();
    }

    @Test
    @DisplayName("Non-existent email throws UsernameNotFoundException")
    void nonExistentEmailThrowsException() {
        when(userRepository.findByEmail("nobody@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nobody@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
