package com.flourish.payment_backend.tools;

import com.flourish.payment_backend.dtos.PaymentDto;
import com.flourish.payment_backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PaymentTools {
    private final PaymentService paymentService;
    @Tool(
            name = "get_payment_status",
            description = "get the payment status for the given payment id"
    )
    public PaymentDto getPaymentStatus(
        @ToolParam(description = "The unique payment id (e.g. paymentId is user123)")
        String paymentId
        ){
        return paymentService.getPaymentStatus(paymentId);
    }

    @Tool(
            name = "find_stuck_payments",
            description = "Find payments that are still PENDING and older than the given number of minutes (i.e. stuck and not progressing)."
    )
    public List<PaymentDto> findStuckPayments(
            @ToolParam(description = "Only return PENDING payments older than this many minutes. Use 30 if unsure.")
            int olderThanMinutes
    ){
        return paymentService.findStuckPayments(olderThanMinutes);
    }
}
