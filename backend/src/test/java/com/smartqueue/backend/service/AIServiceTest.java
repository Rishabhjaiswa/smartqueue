package com.smartqueue.backend.service;

import com.smartqueue.backend.classifier.RuleBasedPreClassifier;
import com.smartqueue.backend.dto.ChatRequest;
import com.smartqueue.backend.dto.ChatResponse;
import com.smartqueue.backend.dto.QueueStateDTO;
import com.smartqueue.backend.dto.TokenRequest;
import com.smartqueue.backend.dto.TokenResponse;
import com.smartqueue.backend.enums.PriorityFlag;
import com.smartqueue.backend.enums.ServiceType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link AIService}.
 *
 * External dependencies (QueueService, AuditLogService, ChatClient) are mocked.
 * The focus is on the routing logic: pre-classifier bypass, low-confidence LLM
 * fallback, and the ollamaFallback audit trail.
 *
 * The Resilience4j circuit breaker is NOT tested here (it needs integration context).
 * Those behaviours are covered by the circuit-breaker's own event listeners and
 * by the end-to-end smoke test.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AIService")
class AIServiceTest {

    // We use a real classifier (it's pure / no I/O) and mock everything else
    private RuleBasedPreClassifier realClassifier;

    @Mock private QueueService queueService;
    @Mock private AuditLogService auditLogService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();
    @Mock private org.springframework.ai.chat.ChatClient chatClient;
    @Mock private com.smartqueue.backend.repository.PatientRepository patientRepository;

    private AIService aiService;

    @BeforeEach
    void setUp() {
        realClassifier = new RuleBasedPreClassifier();
        aiService = new AIService(chatClient, queueService, objectMapper, realClassifier, auditLogService, patientRepository);
        ReflectionTestUtils.setField(aiService, "aiEnabled", true);

        // Default queue state returned for context building (lenient — only needed by Ollama path)
        lenient().when(queueService.getQueueState(any())).thenReturn(
                QueueStateDTO.builder().waitingCount(3).avgWaitMinutes(15).build()
        );
        // Default chatClient stub: low-confidence so rule-fallback triggers cleanly (no NPE)
        // Individual tests may override with when(chatClient.call(...)).thenReturn(...)
        lenient().when(chatClient.call(anyString())).thenReturn(
                "{\"serviceType\":\"OTHER\",\"priorityFlag\":\"NORMAL\",\"language\":\"en\"," +
                "\"confidence\":0.1,\"clarificationNeeded\":false,\"clarificationQuestion\":\"\",\"replyMessage\":\"\"}"
        );
    }

    private TokenResponse sampleToken(String number, int position) {
        return TokenResponse.builder()
                .tokenNumber(number)
                .positionInQueue(position)
                .estimatedWaitMinutes(position * 10)
                .build();
    }

    // ── Rule-based bypass ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Rule-based pre-classifier bypass")
    class RuleBypass {

        @Test
        @DisplayName("emergency message bypasses Ollama entirely (confidence ≥ 0.85)")
        void emergencyBypassesOllama() {
            when(queueService.generateToken(any(TokenRequest.class), any()))
                    .thenReturn(sampleToken("D1-T1", 1));

            ChatRequest req = new ChatRequest();
            req.setMessage("I have an emergency urgent case");
            req.setOfficeId(1);

            ChatResponse response = aiService.processMessage(req);

            assertThat(response.isTokenGenerated()).isTrue();
            assertThat(response.getTokenData().getTokenNumber()).isEqualTo("D1-T1");

            // Ollama must NOT have been called
            verifyNoInteractions(chatClient);
        }

        @Test
        @DisplayName("generateToken receives EMERGENCY service type for emergency message")
        void emergencyPassesCorrectServiceType() {
            when(queueService.generateToken(any(TokenRequest.class), any()))
                    .thenReturn(sampleToken("D1-T2", 1));

            ChatRequest req = new ChatRequest();
            req.setMessage("emergency chest pain");
            req.setOfficeId(1);

            aiService.processMessage(req);

            ArgumentCaptor<TokenRequest> captor = ArgumentCaptor.forClass(TokenRequest.class);
            verify(queueService).generateToken(captor.capture(), any());

            TokenRequest captured = captor.getValue();
            assertThat(captured.getServiceType()).isEqualTo(ServiceType.EMERGENCY);
            assertThat(captured.getPriorityFlag()).isEqualTo(PriorityFlag.EMERGENCY);
        }

        @Test
        @DisplayName("LAB message with senior patient sets correct service type and SENIOR priority")
        void seniorLabMessage() {
            when(queueService.generateToken(any(TokenRequest.class), any()))
                    .thenReturn(sampleToken("D2-T3", 2));

            ChatRequest req = new ChatRequest();
            req.setMessage("elderly patient needs blood test");
            req.setOfficeId(1);

            aiService.processMessage(req);

            ArgumentCaptor<TokenRequest> captor = ArgumentCaptor.forClass(TokenRequest.class);
            verify(queueService).generateToken(captor.capture(), any());

            assertThat(captor.getValue().getServiceType()).isEqualTo(ServiceType.LAB);
            assertThat(captor.getValue().getPriorityFlag()).isEqualTo(PriorityFlag.SENIOR);
        }

        @Test
        @DisplayName("When AI is disabled via feature flag, uses rule classifier directly without calling Ollama")
        void aiDisabledUsesRuleClassifier() {
            ReflectionTestUtils.setField(aiService, "aiEnabled", false);
            when(queueService.generateToken(any(TokenRequest.class), any()))
                    .thenReturn(sampleToken("D3-T1", 1));

            ChatRequest req = new ChatRequest();
            req.setMessage("general consultation");
            req.setOfficeId(1);

            ChatResponse response = aiService.processMessage(req);

            assertThat(response.isTokenGenerated()).isTrue();
            verifyNoInteractions(chatClient);
        }
    }

    // ── Clarification flow ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Clarification flow for ambiguous input")
    class ClarificationFlow {

        @Test
        @DisplayName("ambiguous input falls through to Ollama (chatClient called)")
        void ambiguousInputCallsOllama() {
            // Ollama returns a clarification JSON
            when(chatClient.call(anyString())).thenReturn("""
                    {
                      "serviceType": "OTHER",
                      "priorityFlag": "NORMAL",
                      "language": "en",
                      "confidence": 0.75,
                      "clarificationNeeded": true,
                      "clarificationQuestion": "Which type of consultation do you need?",
                      "replyMessage": ""
                    }
                    """);

            ChatRequest req = new ChatRequest();
            req.setMessage("hmm I think I need something");
            req.setOfficeId(1);

            ChatResponse response = aiService.processMessage(req);

            assertThat(response.isTokenGenerated()).isFalse();
            assertThat(response.isNeedsClarification()).isTrue();
            assertThat(response.getClarificationQuestion()).isNotBlank();

            verify(chatClient, atLeastOnce()).call(anyString());
        }
    }

    // ── Fallback / audit trail ────────────────────────────────────────────────

    @Nested
    @DisplayName("ollamaFallback — circuit breaker fallback")
    class FallbackTests {

        @Test
        @DisplayName("fallback uses pre-classifier result and logs to AuditLogService")
        void fallbackLogsAndUsesRuleClassifier() {
            when(queueService.generateToken(any(TokenRequest.class), any()))
                    .thenReturn(sampleToken("D1-T9", 3));

            ChatRequest req = new ChatRequest();
            req.setMessage("follow up appointment please");
            req.setOfficeId(1);

            RuleBasedPreClassifier.ClassificationResult ruleResult =
                    realClassifier.classify(req.getMessage());

            RuntimeException fakeEx = new RuntimeException("Connection refused to Ollama");
            ChatResponse response = aiService.ollamaFallback(req, 1, ruleResult, null, fakeEx);

            assertThat(response.isTokenGenerated()).isTrue();

            verify(auditLogService).log(
                    eq("AI_TRIAGE_FALLBACK"),
                    eq("system"),
                    contains("Connection refused to Ollama")
            );
        }

        @Test
        @DisplayName("fallback with unclear rule result returns clarification response (no token)")
        void fallbackWithUnclearInputReturnsClarification() {
            ChatRequest req = new ChatRequest();
            req.setMessage("xyzzy");
            req.setOfficeId(1);

            RuleBasedPreClassifier.ClassificationResult unclear =
                    realClassifier.classify(req.getMessage()); // → OTHER, 0.0

            RuntimeException ex = new RuntimeException("Circuit open");
            ChatResponse response = aiService.ollamaFallback(req, 1, unclear, null, ex);

            assertThat(response.isTokenGenerated()).isFalse();
            assertThat(response.isNeedsClarification()).isTrue();
            assertThat(response.getBotMessage()).isNotBlank();

            // No token should have been queued
            verify(queueService, never()).generateToken(any(), any());
        }

        @Test
        @DisplayName("processMessage safely catches exceptions and returns valid fallback response")
        void callOllamaThrowsWhenChatClientFails() {
            when(queueService.generateToken(any(TokenRequest.class), any()))
                    .thenReturn(sampleToken("D3-T9", 1));
            when(chatClient.call(anyString())).thenThrow(new RuntimeException("Timeout from Ollama"));

            ChatRequest req = new ChatRequest();
            req.setMessage("general checkup");
            req.setOfficeId(1);

            ChatResponse response = aiService.processMessage(req);

            assertThat(response.isTokenGenerated()).isTrue();
            // Verify fallback was used
            verify(auditLogService).log(eq("AI_TRIAGE_FALLBACK"), anyString(), anyString());
        }
    }

    // ── Bot message format ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Bot message format")
    class BotMessageFormat {

        @Test
        @DisplayName("bot message contains token number and position when token is generated")
        void botMessageContainsTokenInfo() {
            when(queueService.generateToken(any(TokenRequest.class), any()))
                    .thenReturn(sampleToken("D1-T5", 4));

            ChatRequest req = new ChatRequest();
            req.setMessage("I have an emergency");
            req.setOfficeId(1);

            ChatResponse response = aiService.processMessage(req);

            assertThat(response.getBotMessage())
                    .contains("D1-T5")
                    .contains("#4");
        }
    }
}
