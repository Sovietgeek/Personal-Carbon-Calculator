package com.ecoverse.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiResponse {
    private String message;           // AI's response text
    private String type;              // "suggestion", "chat", "insight", "warning"
    private List<String> quickActions; // Suggested follow-up actions
    private String source;            // "gemini", "fallback"
}
