package com.ecoverse.controller;

import com.ecoverse.dto.ApiResponse;
import com.ecoverse.dto.profile.ProfileResponse;
import com.ecoverse.dto.profile.ProfileUpdateRequest;
import com.ecoverse.model.User;
import com.ecoverse.repository.CarbonEntryRepository;
import com.ecoverse.repository.HealthLogRepository;
import com.ecoverse.repository.NoteRepository;
import com.ecoverse.repository.UserRepository;
import com.ecoverse.security.JwtTokenProvider;
import com.ecoverse.service.AuditLogService;
import com.ecoverse.service.AuthService;
import com.ecoverse.util.CookieUtil;
import com.ecoverse.util.InputSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private CarbonEntryRepository carbonEntryRepository;
    @Autowired private HealthLogRepository healthLogRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private AuditLogService auditLogService;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private CookieUtil cookieUtil;

    @GetMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> getCurrentUser() {
        Long userId = getCurrentUserId();
        User user = authService.getCurrentUser(userId);
        ProfileResponse response = mapToProfileResponse(user);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        Long userId = getCurrentUserId();
        User user = authService.getCurrentUser(userId);

        // Sanitize and update fields
        if (request.getName() != null) {
            user.setName(InputSanitizer.sanitizeName(request.getName()));
        }
        if (request.getCarbonBudget() != null) {
            user.setCarbonBudget(request.getCarbonBudget());
        }
        if (request.getTimezone() != null) {
            user.setTimezone(request.getTimezone());
        }
        if (request.getGoalsSteps() != null) {
            user.setGoalsSteps(request.getGoalsSteps());
        }
        if (request.getGoalsSleep() != null) {
            user.setGoalsSleep(request.getGoalsSleep());
        }
        if (request.getGoalsWater() != null) {
            user.setGoalsWater(request.getGoalsWater());
        }
        if (request.getGoalsCalories() != null) {
            user.setGoalsCalories(request.getGoalsCalories());
        }

        user = userRepository.save(user);

        auditLogService.log(userId, "PROFILE_UPDATE", "/api/profile");

        ProfileResponse response = mapToProfileResponse(user);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", response));
    }

    /**
     * Delete account — requires password confirmation.
     * Revokes all refresh tokens and clears the httpOnly cookie.
     */
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteAccount(@RequestBody Map<String, String> body,
                                                            HttpServletRequest request,
                                                            HttpServletResponse response) {
        Long userId = getCurrentUserId();
        User user = authService.getCurrentUser(userId);

        // SECURITY: Require password confirmation before account deletion
        String password = body.get("password");
        if (password == null || password.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Password confirmation is required to delete your account"));
        }

        if (!authService.checkPassword(userId, password)) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error("Incorrect password. Account deletion cancelled."));
        }

        // Audit log BEFORE deletion (user still exists)
        auditLogService.log(userId, "ACCOUNT_DELETE", "/api/profile");

        // Revoke all refresh tokens and permanently delete them
        jwtTokenProvider.deleteAllUserTokens(userId);

        // Clear the httpOnly cookie
        cookieUtil.clearRefreshTokenCookie(request, response);

        // Delete the user account
        userRepository.deleteById(userId);

        return ResponseEntity.ok(ApiResponse.success("Account deleted successfully", null));
    }

    @GetMapping("/export")
    public ResponseEntity<ApiResponse<Map<String, Object>>> exportData() {
        Long userId = getCurrentUserId();
        Map<String, Object> data = new HashMap<>();
        data.put("carbonEntries", carbonEntryRepository.findByUserId(userId));
        data.put("healthLogs", healthLogRepository.findByUserId(userId));
        data.put("notes", noteRepository.findByUserIdOrderByCreatedAtDesc(userId));

        auditLogService.log(userId, "DATA_EXPORT", "/api/profile/export");

        return ResponseEntity.ok(ApiResponse.success(data));
    }

    private ProfileResponse mapToProfileResponse(User user) {
        return ProfileResponse.builder()
                .id(user.getId()).name(user.getName()).email(user.getEmail()).country(user.getCountry())
                .city(user.getCity()).state(user.getState())
                .carbonBudget(user.getCarbonBudget()).isPremium(user.getIsPremium())
                .joinedDate(user.getJoinedDate() != null ? user.getJoinedDate().toLocalDate() : null)
                .bestStreak(user.getBestStreak()).goalsSteps(user.getGoalsSteps())
                .goalsSleep(user.getGoalsSleep()).goalsWater(user.getGoalsWater())
                .goalsCalories(user.getGoalsCalories()).timezone(user.getTimezone())
                .build();
    }

    /**
     * Update user's detected location (city, state, country, lat, lon).
     * Called by frontend after browser geolocation succeeds.
     */
    @PutMapping("/location")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateLocation(@RequestBody Map<String, Object> body) {
        Long userId = getCurrentUserId();
        User user = authService.getCurrentUser(userId);

        if (body.get("city") != null) {
            user.setCity(InputSanitizer.sanitize((String) body.get("city"), 100));
        }
        if (body.get("state") != null) {
            user.setState(InputSanitizer.sanitize((String) body.get("state"), 100));
        }
        if (body.get("country") != null) {
            user.setCountry(InputSanitizer.sanitize((String) body.get("country"), 10));
        }
        if (body.get("latitude") != null) {
            try { user.setLatitude(Double.parseDouble(body.get("latitude").toString())); } catch (NumberFormatException ignored) {}
        }
        if (body.get("longitude") != null) {
            try { user.setLongitude(Double.parseDouble(body.get("longitude").toString())); } catch (NumberFormatException ignored) {}
        }

        user = userRepository.save(user);

        auditLogService.log(userId, "LOCATION_UPDATE", "/api/profile/location");

        ProfileResponse response = mapToProfileResponse(user);
        return ResponseEntity.ok(ApiResponse.success("Location updated", response));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getPrincipal().toString());
    }
}
