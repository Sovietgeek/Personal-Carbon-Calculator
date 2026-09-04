package com.ecoverse.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(min = 1, max = 255, message = "Email must be between 1 and 255 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 1, max = 128, message = "Password must be between 1 and 128 characters")
    private String password;
}
