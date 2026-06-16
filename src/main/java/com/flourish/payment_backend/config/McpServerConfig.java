package com.flourish.payment_backend.config;

import com.flourish.payment_backend.tools.PaymentTools;
import com.flourish.payment_backend.tools.TriageTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider paymentMcpTools(PaymentTools paymentTools, TriageTools triageTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(paymentTools,triageTools)   // same bean from the Tool Calling step
                .build();
    }
}
