package com.flourish.payment_backend.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Configuration
@EnableCaching
public class AIConfig {
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder){
        return builder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    /**
     * Custom OpenAI chat model so that Resilience4j is the SOLE retry layer.
     *
     * Spring AI 2.0 talks to OpenAI via the openai-java SDK (okhttp), which has its OWN
     * internal retry (default maxRetries=2). We set maxRetries(0) here to disable it, so the
     * raw com.openai.errors.* exception propagates straight to the @Retry/@CircuitBreaker
     * aspects on the agents — no double-retry. This also sets the real per-call timeout
     * (the okhttp client ignores spring.http.client.*).
     *
     * Replaces the auto-configured OpenAiChatModel (which is @ConditionalOnMissingBean).
     */
    @Bean
    public OpenAiChatModel openAiChatModel(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.base-url:}") String baseUrl,
            @Value("${spring.ai.openai.chat.options.model}") String model,
            @Value("${spring.ai.openai.chat.options.temperature:0.2}") Double temperature) {

        OpenAIOkHttpClient.Builder clientBuilder = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(30))     //  real per-call timeout for OpenAI
                .maxRetries(0);                     // disable SDK retry → Resilience4j owns retries
        if (StringUtils.hasText(baseUrl)) {
            clientBuilder.baseUrl(baseUrl);
        }
        OpenAIClient openAiClient = clientBuilder.build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .build();

        return OpenAiChatModel.builder()
                .openAiClient(openAiClient)
                .options(options)
                .build();
    }

    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(10)
                .build();
    }
}
