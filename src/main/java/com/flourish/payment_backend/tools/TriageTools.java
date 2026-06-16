package com.flourish.payment_backend.tools;

import com.flourish.payment_backend.dtos.TriageResultDto;
import com.flourish.payment_backend.service.TriageService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class TriageTools {
    private final TriageService triageService;

    public TriageTools(@Lazy TriageService triageService) {
        this.triageService = triageService;
    }
    @Tool(
            name = "triage_payment",
            description = "Run full failure triage for a payment: diagnose the failure, " +
                    "explain the applicable network rules, and propose a remediation plan. " +
                    "Read-only — it proposes actions but never executes them."
    )
    public TriageResultDto triagePayment(
            @ToolParam(description = "The unique payment id to triage, e.g. pay_1001")
            String paymentId
    ) {
        return triageService.triage(paymentId);
    }
}
