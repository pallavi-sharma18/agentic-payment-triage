package com.flourish.payment_backend.service;

import com.flourish.payment_backend.dtos.PaymentDto;

import java.util.List;

public interface PaymentService {
    PaymentDto getPaymentStatus(String paymentId);
    List<PaymentDto> findStuckPayments(int olderThanMinutes);
    List<PaymentDto> getRecentFailures(int withinMinutes);
}
