package com.flourish.payment_backend.agents;

import com.flourish.payment_backend.dtos.ActionPlanDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class RemediationExecutor {

    private final ChatClient chatClient;
    private final SyncMcpToolCallbackProvider mcpToolCallbackProvider;

    public RemediationExecutor(ChatClient.Builder builder,
                               List<McpSyncClient> mcpClients,
                               @Value("${app.incident.owner}") String owner,
                               @Value("${app.incident.repo}") String repo) {
        this.mcpToolCallbackProvider = SyncMcpToolCallbackProvider.builder().mcpClients(mcpClients).build();

        String system = """
            You execute APPROVED payment-remediation actions for a payments ops team,
            using the available GitHub tools.

            For the actions you are given, open exactly ONE GitHub issue that serves as an
            incident record. Call the create_issue tool with:
              - owner: "%s"
              - repo:  "%s"
              - title: "Payment incident: <paymentId>"
              - body:  markdown summarizing the paymentId, the incident summary, and a
                       checklist of each action being taken (step, action, rationale).

            You NEVER move money — you only open the incident issue to record the actions.
            Do not perform fund transfers, refunds, captures, or reversals yourself.
            After creating the issue, report the issue number and URL returned by the tool.
            """.formatted(owner, repo);

        this.chatClient = builder.defaultSystem(system).build();
    }

    @CircuitBreaker(name = "mcpGithub", fallbackMethod = "executeFallback")
    public String execute(String paymentId, ActionPlanDto plan, Set<Integer> approvedSteps) {
        if (plan == null || plan.actions() == null) {
            return "No action plan to execute.";
        }
        var toExecute = plan.actions().stream()
                .filter(a -> !a.requiresHumanApproval() || approvedSteps.contains(a.step()))
                .toList();

        if (toExecute.isEmpty()) {
            return "Nothing to execute — no auto-approved or human-approved actions.";
        }

        return chatClient.prompt()
                .user("""
                      Payment %s
                      Incident summary: %s
                      Actions to record:
                      %s
                      """.formatted(paymentId, plan.summary(), toExecute))
                .tools(mcpToolCallbackProvider)
                .call()
                .content();
    }

    /** Surface the failure loudly — the operator must know the incident was NOT auto-filed. */
    private String executeFallback(String paymentId, ActionPlanDto plan,
                                   Set<Integer> approvedSteps, Throwable t) {
        return "Incident NOT auto-filed for payment " + paymentId
                + " — GitHub/MCP unavailable (" + t.getClass().getSimpleName()
                + "). Please open the incident manually.";
    }
}
