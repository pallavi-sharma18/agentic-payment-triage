package com.flourish.payment_backend.service.impl;

import com.flourish.payment_backend.dtos.PaymentDto;
import com.flourish.payment_backend.entities.Payment;
import com.flourish.payment_backend.entities.enums.PaymentStatus;
import com.flourish.payment_backend.repositories.PaymentRepository;
import com.flourish.payment_backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final ModelMapper modelMapper;
    @Override
    public PaymentDto getPaymentStatus(String paymentId) {
        return paymentRepository.findByPaymentId(paymentId)
                .map(p -> modelMapper.map(p, PaymentDto.class))
                .orElse(null);
    }

    @Override
    public List<PaymentDto> findStuckPayments(int olderThanMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(olderThanMinutes);
        List<Payment> pendingPayments = paymentRepository.findByPaymentStatusAndPaymentTimeBefore(PaymentStatus.PENDING,cutoff);

        return pendingPayments.stream()
                .map(payment -> modelMapper.map(payment,PaymentDto.class))
                .toList();
    }

    @Override
    public List<PaymentDto> getRecentFailures(int withinMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(withinMinutes);
        return paymentRepository
                .findByPaymentStatusAndPaymentTimeAfter(PaymentStatus.FAILED, cutoff)
                .stream()
                .map(p -> modelMapper.map(p, PaymentDto.class))
                .toList();
    }
}
