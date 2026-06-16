package com.flourish.payment_backend.dtos;

public record TriageResultDto(
        DiagnosisDto diagnosisResult,
        RuleExplanationDto ruleExplanationResult,
        ActionPlanDto actionPlanResult
) {}
