package com.flourish.payment_backend.agents;

import com.flourish.payment_backend.dtos.DiagnosisDto;
import com.flourish.payment_backend.dtos.PaymentDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DiagnosticsAgent {

    private final ChatClient chatClient;   // no PaymentTools — data is passed in

    private static final String systemPrompt = """
        You are a payment DIAGNOSTICS specialist. Classify the failure of the payment
        you are given, using ONLY the data provided. You do not recommend fixes.

        Classify "failureCategory" as exactly one of:
        ISSUER_DECLINE, INSUFFICIENT_FUNDS, TECHNICAL, STUCK, UNKNOWN.

        If status is PENDING and minutesSincePayment > 30, set stuck=true and
        failureCategory=STUCK.

        Some reason codes are GENERIC/AMBIGUOUS — e.g. do_not_honor, unknown, or empty.
        A single such payment could be EITHER an individual issuer decline OR a symptom
        of a systemic outage. You cannot tell which from one payment alone.

        RULES for "confidence" (0.0–1.0) and "needsMoreContext":
        - If failureReason is generic/ambiguous AND the message says
          "No recent-failure context provided": you MUST set needsMoreContext=true,
          confidence <= 0.4, and failureCategory=UNKNOWN. Do NOT guess ISSUER_DECLINE yet.
        - - If recent-failure context IS provided, you MUST set needsMoreContext=false — this is
                your FINAL classification, do not ask for more. Then decide:
                if 3 or more payments failed recently (or 2+ share this payment's reason),
                classify as TECHNICAL (systemic outage). Otherwise classify as ISSUER_DECLINE.
        - For SPECIFIC reasons (insufficient_funds, expired_card): classify directly with
          needsMoreContext=false and high confidence. No extra context needed.

        Keep "summary" to one factual sentence. Do not invent data that is not provided.
        """;

    /** Convenience: first pass with no extra evidence. */
    // fallbackMethod is on @Retry (the OUTER aspect) so retries run first; the fallback fires
    // only after retries are exhausted, or when the breaker is open.
    @Retry(name = "llm", fallbackMethod = "diagnoseFallback")
    @CircuitBreaker(name = "llm")
    public DiagnosisDto diagnose(PaymentDto payment) {
        return diagnose(payment, List.of());
    }

    @Retry(name = "llm", fallbackMethod = "diagnoseFallback")
    @CircuitBreaker(name = "llm")
    public DiagnosisDto diagnose(PaymentDto payment, List<PaymentDto> recentFailures) {
        long minutesSincePayment = payment.getPaymentTime() == null ? 0
                : Duration.between(payment.getPaymentTime(), LocalDateTime.now()).toMinutes();

        long sameReason = recentFailures.stream()
                .filter(p -> payment.getFailureReason() != null
                        && payment.getFailureReason().equals(p.getFailureReason()))
                .count();

        String evidence = recentFailures.isEmpty()
                ? "No recent-failure context provided."
                : ("Recent-failure context: %d payments FAILED in the last 60 minutes, "
                + "%d of them with the same reason '%s' as this payment.")
                .formatted(recentFailures.size(), sameReason, payment.getFailureReason());

        String userPrompt = """
                Diagnose this payment:
                - paymentId: %s
                - status: %s
                - amount: %s
                - failureReason: %s
                - minutesSincePayment: %d

                %s
                """.formatted(
                payment.getPaymentId(), payment.getPaymentStatus(), payment.getAmount(),
                payment.getFailureReason(), minutesSincePayment, evidence);

        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .entity(DiagnosisDto.class);
    }

    private DiagnosisDto diagnoseFallback(PaymentDto payment, Throwable t) {
        return degraded(payment, t);
    }

    private DiagnosisDto diagnoseFallback(PaymentDto payment, List<PaymentDto> recentFailures, Throwable t) {
        return degraded(payment, t);
    }

    /** Graceful degradation: a valid DiagnosisDto the pipeline can keep running on. */
    private DiagnosisDto degraded(PaymentDto payment, Throwable t) {
        return new DiagnosisDto(
                payment.getPaymentId(),
                payment.getPaymentStatus(),
                payment.getAmount(),
                payment.getFailureReason(),
                "UNKNOWN",          // failureCategory
                false,              // stuck
                "Diagnostics unavailable: " + t.getClass().getSimpleName(),
                0.0,                // confidence
                true                // needsMoreContext
        );
    }
}