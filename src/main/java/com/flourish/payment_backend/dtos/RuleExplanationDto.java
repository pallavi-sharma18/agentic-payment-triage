package com.flourish.payment_backend.dtos;
// output of the Rules/RAG agent

import java.util.List;

public record RuleExplanationDto(
        String failureReason,      // the reason this explanation is about
        String explanation,        // plain-English why, grounded in visa.pdf
        boolean retryAllowed,
        Integer maxRetries,        // nullable if not specified by the rules
        boolean reversalAllowed,
        List<String> sources       // snippets/citations pulled from the knowledge base
) {}
