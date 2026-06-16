package com.flourish.payment_backend.dtos;

import java.util.List;

public record ActionPlanDto(
        List<RecommendedAction> actions,
        String summary
) {
    public record RecommendedAction(
            int step,
            String action,                  // e.g. "Retry the payment once after 1 hour"
            boolean requiresHumanApproval,  // writes must stay gated
            String rationale
    ) {
    }
}
