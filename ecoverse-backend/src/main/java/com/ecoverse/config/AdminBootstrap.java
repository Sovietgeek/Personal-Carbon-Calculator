package com.ecoverse.config;

import com.ecoverse.model.Role;
import com.ecoverse.model.User;
import com.ecoverse.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Bootstraps the first ADMIN user from protected environment variables.
 *
 * SECURITY DESIGN:
 * - No HTTP endpoint can create an admin (no POST /make-admin)
 * - No request body can set role=ADMIN
 * - Only the ADMIN_EMAIL environment variable can promote/create an admin
 * - If the user exists: promotes them to ADMIN
 * - If the user doesn't exist AND ADMIN_PASSWORD is set: creates them
 * - After the admin exists, both env vars can be removed
 *
 * Usage (promote existing user):
 *   Set ADMIN_EMAIL=admin@ecoverse.app in your environment
 *   Register that email normally (via /api/auth/register)
 *   The next startup will promote them to ADMIN
 *
 * Usage (create admin from scratch):
 *   Set ADMIN_EMAIL=admin@ecoverse.app
 *   Set ADMIN_PASSWORD=YourSecurePassword123
 *   On startup, the admin user is created with that password
 */
@Configuration
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void bootstrapAdmin() {
        if (adminEmail == null || adminEmail.isBlank()) {
            return; // No admin email configured — skip
        }

        var existing = userRepository.findByEmail(adminEmail);

        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getRole() == Role.ADMIN) {
                log.info("Admin user already exists: {}", adminEmail);
                return; // Already admin — nothing to do
            }

            if (!Boolean.TRUE.equals(user.getEnabled())) {
                log.warn("ADMIN_EMAIL={} points to a disabled user. "
                        + "Enable the account first, then restart to promote.", adminEmail);
                return;
            }

            Role previousRole = user.getRole();
            user.setRole(Role.ADMIN);
            userRepository.save(user);

            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("✅  ADMIN BOOTSTRAP: Promoted {} from {} to ADMIN", adminEmail, previousRole);
            log.info("   You can now remove ADMIN_EMAIL from your environment.");
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        } else if (adminPassword != null && !adminPassword.isBlank()) {
            // Admin doesn't exist yet — create them
            User admin = new User();
            admin.setName("Admin");
            admin.setEmail(adminEmail);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRole(Role.ADMIN);
            admin.setEnabled(true);
            admin.setAccountNonLocked(true);
            admin.setProvider("LOCAL");
            userRepository.save(admin);

            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("✅  ADMIN BOOTSTRAP: Created admin user {}", adminEmail);
            log.info("   You can now remove ADMIN_EMAIL and ADMIN_PASSWORD from your environment.");
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        } else {
            log.info("ADMIN_EMAIL={} is set but no user with that email exists yet. "
                    + "Either register that email, or set ADMIN_PASSWORD to create the admin automatically.",
                    adminEmail);
        }
    }
}
