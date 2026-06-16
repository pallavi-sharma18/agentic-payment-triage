package com.flourish.payment_backend.dtos;
// output of the Diagnostics agent
import com.flourish.payment_backend.entities.enums.PaymentStatus;

public record DiagnosisDto(String paymentId,
                           PaymentStatus status,
                           Double amount,
                           String failureReason,      // raw reason from the payment record
                           String failureCategory,    // LLM-classified, e.g. ISSUER_DECLINE, INSUFFICIENT_FUNDS, TECHNICAL, STUCK
                           Boolean stuck,             // true if it's a stuck/pending case
                           String summary,            // one-line human-readable diagnosis
                           Double confidence,
                           Boolean needsMoreContext

){
}
