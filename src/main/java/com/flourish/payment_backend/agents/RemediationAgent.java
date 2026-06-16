package com.flourish.payment_backend.agents;

import com.flourish.payment_backend.dtos.ActionPlanDto;
import com.flourish.payment_backend.dtos.DiagnosisDto;
import com.flourish.payment_backend.dtos.RuleExplanationDto;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RemediationAgent {
    private final ChatClient chatClient;

    private static final String systemPrompt = """
        You are a payment REMEDIATION planner. Given a diagnosis and the applicable
        network rules, propose an ordered, safe action plan.

        You PROPOSE ONLY. You never execute anything.
        Any action that changes data (retry, reversal, refund, capture, contacting the
        issuer to move money) MUST have requiresHumanApproval=true.
        Read-only or advisory steps may have requiresHumanApproval=false.

        Respect the rules you are given: do not propose a retry if retryAllowed is false,
        and do not exceed maxRetries. Prefer the least invasive action first.
        Number the steps starting at 1 and give a short rationale for each.
        """;

    public ActionPlanDto plan(DiagnosisDto diagnosis, RuleExplanationDto rules) {
        String context = """
                DIAGNOSIS
                - paymentId: %s
                - status: %s
                - failureReason: %s
                - failureCategory: %s
                - stuck: %s
                - summary: %s

                RULES
                - explanation: %s
                - retryAllowed: %s
                - maxRetries: %s
                - reversalAllowed: %s

                Produce the remediation plan.
                """.formatted(
                diagnosis.paymentId(), diagnosis.status(), diagnosis.failureReason(),
                diagnosis.failureCategory(), diagnosis.stuck(), diagnosis.summary(),
                rules.explanation(), rules.retryAllowed(), rules.maxRetries(), rules.reversalAllowed());

        return chatClient.prompt()
                .system(systemPrompt)
                .user(context)
                .call()
                .entity(ActionPlanDto.class);
    }

}
