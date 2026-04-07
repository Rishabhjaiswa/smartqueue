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
        You are a JSON extraction bot for a government queue system.
        
        YOUR ONLY JOB: Read the citizen's message and output a single JSON object.
        DO NOT output any text before the JSON.
        DO NOT output any text after the JSON.
        DO NOT use markdown code fences.
        DO NOT explain your answer.
        ONLY output the raw JSON object, nothing else.
        
        JSON schema (copy this structure exactly):
        {
          "serviceType": "",
          "priorityFlag": "",
          "language": "",
          "confidence": ,
          "clarificationNeeded": ,
          "clarificationQuestion": "",
          "replyMessage": ""
        }
        
        Rules for serviceType:
        - aadhaar/aadhar → AADHAAR_UPDATE
        - pan → PAN_CARD
        - passport → PASSPORT
        - driving/license/licence/DL → DRIVING_LICENSE
        - income/certificate → INCOME_CERTIFICATE
        - unclear → OTHER with clarificationNeeded: true
        
        Rules for priorityFlag:
        - senior/elderly/aged/60+/old age → SENIOR
        - emergency/urgent/critical/accident → EMERGENCY
        - anything else → NORMAL
        
        Rules for language:
        - Hindi words or Devanagari script → hi
        - Marathi words → mr
        - English → en
        
        Set clarificationNeeded to true ONLY when serviceType would be OTHER.
        If clarificationNeeded is true, write a helpful clarificationQuestion.
        
        REMEMBER: Output ONLY the JSON. No other text. Start your response with {
        """;

    public ChatResponse processMessage(ChatRequest request) {
        int officeId = request.getOfficeId() != null ? request.getOfficeId() : 1;

        var queueState = queueService.getQueueState(officeId);
        String context = String.format(
                "Queue context: %d people waiting, ~%d min estimated wait. ",
                queueState.getWaitingCount(),
                queueState.getAvgWaitMinutes()
        );

        String userMessage = context + "Citizen message: " + request.getMessage();

        IntentDTO intent = callOllama(userMessage);

        if (intent == null) {
            log.warn("Ollama parse failed, using keyword fallback");
            return fallbackKeywordParse(request.getMessage(), officeId);
        }

        if (intent.isClarificationNeeded() || "OTHER".equals(intent.getServiceType())) {
            return ChatResponse.builder()
                    .botMessage(intent.getClarificationQuestion() != null
                            ? intent.getClarificationQuestion()
                            : "Which service do you need? Aadhaar, PAN, Passport, Driving License, or Income Certificate?")
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
            if (msg.matches(".*\\b(6[0-9]|[7-9][0-9])\\b.*")) {
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

        TokenResponse tokenResponse = queueService.generateToken(tokenRequest);

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

            cleaned = cleaned.replaceAll("(?s)```json\\s*", "");
            cleaned = cleaned.replaceAll("```", "");
            cleaned = cleaned.trim();

            int start = cleaned.indexOf('{');
            int end   = cleaned.lastIndexOf('}');
            if (start == -1 || end == -1 || end <= start) {
                log.warn("No JSON object found in Ollama response: {}", cleaned);
                return null;
            }
            cleaned = cleaned.substring(start, end + 1);

            return objectMapper.readValue(cleaned, IntentDTO.class);

        } catch (Exception e) {
            log.error("Ollama call failed: {}", e.getMessage());
            return null;
        }
    }

    private ChatResponse fallbackKeywordParse(String message, int officeId) {
        log.info("Keyword fallback triggered for: {}", message);
        String lower = message.toLowerCase();

        ServiceType serviceType = ServiceType.OTHER;
        if (lower.contains("aadhaar") || lower.contains("aadhar"))      serviceType = ServiceType.AADHAAR_UPDATE;
        else if (lower.contains("pan"))                                  serviceType = ServiceType.PAN_CARD;
        else if (lower.contains("passport"))                             serviceType = ServiceType.PASSPORT;
        else if (lower.contains("driving") || lower.contains("license")
                || lower.contains("licence") || lower.contains("dl"))     serviceType = ServiceType.DRIVING_LICENSE;
        else if (lower.contains("income") || lower.contains("certificate")) serviceType = ServiceType.INCOME_CERTIFICATE;

        if (serviceType == ServiceType.OTHER) {
            return ChatResponse.builder()
                    .botMessage("Which service do you need? Please say: Aadhaar update, PAN card, passport, driving license, or income certificate.")
                    .tokenGenerated(false)
                    .needsClarification(true)
                    .build();
        }

        PriorityFlag priorityFlag = PriorityFlag.NORMAL;
        if (lower.contains("senior") || lower.contains("elderly") || lower.contains("aged") || lower.contains("old")
                || lower.matches(".*\\b(6[0-9]|[7-9][0-9])\\b.*")) {
            priorityFlag = PriorityFlag.SENIOR;
        }
        if (lower.contains("emergency") || lower.contains("urgent"))
            priorityFlag = PriorityFlag.EMERGENCY;

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setServiceType(serviceType);
        tokenRequest.setPriorityFlag(priorityFlag);
        tokenRequest.setOfficeId(officeId);

        TokenResponse tokenResponse = queueService.generateToken(tokenRequest);

        return ChatResponse.builder()
                .botMessage("Your token is " + tokenResponse.getTokenNumber()
                        + ". Position: #" + tokenResponse.getPositionInQueue()
                        + ". Est. wait: ~" + tokenResponse.getEstimatedWaitMinutes() + " min.")
                .tokenGenerated(true)
                .tokenData(tokenResponse)
                .build();
    }
}