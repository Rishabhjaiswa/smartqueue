package com.smartqueue.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.backend.classifier.RuleBasedPreClassifier;
import com.smartqueue.backend.dto.ChatRequest;
import com.smartqueue.backend.dto.ChatResponse;
import com.smartqueue.backend.dto.IntentDTO;
import com.smartqueue.backend.dto.TokenRequest;
import com.smartqueue.backend.dto.TokenResponse;
import com.smartqueue.backend.enums.PriorityFlag;
import com.smartqueue.backend.enums.ServiceType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.smartqueue.backend.repository.PatientRepository;
import com.smartqueue.backend.entity.Patient;

import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Value;

/**
 * AI-driven triage service.
 *
 * Request pipeline (in priority order):
 *
 *  1. RuleBasedPreClassifier — deterministic regex/keyword rules.
 *     If confidence >= 0.85, skip Ollama entirely (saves ~2-4 s per request).
 *
 *  2. Ollama LLM call — wrapped in a Resilience4j circuit breaker.
 *     If Ollama is down / slow, the circuit opens and falls back to step 3.
 *
 *  3. Rule-classifier fallback — same as step 1, used as circuit-breaker fallback.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AIService {

    private final ChatClient chatClient;
    private final QueueService queueService;
    private final ObjectMapper objectMapper;
    private final RuleBasedPreClassifier preClassifier;
    private final AuditLogService auditLogService;
    private final PatientRepository patientRepository;

    @Value("${smartqueue.ai.enabled:true}")
    private boolean aiEnabled;

    // Circuit breaker name must match resilience4j config in application.properties
    private static final String CB_NAME = "ollama";

    private static final String SYSTEM_PROMPT = """
        You are a medical queue assistant for a clinic.
        
        YOUR ONLY JOB: Read the patient's message and output a single JSON object.
        
        STRICT RULES:
        - Output ONLY raw JSON
        - No explanation
        - No markdown
        - No extra text
        
        JSON schema:
        {
          "serviceType": "",
          "priorityFlag": "",
          "language": "",
          "confidence": 0,
          "clarificationNeeded": false,
          "clarificationQuestion": "",
          "replyMessage": ""
        }
        
        Service mapping:
        - general → GENERAL
        - follow up → FOLLOW_UP
        - specialist → SPECIALIST
        - emergency → EMERGENCY
        - lab/test → LAB
        - unclear → OTHER (clarificationNeeded: true)
        
        Priority:
        - emergency/urgent → EMERGENCY
        - senior/elderly/60+ → SENIOR
        - else → NORMAL
        
        Return ONLY JSON.
        """;

    @Timed(value = "smartqueue.ai.process", description = "End-to-end AI triage latency")
    public ChatResponse processMessage(ChatRequest request) {
        int officeId = request.getOfficeId() != null ? request.getOfficeId() : 1;
        Patient patient = null;
        if (request.getPatientId() != null) {
            patient = patientRepository.findById(request.getPatientId()).orElse(null);
        }

        // ── Step 1: Rule-based pre-classifier ────────────────────────────────
        RuleBasedPreClassifier.ClassificationResult preResult =
                preClassifier.classify(request.getMessage());

        if (!aiEnabled) {
            log.info("AI is disabled by feature flag — using rule classifier directly");
            return buildFromPreClassifier(preResult, request.getMessage(), officeId, patient);
        }

        if (preResult.confidence() >= 0.85) {
            log.info("Rule classifier high-confidence hit ({}) — skipping Ollama",
                    preResult.confidence());
            return buildTokenResponse(preResult.serviceType(), preResult.priorityFlag(),
                    request.getMessage(), officeId, null, patient);
        }

        // ── Step 2: Ollama LLM (with circuit breaker + fallback) ─────────────
        try {
            // NOTE: Self-invocation of @CircuitBreaker method bypasses Spring AOP proxy.
            // A manual try-catch is enforced here to guarantee request safety.
            return callOllamaWithCircuitBreaker(request, officeId, preResult, patient);
        } catch (Exception e) {
            log.warn("LLM failure detected, switching to fallback", e);
            return ollamaFallback(request, officeId, preResult, patient, e);
        }
    }

    /**
     * Async variant — runs triage on the dedicated {@code aiTriageExecutor} thread pool
     * so Tomcat threads are never blocked waiting for Ollama.
     *
     * Usage: inject AIService and call {@code processMessageAsync(req).thenAccept(...)}
     */
    @Async("aiTriageExecutor")
    @Timed(value = "smartqueue.ai.process.async", description = "Async AI triage latency")
    public CompletableFuture<ChatResponse> processMessageAsync(ChatRequest request) {
        return CompletableFuture.completedFuture(processMessage(request));
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "ollamaFallback")
    @Timed(value = "smartqueue.ollama.call", description = "Ollama LLM call latency")
    public ChatResponse callOllamaWithCircuitBreaker(ChatRequest request, int officeId,
                                                      RuleBasedPreClassifier.ClassificationResult preResult,
                                                      Patient patient) {
        var queueState = queueService.getQueueState((long) officeId);
        String context = String.format(
                "Queue context: %d people waiting, ~%d min estimated wait. ",
                queueState.getWaitingCount(),
                queueState.getAvgWaitMinutes()
        );

        String userMessage = context + "Citizen message: " + request.getMessage();
        IntentDTO intent = callOllama(userMessage);

        if (intent == null || intent.getConfidence() < 0.6) {
            log.warn("Ollama confidence too low — falling back to rule classifier");
            return buildFromPreClassifier(preResult, request.getMessage(), officeId, patient);
        }

        if (intent.isClarificationNeeded() || "OTHER".equals(intent.getServiceType())) {
            return ChatResponse.builder()
                    .botMessage(intent.getClarificationQuestion() != null
                            ? intent.getClarificationQuestion()
                            : "Which consultation do you need? General, Follow-up, Specialist, Emergency, or Lab/Test.")
                    .tokenGenerated(false)
                    .needsClarification(true)
                    .clarificationQuestion(intent.getClarificationQuestion())
                    .build();
        }

        ServiceType serviceType;
        PriorityFlag priorityFlag;
        try {
            serviceType  = ServiceType.valueOf(intent.getServiceType().trim().toUpperCase());
            priorityFlag = PriorityFlag.valueOf(intent.getPriorityFlag().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Bad enum from Ollama: {} / {} — using rule fallback",
                    intent.getServiceType(), intent.getPriorityFlag());
            return buildFromPreClassifier(preResult, request.getMessage(), officeId, patient);
        }

        return buildTokenResponse(serviceType, priorityFlag, request.getMessage(), officeId,
                intent.getReplyMessage(), patient);
    }

    /**
     * Resilience4j fallback — invoked when the circuit is OPEN or Ollama throws.
     * Signature MUST match callOllamaWithCircuitBreaker + Throwable parameter.
     */
    @SuppressWarnings("unused")
    public ChatResponse ollamaFallback(ChatRequest request, int officeId,
                                        RuleBasedPreClassifier.ClassificationResult preResult,
                                        Patient patient,
                                        Throwable ex) {
        log.warn("Ollama circuit breaker open — using rule fallback. Reason: {}", ex.getMessage());
        // Persist fallback activation so ops can track Ollama health trends
        auditLogService.log(
                "AI_TRIAGE_FALLBACK",
                "system",
                String.format("Circuit breaker activated: %s | office=%s",
                        ex.getMessage(),
                        request.getOfficeId())
        );
        return buildFromPreClassifier(preResult, request.getMessage(), officeId, patient);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ChatResponse buildFromPreClassifier(RuleBasedPreClassifier.ClassificationResult result,
                                                 String rawMessage, int officeId, Patient patient) {
        if (result.serviceType() == ServiceType.OTHER) {
            String clarificationQ = "Which consultation do you need? General, Follow-up, Specialist, Emergency, or Lab/Test.";
            return ChatResponse.builder()
                    .botMessage(clarificationQ)
                    .tokenGenerated(false)
                    .needsClarification(true)
                    .clarificationQuestion(clarificationQ)
                    .build();
        }
        return buildTokenResponse(result.serviceType(), result.priorityFlag(), rawMessage, officeId, null, patient);
    }

    private ChatResponse buildTokenResponse(ServiceType serviceType, PriorityFlag priorityFlag,
                                             String rawMessage, int officeId, String ollamaReply, Patient patient) {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setServiceType(serviceType);
        tokenRequest.setPriorityFlag(priorityFlag);
        tokenRequest.setOfficeId(officeId);
        tokenRequest.setSeverityScore(defaultSeverity(priorityFlag));
        tokenRequest.setChiefComplaint(rawMessage);

        TokenResponse tokenResponse = queueService.generateToken(tokenRequest, patient);

        String botMsg = (ollamaReply != null && !ollamaReply.isBlank())
                ? ollamaReply + " Token: " + tokenResponse.getTokenNumber()
                  + ", Position: #" + tokenResponse.getPositionInQueue()
                  + ", Est. wait: ~" + tokenResponse.getEstimatedWaitMinutes() + " min."
                : "Your token is " + tokenResponse.getTokenNumber()
                  + ". You are #" + tokenResponse.getPositionInQueue() + " in queue."
                  + " Estimated wait: ~" + tokenResponse.getEstimatedWaitMinutes() + " min.";

        return ChatResponse.builder()
                .botMessage(botMsg)
                .tokenGenerated(true)
                .tokenData(tokenResponse)
                .needsClarification(false)
                .build();
    }

    private IntentDTO callOllama(String userMessage) {
        try {
            String raw = chatClient.call(SYSTEM_PROMPT + "\n\n" + userMessage);
            log.debug("Ollama raw response: {}", raw);

            String cleaned = raw.trim()
                    .replace("```json", "")
                    .replace("```", "");

            int start = cleaned.indexOf('{');
            int end   = cleaned.lastIndexOf('}');

            if (start == -1 || end == -1 || end <= start) {
                log.warn("No JSON object found in Ollama response: {}", cleaned);
                return null;
            }

            return objectMapper.readValue(cleaned.substring(start, end + 1), IntentDTO.class);
        } catch (Exception e) {
            log.error("Ollama call failed: {}", e.getMessage());
            throw new RuntimeException("Ollama unavailable or timed out", e);
        }
    }

    private int defaultSeverity(PriorityFlag flag) {
        return switch (flag) {
            case EMERGENCY -> 10;
            case SENIOR    -> 7;
            default        -> 5;
        };
    }

    public com.smartqueue.backend.dto.QueueStateDTO getQueueStateForTelegram(int officeId) {
        return queueService.getQueueState((long) officeId);
    }
}