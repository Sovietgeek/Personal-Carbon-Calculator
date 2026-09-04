package com.ecoverse.controller;

import com.ecoverse.dto.ApiResponse;
import com.ecoverse.dto.achievement.AchievementResponse;
import com.ecoverse.service.AchievementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/achievements")
public class AchievementController {

    @Autowired
    private AchievementService achievementService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AchievementResponse>>> getAchievements() {
        Long userId = getCurrentUserId();
        List<AchievementResponse> responses = achievementService.getAchievements(userId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @PostMapping("/check")
    public ResponseEntity<ApiResponse<List<AchievementResponse>>> checkAndUnlockBadges() {
        Long userId = getCurrentUserId();
        List<AchievementResponse> newlyUnlocked = achievementService.checkAndUnlockBadges(userId);
        return ResponseEntity.ok(ApiResponse.success("Badge check completed", newlyUnlocked));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getPrincipal().toString());
    }
}
