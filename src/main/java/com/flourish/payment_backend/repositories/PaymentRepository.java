package com.flourish.payment_backend.repositories;

import com.flourish.payment_backend.entities.Payment;
import com.flourish.payment_backend.entities.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment,Long> {
    Optional<Payment> findByPaymentId(String paymentId);

    List<Payment> findByPaymentStatusAndPaymentTimeBefore(PaymentStatus status, LocalDateTime cutoff);

    // get recent failures within x minutes
    List<Payment> findByPaymentStatusAndPaymentTimeAfter(PaymentStatus status, LocalDateTime cutoff);
}
