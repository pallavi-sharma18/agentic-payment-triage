package com.flourish.payment_backend;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import com.openai.errors.OpenAIIoException;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the resilience wiring for LLM/MCP calls WITHOUT booting the full app
 * (no Postgres / OpenAI key / MCP subprocess). Loads only the Resilience4j
 * auto-configuration so it binds the resilience4j.* config from application.yaml.
 */
@SpringBootTest(classes = ResilienceConfigTest.Config.class)
class ResilienceConfigTest {

    @Configuration
    @ImportAutoConfiguration({RetryAutoConfiguration.class, CircuitBreakerAutoConfiguration.class})
    static class Config {
    }

    @Autowired
    RetryRegistry retryRegistry;

    @Autowired
    CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    void llmRetryConfigIsBoundFromYaml() {
        Retry retry = retryRegistry.retry("llm");
        assertThat(retry.getRetryConfig().getMaxAttempts()).isEqualTo(3);
    }

    @Test
    void transientOpenAiExceptionIsRetriedThreeTimes() {
        Retry retry = retryRegistry.retry("llm");
        AtomicInteger attempts = new AtomicInteger();

        Supplier<String> failing = Retry.decorateSupplier(retry, () -> {
            attempts.incrementAndGet();
            throw new OpenAIIoException("simulated network error / timeout");
        });

        assertThatThrownBy(failing::get).isInstanceOf(OpenAIIoException.class);
        assertThat(attempts.get()).isEqualTo(3);   // initial + 2 retries
    }

    @Test
    void nonWhitelistedExceptionIsNotRetried() {
        // retry-exceptions is a whitelist → anything not listed (e.g. a 4xx, or any other
        // exception) is NOT retried. Proves we don't burn attempts on non-transient failures.
        Retry retry = retryRegistry.retry("llm");
        AtomicInteger attempts = new AtomicInteger();

        Supplier<String> failing = Retry.decorateSupplier(retry, () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("simulated non-transient failure");
        });

        assertThatThrownBy(failing::get).isInstanceOf(IllegalStateException.class);
        assertThat(attempts.get()).isEqualTo(1);   // not whitelisted → no retry
    }

    @Test
    void llmCircuitBreakerIsBoundFromYaml() {
        CircuitBreaker llm = circuitBreakerRegistry.circuitBreaker("llm");
        assertThat(llm).isNotNull();
        assertThat(llm.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(50f);
        assertThat(llm.getCircuitBreakerConfig().getSlidingWindowSize()).isEqualTo(20);
    }

    @Test
    void mcpGithubCircuitBreakerIsSeparateAndBound() {
        CircuitBreaker mcp = circuitBreakerRegistry.circuitBreaker("mcpGithub");
        assertThat(mcp).isNotNull();
        assertThat(mcp.getCircuitBreakerConfig().getSlidingWindowSize()).isEqualTo(10);
        // distinct instance from the LLM breaker → independent failure domains
        assertThat(mcp).isNotSameAs(circuitBreakerRegistry.circuitBreaker("llm"));
    }
}
