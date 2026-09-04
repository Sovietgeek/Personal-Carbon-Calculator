package com.ecoverse.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private Long expiresIn;          // Access token expiry in seconds
    private UserDTO user;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserDTO {
        private Long id;
        private String name;
        private String email;
        private String country;
        private String city;
        private String state;
        private BigDecimal carbonBudget;
        private Boolean isPremium;
        private LocalDate joinedDate;
        private String profileImage;
        private String provider;
        private String role;
        private String timezone;
    }
}
