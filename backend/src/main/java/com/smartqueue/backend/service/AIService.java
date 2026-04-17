package com.smartqueue.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.backend.dto.ChatRequest;
import com.smartqueue.backend.dto.ChatResponse;
import com.smartqueue.backend.dto.IntentDTO;
import com.smartqueue.backend.dto.TokenRequest;
import com.smartqueue.backend.dto.TokenResponse;
import com.smartqueue.backend.enums.PriorityFlag;
import com.smartqueue.backend.enums.ServiceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIService {

    private final ChatClient chatClient;
    private final QueueService queueService;
    private final ObjectMapper objectMapper;

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

    public ChatResponse processMessage(ChatRequest request) {
        int officeId = request.getOfficeId() != null ? request.getOfficeId() : 1;

        var queueState = queueService.getQueueState((long)officeId);
        String context = String.format(
                "Queue context: %d people waiting, ~%d min estimated wait. ",
                queueState.getWaitingCount(),
                queueState.getAvgWaitMinutes()
        );

        String userMessage = context + "Citizen message: " + request.getMessage();

        IntentDTO intent = callOllama(userMessage);

        if (intent == null || intent.getConfidence() < 0.6) {
            log.warn("Ollama parse failed, using keyword fallback");
            return fallbackKeywordParse(request.getMessage(), officeId);
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

// 🔥 ADD THIS BLOCK (CRITICAL FIX)
            String msg = request.getMessage().toLowerCase();

// Senior override
            if (msg.contains("senior") || msg.contains("elderly") || msg.contains("aged") || msg.contains("old")) {
                priorityFlag = PriorityFlag.SENIOR;
            }

// Age-based override (60+)
            if (msg.matches(".*\\b([6-9][0-9])\\b.*")) {
                priorityFlag = PriorityFlag.SENIOR;
            }

// Emergency override (highest priority)
            if (msg.contains("emergency") || msg.contains("urgent") || msg.contains("critical")) {
                priorityFlag = PriorityFlag.EMERGENCY;
            }
        } catch (IllegalArgumentException e) {
            log.warn("Bad enum from Ollama: {} / {}", intent.getServiceType(), intent.getPriorityFlag());
            return fallbackKeywordParse(request.getMessage(), officeId);
        }

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setServiceType(serviceType);
        tokenRequest.setPriorityFlag(priorityFlag);
        tokenRequest.setOfficeId(officeId);

        tokenRequest.setSeverityScore(defaultSeverity(priorityFlag));
        tokenRequest.setChiefComplaint(request.getMessage());
        TokenResponse tokenResponse = queueService.generateToken(tokenRequest,null);

        String botMsg = (intent.getReplyMessage() != null && !intent.getReplyMessage().isBlank())
                ? intent.getReplyMessage() + " Token: " + tokenResponse.getTokenNumber()
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

            String cleaned = raw.trim();

            cleaned = cleaned.replace("```json", "");
            cleaned = cleaned.replace("```", "");

            int start = cleaned.indexOf('{');
            int end   = cleaned.lastIndexOf('}');

            if (start == -1 || end == -1 || end <= start) {
                log.warn("No JSON object found in Ollama response: {}", cleaned);
                return null;
            }

            cleaned = cleaned.substring(start, end + 1);

            return objectMapper.readValue(cleaned, IntentDTO.class);

        } catch (Exception e) {
            log.error("Ollama call failed", e);
            return null;
        }
    }

    private ChatResponse fallbackKeywordParse(String message, int officeId) {
        log.info("Keyword fallback triggered for: {}", message);
        String lower = message.toLowerCase();

        ServiceType serviceType = ServiceType.OTHER;

        if (lower.contains("general")) serviceType = ServiceType.GENERAL;
        else if (lower.contains("follow")) serviceType = ServiceType.FOLLOW_UP;
        else if (lower.contains("special")) serviceType = ServiceType.SPECIALIST;
        else if (lower.contains("emergency")) serviceType = ServiceType.EMERGENCY;
        else if (lower.contains("lab") || lower.contains("test")) serviceType = ServiceType.LAB;
        if (serviceType == ServiceType.OTHER) {
            return ChatResponse.builder()
                    .botMessage("Which consultation do you need? Please choose: General, Follow-up, Specialist, Emergency, or Lab/Test.")                    .tokenGenerated(false)
                    .needsClarification(true)
                    .build();
        }

        PriorityFlag priorityFlag = PriorityFlag.NORMAL;
        if (lower.contains("senior") || lower.contains("elderly") || lower.contains("aged") || lower.contains("old")
                ||  lower.matches(".*\\b([6-9][0-9])\\b.*")) {
            priorityFlag = PriorityFlag.SENIOR;
        }
        if (lower.contains("emergency") || lower.contains("urgent"))
            priorityFlag = PriorityFlag.EMERGENCY;

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setServiceType(serviceType);
        tokenRequest.setPriorityFlag(priorityFlag);
        tokenRequest.setOfficeId(officeId);
        tokenRequest.setSeverityScore(defaultSeverity(priorityFlag));
        tokenRequest.setChiefComplaint(message);

        TokenResponse tokenResponse = queueService.generateToken(tokenRequest,null);

        return ChatResponse.builder()
                .botMessage("Your token is " + tokenResponse.getTokenNumber()
                        + ". Position: #" + tokenResponse.getPositionInQueue()
                        + ". Est. wait: ~" + tokenResponse.getEstimatedWaitMinutes() + " min.")
                .tokenGenerated(true)
                .tokenData(tokenResponse)
                .build();
    }

    private int defaultSeverity(PriorityFlag flag) {
        return switch (flag) {
            case EMERGENCY -> 10;
            case SENIOR -> 7;
            default -> 5;
        };
    }

    public com.smartqueue.backend.dto.QueueStateDTO getQueueStateForTelegram(int officeId) {
        return queueService.getQueueState((long)officeId);
    }
}