package com.ecoverse.dto.achievement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AchievementResponse {

    private Long id;
    private String code;
    private String name;
    private String description;
    private String icon;
    private String category;
    private Boolean isUnlocked;
    private String unlockedAt; // nullable
}
