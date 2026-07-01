package com.flourish.payment_backend.agents;

import com.flourish.payment_backend.dtos.DiagnosisDto;
import com.flourish.payment_backend.dtos.RuleExplanationDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RulesAgent {
    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    private static final String systemPrompt = """
        You are a Visa / payment-network RULES expert. Using the provided knowledge base
        context, explain WHY the given failure happens and what the network rules permit.

        Decide:
        - retryAllowed: may this payment be retried?
        - maxRetries: the limit if the rules state one, otherwise null.
        - reversalAllowed: may it be reversed?

        Ground your answer in the retrieved context. If the context does not cover this
        failure, say so in "explanation" and use conservative defaults
        (retryAllowed=false, reversalAllowed=false, maxRetries=null).

        Put the snippets you actually relied on into "sources".
        """;

    @Retry(name = "llm", fallbackMethod = "explainFallback")
    @CircuitBreaker(name = "llm")
    @Cacheable(
            value = "ruleExplanations",
            key = "#diagnosis.failureCategory() + ':' + #diagnosis.failureReason()"
    )
    public RuleExplanationDto explain(DiagnosisDto diagnosis) {
        QuestionAnswerAdvisor ragAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .filterExpression("file_name == 'visa.pdf'")
                        .topK(4)
                        .similarityThreshold(0.3)
                        .build())
                .build();

        String query = """
                Failure reason: %s
                Failure category: %s
                Explain the applicable network rules for this failure.
                """.formatted(diagnosis.failureReason(), diagnosis.failureCategory());

        return chatClient.prompt()
                .system(systemPrompt)
                .user(query)
                .advisors(ragAdvisor)
                .call()
                .entity(RuleExplanationDto.class);
    }

    /** Conservative defaults — mirrors the "context not covered" guidance in the system prompt. */
    private RuleExplanationDto explainFallback(DiagnosisDto diagnosis, Throwable t) {
        return new RuleExplanationDto(
                diagnosis.failureReason(),
                "Rules service unavailable (" + t.getClass().getSimpleName()
                        + "). Applying conservative defaults; manual review required.",
                false,        // retryAllowed
                null,         // maxRetries
                false,        // reversalAllowed
                List.of()     // sources
        );
    }

}
