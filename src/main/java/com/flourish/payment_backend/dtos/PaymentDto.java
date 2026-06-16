package com.flourish.payment_backend.dtos;

import com.flourish.payment_backend.entities.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentDto {

    private Long id;
    private String paymentId;
    private Double amount;
    private PaymentStatus paymentStatus;
    private String failureReason;
    private LocalDateTime paymentTime;
}
