package com.ecoverse.service;

import com.ecoverse.dto.ai.AiRequest;
import com.ecoverse.dto.ai.AiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * AI Service — Spring AI Multi-provider Integration (Gemini + OpenAI)
 *
 * Uses Spring AI's ChatClient for:
 * - Synchronous chat (backward compatible with existing endpoints)
 * - Streaming chat (SSE for real-time chatbot experience)
 * - Conversation memory (per-session, last 20 messages)
 *
 * Provider priority: Gemini (free) > OpenAI (paid) > Fallback
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    @Value("${spring.ai.google-genai.api-key:}")
    private String geminiApiKey;

    @Value("${spring.ai.openai.api-key:}")
    private String openaiApiKey;

    public AiService(ChatClient chatClient, ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
    }

    /**
     * Check if any AI provider is configured with a valid API key.
     */
    public boolean isConfigured() {
        return (geminiApiKey != null && !geminiApiKey.isBlank())
                || (openaiApiKey != null && !openaiApiKey.isBlank());
    }

    /**
     * Get the active provider name.
     */
    public String getProvider() {
        if (geminiApiKey != null && !geminiApiKey.isBlank()) return "gemini";
        if (openaiApiKey != null && !openaiApiKey.isBlank()) return "openai";
        return "none";
    }

    // ================================================================
    // SYNCHRONOUS CHAT (backward compatible)
    // ================================================================

    /**
     * Main chat/suggestion method — synchronous, returns full response.
     */
    public AiResponse chat(AiRequest request) {
        if (!isConfigured()) {
            return getFallbackResponse(request);
        }

        try {
            String conversationId = resolveConversationId(request);
            String userPrompt = buildUserPrompt(request);
            String contextSystem = buildContextSystem(request);

            // Add user message to memory
            chatMemory.add(conversationId, List.of(new UserMessage(userPrompt)));

            // Build prompt with conversation history
            List<Message> history = chatMemory.get(conversationId);
            StringBuilder fullPrompt = new StringBuilder();
            if (!contextSystem.isEmpty()) {
                fullPrompt.append(contextSystem).append("\n\n");
            }
            for (Message msg : history) {
                if (msg instanceof UserMessage) {
                    fullPrompt.append("User: ").append(msg.getText()).append("\n");
                } else {
                    fullPrompt.append("Assistant: ").append(msg.getText()).append("\n");
                }
            }

            String response = chatClient.prompt()
                    .user(fullPrompt.toString())
                    .call()
                    .content();

            // Add assistant response to memory
            if (response != null && !response.isBlank()) {
                chatMemory.add(conversationId,
                        List.of(new AssistantMessage(response)));
            }

            return AiResponse.builder()
                    .message(response != null ? response : "I couldn't generate a response. Please try again.")
                    .type("chat")
                    .source(getProvider())
                    .quickActions(extractQuickActions(request.getContext()))
                    .build();

        } catch (Exception e) {
            log.warn("OpenAI chat call failed, using fallback: {}", e.getMessage());
            return getFallbackResponse(request);
        }
    }

    // ================================================================
    // STREAMING CHAT (SSE for real-time chatbot)
    // ================================================================

    /**
     * Stream chat response token-by-token.
     * Returns a Flux<String> where each element is a token/chunk.
     * The Flux completes with "[DONE]" to signal end-of-stream.
     */
    public Flux<String> streamChat(String message, String context, Double carbonToday,
                                   Double aqi, String city, String conversationId) {
        if (!isConfigured()) {
            return Flux.just("AI assistant is temporarily unavailable. Please set the OPENAI_API_KEY.");
        }

        try {
            String userPrompt = buildUserPromptFromParams(message, carbonToday, aqi, city);
            String contextSystem = buildContextSystemFromParams(context);

            // Add user message to memory
            chatMemory.add(conversationId, List.of(new UserMessage(userPrompt)));

            // Build prompt with conversation history
            List<Message> history = chatMemory.get(conversationId);
            StringBuilder fullPrompt = new StringBuilder();
            if (!contextSystem.isEmpty()) {
                fullPrompt.append(contextSystem).append("\n\n");
            }
            for (Message msg : history) {
                if (msg instanceof UserMessage) {
                    fullPrompt.append("User: ").append(msg.getText()).append("\n");
                } else {
                    fullPrompt.append("Assistant: ").append(msg.getText()).append("\n");
                }
            }

            StringBuilder responseBuilder = new StringBuilder();

            return chatClient.prompt()
                    .user(fullPrompt.toString())
                    .stream()
                    .content()
                    .doOnNext(chunk -> responseBuilder.append(chunk))
                    .doOnComplete(() -> {
                        // Save full assistant response to memory after streaming completes
                        String fullResponse = responseBuilder.toString();
                        if (!fullResponse.isBlank()) {
                            chatMemory.add(conversationId,
                                    List.of(new AssistantMessage(fullResponse)));
                        }
                    })
                    .concatWith(Flux.just("[DONE]"));

        } catch (Exception e) {
            log.warn("OpenAI streaming failed: {}", e.getMessage());
            return Flux.just("AI streaming error: " + e.getMessage());
        }
    }

    // ================================================================
    // CONTEXT-SPECIFIC SHORTCUTS
    // ================================================================

    public AiResponse getCarbonSuggestions(Double carbonToday, Double carbonBudget, String city, Double aqi) {
        AiRequest request = AiRequest.builder()
                .message("Give me 3 personalized suggestions to reduce my carbon footprint today")
                .context("carbon")
                .carbonToday(carbonToday)
                .aqi(aqi)
                .city(city)
                .build();
        return chat(request);
    }

    public AiResponse getHealthRecommendations(Integer steps, Double sleep, Double water, Integer calories) {
        String msg = String.format(
                "My health data today: Steps: %d, Sleep: %.1fh, Water: %.1fL, Calories: %d. Give me personalized health advice.",
                steps, sleep != null ? sleep : 0, water != null ? water : 0, calories != null ? calories : 0
        );
        AiRequest request = AiRequest.builder()
                .message(msg)
                .context("health")
                .build();
        return chat(request);
    }

    public AiResponse getDailyEcoTip(Double carbonToday, String city) {
        AiRequest request = AiRequest.builder()
                .message("Give me one practical eco tip for today. Keep it short and actionable.")
                .context("eco-tips")
                .carbonToday(carbonToday)
                .city(city)
                .build();
        return chat(request);
    }

    /**
     * Clear conversation memory for a given session.
     */
    public void clearMemory(String conversationId) {
        chatMemory.clear(conversationId);
    }

    // ================================================================
    // PRIVATE HELPERS
    // ================================================================

    private String resolveConversationId(AiRequest request) {
        // Use conversationId from request if provided, else generate a default
        return request.getConversationId() != null ? request.getConversationId() : "default";
    }

    private String buildUserPrompt(AiRequest request) {
        return buildUserPromptFromParams(request.getMessage(), request.getCarbonToday(), request.getAqi(), request.getCity());
    }

    private String buildUserPromptFromParams(String message, Double carbonToday, Double aqi, String city) {
        StringBuilder sb = new StringBuilder();
        sb.append(message);

        if (carbonToday != null) {
            sb.append(String.format("\n\nContext: My carbon footprint today is %.2f kg CO2.", carbonToday));
        }
        if (aqi != null) {
            sb.append(String.format("\nAir Quality Index (AQI) in my area: %.0f", aqi));
        }
        if (city != null && !city.isBlank()) {
            sb.append(String.format("\nMy city: %s", city));
        }

        return sb.toString();
    }

    private String buildContextSystem(AiRequest request) {
        return buildContextSystemFromParams(request.getContext());
    }

    private String buildContextSystemFromParams(String context) {
        if (context == null) return "";

        return switch (context) {
            case "carbon" -> "Focus on carbon reduction strategies with specific numbers and actionable steps.";
            case "health" -> "Focus on health and wellness advice. Be encouraging and practical.";
            case "weather" -> "Focus on weather-related safety and outdoor activity recommendations.";
            case "eco-tips" -> "Focus on practical daily eco tips that make a measurable difference.";
            default -> "";
        };
    }

    private List<String> extractQuickActions(String context) {
        if ("carbon".equals(context)) {
            return List.of("Log a carbon entry", "View carbon summary", "Check suggestions");
        } else if ("health".equals(context)) {
            return List.of("Log health data", "Calculate BMI", "View health score");
        }
        return List.of("Ask another question", "View dashboard", "Get eco tip");
    }

    private AiResponse getFallbackResponse(AiRequest request) {
        return AiResponse.builder()
                .message("AI assistant is temporarily unavailable. The AI service provider may be experiencing issues. Please try again in a moment.")
                .type("chat")
                .source("fallback")
                .quickActions(List.of("Try again", "View dashboard", "Get eco tip"))
                .build();
    }
}
