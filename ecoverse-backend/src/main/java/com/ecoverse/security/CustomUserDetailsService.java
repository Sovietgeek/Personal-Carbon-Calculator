package com.ecoverse.security;

import com.ecoverse.model.User;
import com.ecoverse.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email: " + email));

        // Use 7-arg constructor to pass enabled and accountNonLocked from the User entity.
        // The 3-arg constructor defaults enabled=true, which would allow
        // disabled users to access authenticated endpoints.
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                Boolean.TRUE.equals(user.getEnabled()),         // enabled
                true,                                              // accountNonExpired
                true,                                              // credentialsNonExpired
                Boolean.TRUE.equals(user.getAccountNonLocked()), // accountNonLocked
                getAuthorities(user)
        );
    }

    /**
     * Convert the user's role to Spring Security granted authorities.
     * Uses the "ROLE_" prefix convention required by Spring Security's
     * hasRole() and @PreAuthorize("hasRole('ADMIN')") expressions.
     */
    private Collection<? extends GrantedAuthority> getAuthorities(User user) {
        if (user.getRole() == null) {
            return List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }
}
