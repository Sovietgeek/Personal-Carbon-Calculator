package com.ecoverse.dto.carbon;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChallengeResponse {
    private List<Challenge> activeChallenges;
    private List<Challenge> completedChallenges;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Challenge {
        private String id;
        private String title;
        private String description;
        private String icon;
        private Integer durationDays;
        private Double targetKg;           // CO2 target to save
        private Double currentKg;          // Current progress
        private Integer progressPercent;   // 0-100
        private Integer daysLeft;
        private Boolean completed;
        private Integer participants;      // How many people joined
        private Double rewardBadges;       // Badges earned on completion
    }
}
