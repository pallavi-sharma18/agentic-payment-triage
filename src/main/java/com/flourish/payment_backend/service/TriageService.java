package com.flourish.payment_backend.service;

import com.flourish.payment_backend.agents.DiagnosticsAgent;
import com.flourish.payment_backend.agents.RemediationAgent;
import com.flourish.payment_backend.agents.RemediationExecutor;
import com.flourish.payment_backend.agents.RulesAgent;
import com.flourish.payment_backend.dtos.*;
import com.flourish.payment_backend.entities.enums.PaymentStatus;
import com.flourish.payment_backend.exceptions.PaymentNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TriageService {
    private final DiagnosticsAgent diagnosticsAgent;
    private final RulesAgent rulesAgent;
    private final RemediationAgent remediationAgent;
    private final PaymentService paymentService;
    private final RemediationExecutor remediationExecutor;

    private static final int MAX_DIAGNOSIS_ITERATIONS = 2;   // safety cap


    public TriageResultDto triage(String paymentId) {

        PaymentDto payment = paymentService.getPaymentStatus(paymentId);
        if (payment == null || payment.getPaymentId() == null) {
            throw new PaymentNotFoundException(paymentId);
        }
        // 1. Gather facts
        DiagnosisDto diagnosis = diagnosticsAgent.diagnose(payment);

        // AGENTIC LOOP: if the agent isn't confident, gather evidence and re-diagnose
        int iterations = 0;
        while (diagnosis.needsMoreContext() && iterations < MAX_DIAGNOSIS_ITERATIONS) {
            List<PaymentDto> recentFailures = paymentService.getRecentFailures(60);
            diagnosis = diagnosticsAgent.diagnose(payment, recentFailures);
            iterations++;
        }

        // Nothing wrong → skip rules + remediation
        if (diagnosis.status() == PaymentStatus.CONFIRMED && !diagnosis.stuck()) {
            return new TriageResultDto(diagnosis, null, null);
        }

        // 2. Look up the network rules for this failure
        RuleExplanationDto rules = rulesAgent.explain(diagnosis);

        // 3. Propose a safe, ordered remediation plan
        ActionPlanDto plan = remediationAgent.plan(diagnosis, rules);

        return new TriageResultDto(diagnosis, rules, plan);
    }

    // the plan comes from the caller, not from a DB, the action plan is currently not persisted.
    public String executeRemediation(String paymentId, ActionPlanDto approvedPlan, Set<Integer> approvedSteps) {
        PaymentDto payment = paymentService.getPaymentStatus(paymentId);
        if (payment == null || payment.getPaymentId() == null) {
            throw new PaymentNotFoundException(paymentId);
        }
        return remediationExecutor.execute(paymentId, approvedPlan, approvedSteps);
    }
}

