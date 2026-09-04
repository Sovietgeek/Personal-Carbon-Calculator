package com.ecoverse.config;

import com.google.genai.Client;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Spring AI Configuration — Multi-provider ChatClient with conversation memory.
 *
 * Provider priority:
 * 1. Google Gemini (if GEMINI_API_KEY is set) — free tier available
 * 2. OpenAI (if OPENAI_API_KEY is set) — paid
 * 3. Fallback (no API key) — returns "unavailable" message
 *
 * All auto-configurations are excluded in EcoVerseApplication to prevent
 * startup failures when API keys are missing.
 */
@Configuration
public class AiConfig {

    private static final String SYSTEM_PROMPT = """
            You are EcoVerse AI, a friendly and knowledgeable sustainability assistant.
            You help users reduce their carbon footprint, improve their health, and make eco-friendly choices.

            Guidelines:
            - Keep responses concise (2-4 sentences), practical, and actionable
            - Use simple language anyone can understand
            - If the user speaks Hindi/Hinglish, respond in Hinglish
            - Always include specific numbers and facts when possible
            - Never recommend harmful or illegal activities
            - For carbon questions, focus on reduction strategies with real numbers
            - For health questions, give practical wellness advice
            - For eco tips, suggest simple daily actions with measurable impact
            """;

    @Value("${spring.ai.google-genai.api-key:}")
    private String geminiApiKey;

    @Value("${spring.ai.google-genai.chat.options.model:gemini-3.5-flash-lite}")
    private String geminiModel;

    @Value("${spring.ai.google-genai.chat.options.temperature:0.7}")
    private Double geminiTemperature;

    @Value("${spring.ai.openai.api-key:}")
    private String openaiApiKey;

    @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}")
    private String openaiModel;

    @Value("${spring.ai.openai.chat.options.temperature:0.7}")
    private Double openaiTemperature;

    @Value("${spring.ai.openai.chat.options.max-tokens:800}")
    private Integer openaiMaxTokens;

    @Bean
    public ChatModel chatModel() {
        // Priority 1: Google Gemini (free tier)
        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            Client genAiClient = Client.builder()
                    .apiKey(geminiApiKey)
                    .build();

            var options = GoogleGenAiChatOptions.builder()
                    .model(geminiModel)
                    .temperature(geminiTemperature)
                    .build();

            return GoogleGenAiChatModel.builder()
                    .genAiClient(genAiClient)
                    .defaultOptions(options)
                    .build();
        }

        // Priority 2: OpenAI (paid)
        if (openaiApiKey != null && !openaiApiKey.isBlank()) {
            var api = OpenAiApi.builder()
                    .apiKey(openaiApiKey)
                    .build();

            var options = OpenAiChatOptions.builder()
                    .model(openaiModel)
                    .temperature(openaiTemperature)
                    .maxTokens(openaiMaxTokens)
                    .build();

            return OpenAiChatModel.builder()
                    .openAiApi(api)
                    .defaultOptions(options)
                    .build();
        }

        // Priority 3: Fallback — no API key configured
        return new FallbackChatModel();
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
    }

    /**
     * A no-op ChatModel that returns empty responses.
     * Used when no AI provider is configured so the app still starts.
     */
    static class FallbackChatModel implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of());
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.empty();
        }
    }
}
