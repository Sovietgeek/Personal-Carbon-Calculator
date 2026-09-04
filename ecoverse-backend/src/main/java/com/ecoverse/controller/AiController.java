package com.ecoverse.controller;

import com.ecoverse.dto.ApiResponse;
import com.ecoverse.dto.ai.AiRequest;
import com.ecoverse.dto.ai.AiResponse;
import com.ecoverse.service.AiService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private AiService aiService;

    /**
     * Chat with AI assistant (synchronous — backward compatible)
     * POST /api/ai/chat
     */
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<AiResponse>> chat(
            @Valid @RequestBody AiRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (request.getMessage() != null && request.getMessage().length() > 5000) {
            request.setMessage(request.getMessage().substring(0, 5000));
        }
        // Use user ID as conversation ID for memory if not provided
        if (request.getConversationId() == null && userDetails != null) {
            request.setConversationId("user-" + userDetails.getUsername());
        }
        AiResponse response = aiService.chat(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Stream chat with AI assistant (SSE — real-time token-by-token)
     * GET /api/ai/stream?message=...&conversationId=...
     *
     * Returns text/event-stream. Each event is a token chunk.
     * The stream ends with "[DONE]".
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(
            @RequestParam String message,
            @RequestParam(required = false) String context,
            @RequestParam(required = false) Double carbonToday,
            @RequestParam(required = false) Double aqi,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String conversationId,
            @AuthenticationPrincipal UserDetails userDetails) {
        // Truncate message for safety
        if (message != null && message.length() > 5000) {
            message = message.substring(0, 5000);
        }
        // Default conversation ID to user identity
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = userDetails != null
                    ? "user-" + userDetails.getUsername()
                    : "default";
        }
        return aiService.streamChat(message, context, carbonToday, aqi, city, conversationId);
    }

    /**
     * Clear conversation memory for a session
     * DELETE /api/ai/memory/{conversationId}
     */
    @DeleteMapping("/memory/{conversationId}")
    public ResponseEntity<ApiResponse<Void>> clearMemory(@PathVariable String conversationId) {
        aiService.clearMemory(conversationId);
        return ResponseEntity.ok(ApiResponse.success("Conversation memory cleared", null));
    }

    /**
     * Check if AI is configured and available
     * GET /api/ai/status
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Object>> getStatus() {
        return ResponseEntity.ok(ApiResponse.success("AI status", new Object() {
            public final boolean configured = aiService.isConfigured();
            public final String provider = aiService.getProvider();
        }));
    }

    /**
     * Get personalized carbon suggestions
     * GET /api/ai/carbon-suggestions?carbonToday=3.5&city=Delhi&aqi=120
     */
    @GetMapping("/carbon-suggestions")
    public ResponseEntity<ApiResponse<AiResponse>> getCarbonSuggestions(
            @RequestParam(required = false) Double carbonToday,
            @RequestParam(required = false) Double carbonBudget,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double aqi) {
        AiResponse response = aiService.getCarbonSuggestions(carbonToday, carbonBudget, city, aqi);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get health recommendations
     * GET /api/ai/health-tips?steps=5000&sleep=6.5&water=2.0&calories=1800
     */
    @GetMapping("/health-tips")
    public ResponseEntity<ApiResponse<AiResponse>> getHealthTips(
            @RequestParam(required = false) Integer steps,
            @RequestParam(required = false) Double sleep,
            @RequestParam(required = false) Double water,
            @RequestParam(required = false) Integer calories) {
        AiResponse response = aiService.getHealthRecommendations(steps, sleep, water, calories);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get daily eco tip
     * GET /api/ai/eco-tip?carbonToday=2.5&city=Delhi
     */
    @GetMapping("/eco-tip")
    public ResponseEntity<ApiResponse<AiResponse>> getEcoTip(
            @RequestParam(required = false) Double carbonToday,
            @RequestParam(required = false) String city) {
        AiResponse response = aiService.getDailyEcoTip(carbonToday, city);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
