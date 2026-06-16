package com.flourish.payment_backend.exceptions;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String paymentId) {
        super("No payment found with paymentId: " + paymentId);
    }
}
