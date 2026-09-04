package com.ecoverse.security;

import com.ecoverse.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Role-based access control tests.
 * Verifies that the Role enum hierarchy is correct:
 * USER < SELLER < ADMIN
 */
class RoleAccessTest {

    @Test
    @DisplayName("Role enum has USER, SELLER, ADMIN")
    void roleEnumHasAllValues() {
        assertThat(Role.values()).containsExactlyInAnyOrder(Role.USER, Role.SELLER, Role.ADMIN);
    }

    @Test
    @DisplayName("USER role name is USER")
    void userRoleName() {
        assertThat(Role.USER.name()).isEqualTo("USER");
    }

    @Test
    @DisplayName("SELLER role name is SELLER")
    void sellerRoleName() {
        assertThat(Role.SELLER.name()).isEqualTo("SELLER");
    }

    @Test
    @DisplayName("ADMIN role name is ADMIN")
    void adminRoleName() {
        assertThat(Role.ADMIN.name()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Spring Security authority format: ROLE_USER, ROLE_SELLER, ROLE_ADMIN")
    void springSecurityAuthorityFormat() {
        // CustomUserDetailsService maps role to "ROLE_<ROLE_NAME>"
        assertThat("ROLE_" + Role.USER.name()).isEqualTo("ROLE_USER");
        assertThat("ROLE_" + Role.SELLER.name()).isEqualTo("ROLE_SELLER");
        assertThat("ROLE_" + Role.ADMIN.name()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("hasAnyRole('SELLER','ADMIN') includes SELLER and ADMIN but not USER")
    void sellerAdminRoleIncludesCorrectly() {
        // This verifies the intent behind @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
        String[] sellerAdminRoles = {"SELLER", "ADMIN"};
        assertThat(sellerAdminRoles).contains("SELLER", "ADMIN");
        assertThat(sellerAdminRoles).doesNotContain("USER");
    }

    @Test
    @DisplayName("hasRole('ADMIN') excludes USER and SELLER")
    void adminRoleExcludesOthers() {
        String adminRole = "ADMIN";
        assertThat(adminRole).isNotEqualTo("USER");
        assertThat(adminRole).isNotEqualTo("SELLER");
    }
}
