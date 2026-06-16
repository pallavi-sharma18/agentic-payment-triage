package com.flourish.payment_backend.controller;

import com.flourish.payment_backend.tools.PaymentTools;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class ChatController {
    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final PaymentTools paymentTools;
    private final VectorStore vectorStore;

    private static final Set<String> VISA_DOC_KEYWORDS = Set.of(
            "visa", "visanet", "card", "cardholder", "interchange", "authorization", "authorisation",
            "settlement", "chargeback", "merchant", "acquirer", "issuer", "clearing", "dispute",
            "reversal", "refund", "bin", "pan", "emv", "transaction", "point-of-sale", "pos"
    );

    private boolean isVisaDocRelated(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return VISA_DOC_KEYWORDS.stream().anyMatch(lower::contains);
    }

    @PostMapping("/chat")
    public String chat(@RequestBody String message, @RequestParam String paymentId, @RequestParam String userId){
        String systemPrompt = String.format("""
            You are a friendly payment monitoring system.
            Immediately call the available tools to retrieve payment information whenever the user asks about a payment
             — this is a read-only lookup and does NOT require confirmation.                                                                                                                                                        \s
            Only ask for confirmation before performing actions that change data.

            IMPORTANT: The current payment ID is "%s".
            When calling tools that require a paymentId, ALWAYS use this exact value.

            IMPORTANT: The current user ID is "%s".
            When calling tools that require a userId, ALWAYS use this exact value.
            """, paymentId, userId);

        List<Advisor> advisors = new ArrayList<>();
        advisors.add(MessageChatMemoryAdvisor.builder(chatMemory).build()); // short term memory
        advisors.add(VectorStoreChatMemoryAdvisor.builder(vectorStore).defaultTopK(4).build()); // long term memory

        if (isVisaDocRelated(message)) {
            advisors.add(QuestionAnswerAdvisor.builder(vectorStore)
                    .searchRequest(SearchRequest.builder()
                            .filterExpression("file_name == 'visa.pdf'")
                            .topK(4)
                            .similarityThreshold(0.5) // skip irrelevant chunks for unrelated questions
                            .build())
                    .promptTemplate(new PromptTemplate("""
                            {query}

                            You may use the following knowledge base context if it is relevant to the question:
                            ---------------------
                            {question_answer_context}
                            ---------------------

                            If the context above is relevant, use it to answer.
                            Otherwise, ignore it and answer using the conversation history or your own general knowledge.
                            """))
                    .build());
        }

        advisors.add(new SimpleLoggerAdvisor());

        return  chatClient.prompt()
                .system(systemPrompt)
                .user(message)
                .tools(paymentTools)
                .advisors(a -> a
                        .advisors(advisors)
                        .param("chat_memory_conversation_id", userId) //later get it from the session context.
                )
                .call()
                .content();
    }

}

