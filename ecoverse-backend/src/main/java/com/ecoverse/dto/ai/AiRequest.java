package com.ecoverse.dto.ai;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiRequest {
    @Size(min = 1, max = 5000, message = "Message must be between 1 and 5000 characters")
    private String message;       // User's question/prompt

    @Size(max = 200, message = "Context must be at most 200 characters")
    private String context;       // "carbon", "health", "weather", "general", "eco-tips"

    private Double carbonToday;   // User's today carbon (for context)
    private Double aqi;          // Current AQI

    @Size(max = 100, message = "City must be at most 100 characters")
    private String city;         // User's city

    @Size(max = 100, message = "Conversation ID must be at most 100 characters")
    private String conversationId; // For Spring AI ChatMemory session tracking
}
