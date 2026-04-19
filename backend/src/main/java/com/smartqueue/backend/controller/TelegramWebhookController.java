package com.smartqueue.backend.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.backend.dto.QueueStateDTO;
import com.smartqueue.backend.dto.TelegramUpdate;
import com.smartqueue.backend.dto.TokenRequest;
import com.smartqueue.backend.dto.TokenResponse;
import com.smartqueue.backend.entity.Patient;
import com.smartqueue.backend.entity.Token;
import com.smartqueue.backend.enums.ServiceType;
import com.smartqueue.backend.enums.TokenStatus;
import com.smartqueue.backend.enums.VisitType;
import com.smartqueue.backend.repository.PatientRepository;
import com.smartqueue.backend.repository.TokenRepository;
import com.smartqueue.backend.service.DoctorQueueService;
import com.smartqueue.backend.service.QueueService;
import com.smartqueue.backend.service.TelegramService;
import com.smartqueue.backend.service.AIService;
import com.smartqueue.backend.dto.ChatRequest;
import com.smartqueue.backend.dto.ChatResponse;
import com.smartqueue.backend.repository.DoctorRepository;
import com.smartqueue.backend.entity.Doctor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@Slf4j
@RequiredArgsConstructor
public class TelegramWebhookController{

    private final TelegramService telegramService;
    private final QueueService queueService;
    private final DoctorQueueService doctorQueueService;
    private final PatientRepository patientRepository;
    private final TokenRepository tokenRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final AIService aiService;
    private final DoctorRepository doctorRepository;

    @Value("${telegram.bot.default-office-id:1}")
    private int defaultOfficeId;

    @Value("${telegram.session.ttl-minutes:180}")
    private long telegramSessionTtlMinutes;

    @GetMapping("/telegram/webhook")
    public ResponseEntity<String> testWebhook() {
        return ResponseEntity.ok("Webhook is live");
    }

    @PostMapping("/telegram/webhook")
    public ResponseEntity<String> handleUpdate(@RequestBody TelegramUpdate update) {
        if (update.getMessage() == null
                || update.getMessage().getText() == null
                || update.getMessage().getChat() == null
                || update.getMessage().getChat().getId() == null) {
            return ResponseEntity.ok("ok");
        }

        Long chatId = update.getMessage().getChat().getId();
        String text = update.getMessage().getText().trim();
        String firstName = update.getMessage().getChat().getFirstName();
        String safeFirstName = escapeHtml(
                firstName == null || firstName.isBlank() ? "there" : firstName
        );

        log.info("Telegram message from chat {}: {}", chatId, text);

        if (text.startsWith("/")) {
            handleCommand(chatId, text.toLowerCase(), safeFirstName);
            return ResponseEntity.ok("ok");
        }

        IntakeSession session = loadSession(chatId);
        if (session == null) {
            telegramService.sendMessage(chatId,
                    "Your session expired.\n\nPlease type <b>/start</b> again.");
            return ResponseEntity.ok("ok");
        }

        telegramService.sendTypingAction(chatId);
        handleGuidedIntake(chatId, text);
        return ResponseEntity.ok("ok");
    }

    private void handleCommand(Long chatId, String command, String firstName) {
        switch (command) {
            case "/start" -> {
                startOrResumeSession(chatId, firstName);
            }
            case "/patients" -> {
                promptPatientSelection(chatId, firstName);
            }
            case "/help" -> telegramService.sendMessage(chatId,
                    "<b>Available commands</b>\n"
                            + "/start - Begin a new token booking\n"
                            + "/patients - Select the active patient\n"
                            + "/queue - Check the current clinic queue\n"
                            + "/status - Check your active tokens\n"
                            + "/cancel - Cancel your latest active token\n"
                            + "/help - Show this help\n\n"
                            + "<b>Consultation types</b>\n"
                            + "General Consultation\n"
                            + "Follow-up Visit\n"
                            + "Specialist Consultation\n"
                            + "Emergency\n"
                            + "Lab/Test Review");
            case "/queue" -> {
                telegramService.sendMessage(chatId, buildPatientQueueMessage(chatId));
            }
            case "/status" -> telegramService.sendMessage(chatId, buildStatusMessage(chatId));
            case "/cancel" -> telegramService.sendMessage(chatId, cancelLatestToken(chatId));
            default -> telegramService.sendMessage(chatId,
                    "Unknown command. Type <b>/help</b> to see available commands.");
        }
    }

    private void handleGuidedIntake(Long chatId, String text) {
        IntakeSession session = loadSession(chatId);
        if (session == null) {
            telegramService.sendMessage(chatId, "Type <b>/start</b> to begin the guided booking flow.");
            return;
        }

        switch (session.getStep()) {
            case SELECT_PATIENT -> {
                String normalized = text.trim().toLowerCase();
                List<Patient> patients = patientRepository.findAllByTelegramChatIdOrderByCreatedAtAsc(chatId);

                if ("new".equals(normalized)) {
                    session.setActivePatientId(null);
                    session.setStep(IntakeStep.ASK_NAME);
                    saveSession(chatId, session);
                    telegramService.sendMessage(chatId, "<b>Step 1 of 4</b>\nPlease enter the patient name.");
                    return;
                }

                try {
                    int choice = Integer.parseInt(normalized);
                    if (choice < 1 || choice > patients.size()) {
                        telegramService.sendMessage(chatId, "Please select a valid patient number or reply <b>new</b>.");
                        return;
                    }
                    Patient selected = patients.get(choice - 1);
                    session.setActivePatientId(selected.getId());
                    session.setName(selected.getName());
                    session.setAge(selected.getAge());
                    if (Boolean.TRUE.equals(session.getBookingSelection())) {
                        session.setStep(IntakeStep.CHAT_WITH_AI);
                        saveSession(chatId, session);
                        telegramService.sendMessage(chatId,
                                "<b>Selected patient:</b> " + escapeHtml(selected.getName()) + "\n\n"
                                        + "Please describe your symptoms or reason for visit (e.g., 'I have a severe headache' or 'I need a regular checkup').");
                    } else {
                        session.setStep(IntakeStep.SELECT_PATIENT);
                        session.setBookingSelection(false);
                        saveSession(chatId, session);
                        telegramService.sendMessage(chatId,
                                "<b>Active patient switched.</b>\nNow using " + escapeHtml(selected.getName()) + ".\n\nUse <b>/status</b> to track tokens or <b>/start</b> to book a new token.");
                    }
                } catch (NumberFormatException ex) {
                    telegramService.sendMessage(chatId, "Reply with a patient number or <b>new</b>.");
                }
            }
            case ASK_NAME -> {
                if (text.isBlank()) {
                    telegramService.sendMessage(chatId, "Please enter a valid patient name.");
                    return;
                }
                session.setName(text.trim());
                session.setStep(IntakeStep.ASK_AGE);
                saveSession(chatId, session);
                telegramService.sendMessage(chatId, "<b>Step 2 of 4</b>\nPlease enter the patient age.");
            }
            case ASK_AGE -> {
                Integer age = parseAge(text);
                if (age == null) {
                    telegramService.sendMessage(chatId, "Please enter a valid age between 1 and 120.");
                    return;
                }
                session.setAge(age);

                Patient existing = patientRepository.findByTelegramChatIdAndNameIgnoreCaseAndAge(chatId, session.getName(), age)
                        .orElse(null);
                if (existing != null) {
                    session.setActivePatientId(existing.getId());
                    session.setStep(IntakeStep.CONFIRM_EXISTING);
                    saveSession(chatId, session);
                    telegramService.sendMessage(chatId,
                            "I found an existing patient match for <b>" + escapeHtml(existing.getName()) + "</b>, age <b>" + age + "</b>.\n"
                                    + "Reply <b>yes</b> to use this patient or <b>no</b> to register a new one.");
                    return;
                }

                session.setStep(IntakeStep.CHAT_WITH_AI);
                saveSession(chatId, session);
                
                // Create patient if it's new
                Patient newPatient = Patient.builder()
                        .name(session.getName())
                        .phone(buildTelegramPhone(chatId))
                        .age(session.getAge() != null ? session.getAge() : 30)
                        .telegramChatId(chatId)
                        .createdAt(LocalDateTime.now())
                        .build();
                newPatient = patientRepository.save(newPatient);
                session.setActivePatientId(newPatient.getId());
                saveSession(chatId, session);

                telegramService.sendMessage(chatId,
                        "Patient registered.\n\nPlease describe your symptoms or reason for visit (e.g., 'I have a severe chest pain').");
            }
            case CONFIRM_EXISTING -> {
                String normalized = text.trim().toLowerCase();
                if (List.of("yes", "y").contains(normalized)) {
                    session.setStep(IntakeStep.CHAT_WITH_AI);
                    saveSession(chatId, session);
                    telegramService.sendMessage(chatId,
                            "Please describe your symptoms or reason for visit (e.g., 'I need a follow up').");
                    return;
                }
                if (List.of("no", "n").contains(normalized)) {
                    session.setActivePatientId(null);
                    session.setStep(IntakeStep.CHAT_WITH_AI);
                    saveSession(chatId, session);
                    
                    Patient newPatient = Patient.builder()
                            .name(session.getName())
                            .phone(buildTelegramPhone(chatId))
                            .age(session.getAge() != null ? session.getAge() : 30)
                            .telegramChatId(chatId)
                            .createdAt(LocalDateTime.now())
                            .build();
                    newPatient = patientRepository.save(newPatient);
                    session.setActivePatientId(newPatient.getId());
                    saveSession(chatId, session);

                    telegramService.sendMessage(chatId,
                            "New patient registered.\nPlease describe your symptoms or reason for visit.");
                    return;
                }
                telegramService.sendMessage(chatId, "Reply <b>yes</b> to use the existing patient or <b>no</b> to register new.");
            }
            case CHAT_WITH_AI -> {
                if (session.getActivePatientId() == null) {
                    telegramService.sendMessage(chatId, "Please select a patient using /patients first.");
                    return;
                }

                ChatRequest request = new ChatRequest();
                request.setMessage(text);
                request.setOfficeId(defaultOfficeId);
                request.setPatientId(session.getActivePatientId());
                request.setSessionId(chatId.toString());

                try {
                    ChatResponse response = aiService.processMessage(request);

                    if (response.isTokenGenerated()) {
                        TokenResponse tokenResponse = response.getTokenData();
                        if ("ALREADY_EXISTS".equals(tokenResponse.getMessage())) {
                            telegramService.sendMessage(chatId, "You already have an active token.\n\nUse <b>/status</b> to track it or <b>/cancel</b> to cancel.");
                        } else {
                            telegramService.sendMessage(chatId, 
                                "<b>Token confirmed.</b>\n\n"
                                + "<b>Token:</b> " + escapeHtml(tokenResponse.getTokenNumber()) + "\n"
                                + "<b>Doctor:</b> " + escapeHtml(tokenResponse.getDoctorName()) + "\n"
                                + "<b>Position:</b> #" + tokenResponse.getPositionInQueue() + "\n"
                                + "<b>Estimated wait:</b> " + buildWaitRange(tokenResponse.getEstimatedWaitMinutes()) + "\n\n"
                                + "Please keep this chat open for live updates.");
                        }
                        clearBookingSession(chatId, session);
                    } else {
                        // Needs clarification or other response
                        telegramService.sendMessage(chatId, escapeHtml(response.getBotMessage()));
                    }
                } catch (Exception ex) {
                    log.error("AI Triage Error", ex);
                    if ("Patient already has an active token".equals(ex.getMessage())) {
                        telegramService.sendMessage(chatId, "You already have an active token.\n\nUse <b>/status</b> to track it or <b>/cancel</b> to cancel.");
                    } else {
                        telegramService.sendMessage(chatId, "Sorry, I am unable to process your request at the moment. Please approach the help desk.");
                    }
                }
            }
        }
    }

    // Token creation moved to AIService

    private String buildStatusMessage(Long chatId) {
        Patient activePatient = resolveActivePatient(chatId);
        if (activePatient == null) {
            return "No active patient selected.\n\nUse <b>/patients</b> to switch patient.";
        }

        List<TokenStatus> activeStatuses = List.of(
                TokenStatus.WAITING,
                TokenStatus.CALLED,
                TokenStatus.IN_CONSULTATION
        );

        List<Token> tokens = tokenRepository.findByPatientIdOrderByCreatedAtDesc(activePatient.getId()).stream()
                .filter(token -> activeStatuses.contains(token.getStatus()))
                .limit(5)
                .toList();

        if (tokens.isEmpty()) {
            return "You have no active tokens right now.";
        }

        StringBuilder message = new StringBuilder("<b>Your active tokens</b>\n");
        for (Token token : tokens) {
            int estimatedWait = estimateTokenWaitMinutes(token);
            
            String doctorName = "TBD";
            if (token.getDoctorId() != null) {
                doctorName = doctorRepository.findById(token.getDoctorId())
                        .map(Doctor::getName)
                        .orElse("D" + token.getDoctorId());
            }

            message.append("\n<b>")
                    .append(escapeHtml(token.getTokenNumber()))
                    .append("</b> · ")
                    .append(token.getStatus().name())
                    .append("\nDoctor: ")
                    .append(escapeHtml(doctorName))
                    .append("\nEstimated wait: ")
                    .append(buildWaitRange(estimatedWait))
                    .append("\n");
        }
        return message.toString().trim();
    }

    private String cancelLatestToken(Long chatId) {
        Patient activePatient = resolveActivePatient(chatId);
        if (activePatient == null) {
            return "Please select a patient using <b>/patients</b>.";
        }

        Token latest = tokenRepository.findTopByPatientIdAndStatusInOrderByCreatedAtDesc(
                        activePatient.getId(),
                        List.of(TokenStatus.WAITING, TokenStatus.CALLED)
                )
                .orElse(null);

        if (latest == null) {
            return "You do not have any cancellable tokens right now.";
        }

        queueService.cancelTokenForPatient(latest.getId(), activePatient.getId());
        return "<b>Token cancelled.</b>\n" + escapeHtml(latest.getTokenNumber()) + " has been cancelled successfully.";
    }

    private String buildPatientQueueMessage(Long chatId) {
        Patient activePatient = resolveActivePatient(chatId);
        if (activePatient == null) {
            return "Please select a patient using <b>/patients</b>.";
        }

        List<TokenStatus> activeStatuses = List.of(TokenStatus.WAITING, TokenStatus.CALLED, TokenStatus.IN_CONSULTATION);
        StringBuilder message = new StringBuilder("<b>Your queue info</b>\n");

        List<Token> tokens = tokenRepository.findByPatientIdOrderByCreatedAtDesc(activePatient.getId())
                .stream()
                .filter(token -> activeStatuses.contains(token.getStatus()))
                .limit(3)
                .toList();

        if (tokens.isEmpty()) {
            return "You do not have any active queue entries right now.";
        }

        for (Token token : tokens) {
            int estimatedWait = estimateTokenWaitMinutes(token);
            message.append("\n<b>")
                    .append(escapeHtml(activePatient.getName()))
                    .append("</b>\nToken: <b>")
                    .append(escapeHtml(token.getTokenNumber()))
                    .append("</b>\nStatus: ")
                    .append(token.getStatus().name())
                    .append("\nEstimated wait: ")
                    .append(buildWaitRange(estimatedWait))
                    .append("\n");
        }

        return message.toString().trim();
    }

    private ServiceType parseServiceType(String text) {
        String normalized = text.trim();

        return switch (normalized) {
            case "1" -> ServiceType.GENERAL;
            case "2" -> ServiceType.FOLLOW_UP;
            case "3" -> ServiceType.SPECIALIST;
            case "4" -> ServiceType.EMERGENCY;
            case "5" -> ServiceType.LAB;
            default -> null;
        };
    }
    private Integer parseAge(String text) {
        try {
            int age = Integer.parseInt(text.trim());
            return age >= 1 && age <= 120 ? age : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String formatServiceType(ServiceType serviceType) {
        if (serviceType == null) {
            return "Consultation";
        }
        return switch (serviceType) {
            case GENERAL -> "General Consultation";
            case FOLLOW_UP -> "Follow-up Visit";
            case SPECIALIST -> "Specialist Consultation";
            case EMERGENCY -> "Emergency";
            case LAB -> "Lab/Test Review";
            default -> "Other"; // ✅ ADD THIS

        };
    }

    private Integer defaultSeverityForService(ServiceType serviceType) {
        if (serviceType == null) {
            return 5;
        }
        return switch (serviceType) {
            case EMERGENCY -> 10;
            case SPECIALIST -> 7;
            case GENERAL -> 5;
            case FOLLOW_UP -> 3;
            case LAB -> 2;
            default -> 4;
        };
    }

    private String buildWaitRange(int minutes) {
        int start = Math.max(0, minutes);
        int end = start + 10;
        return start + "–" + end + " min";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private void startOrResumeSession(Long chatId, String firstName) {
        List<Patient> patients = patientRepository.findAllByTelegramChatIdOrderByCreatedAtAsc(chatId);
        if (!patients.isEmpty()) {
            promptPatientSelection(chatId, firstName, true);
            return;
        }

        saveSession(chatId, new IntakeSession(IntakeStep.ASK_NAME, null, null, null, null, true));
        telegramService.sendMessage(chatId,
                "<b>Hello " + firstName + ".</b>\n\n"
                        + "I am the SmartQueue clinic assistant.\n"
                        + "I will guide you through booking a consultation token.\n\n"
                        + "<b>Step 1 of 4</b>\nPlease enter the patient name.");
    }

    private Patient resolveActivePatient(Long chatId) {
        IntakeSession session = loadSession(chatId);
        if (session != null && session.getActivePatientId() != null) {
            Patient patient = patientRepository.findById(session.getActivePatientId()).orElse(null);
            if (patient != null && chatId.equals(patient.getTelegramChatId())) {
                return patient;
            }
        }

        List<Patient> patients = patientRepository.findAllByTelegramChatIdOrderByCreatedAtAsc(chatId);
        if (patients.size() == 1) {
            IntakeSession nextSession = session != null ? session : new IntakeSession(IntakeStep.SELECT_PATIENT, null, null, null, null, false);
            nextSession.setActivePatientId(patients.getFirst().getId());
            saveSession(chatId, nextSession);
            return patients.getFirst();
        }
        return null;
    }

    private void promptPatientSelection(Long chatId, String firstName) {
        promptPatientSelection(chatId, firstName, false);
    }

    private void promptPatientSelection(Long chatId, String firstName, boolean bookingSelection) {
        List<Patient> patients = patientRepository.findAllByTelegramChatIdOrderByCreatedAtAsc(chatId);
        if (patients.isEmpty()) {
            saveSession(chatId, new IntakeSession(IntakeStep.ASK_NAME, null, null, null, null, bookingSelection));
            telegramService.sendMessage(chatId,
                    "<b>Hello " + firstName + ".</b>\n\n"
                            + "No patient profile is linked to this chat yet.\n"
                            + "<b>Step 1 of 4</b>\nPlease enter the patient name.");
            return;
        }

        IntakeSession session = new IntakeSession(IntakeStep.SELECT_PATIENT, null, null, null, null, bookingSelection);
        Patient activePatient = resolveActivePatient(chatId);
        if (activePatient != null) {
            session.setActivePatientId(activePatient.getId());
        }
        saveSession(chatId, session);

        StringBuilder message = new StringBuilder("<b>Hello ")
                .append(firstName)
                .append(".</b>\n\nSelect the active patient or reply <b>new</b>:\n");
        for (int i = 0; i < patients.size(); i++) {
            Patient patient = patients.get(i);
            message.append(i + 1)
                    .append(". ")
                    .append(escapeHtml(patient.getName()))
                    .append(activePatient != null && activePatient.getId().equals(patient.getId()) ? " <b>[active]</b>" : "")
                    .append(" (age ")
                    .append(patient.getAge())
                    .append(")\n");
        }
        telegramService.sendMessage(chatId, message.toString().trim());
    }

    private String consultationOptions() {
        return "1. General Consultation\n"
                + "2. Follow-up Visit\n"
                + "3. Specialist Consultation\n"
                + "4. Emergency\n"
                + "5. Lab/Test Review";
    }

    private IntakeSession loadSession(Long chatId) {
        try {
            String raw = redisTemplate.opsForValue().get(sessionKey(chatId));
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return objectMapper.readValue(raw, IntakeSession.class);
        } catch (Exception ex) {
            log.warn("Unable to load Telegram session for {}: {}", chatId, ex.getMessage());
            return null;
        }
    }

    private void saveSession(Long chatId, IntakeSession session) {
        try {
            redisTemplate.opsForValue().set(
                    sessionKey(chatId),
                    objectMapper.writeValueAsString(session),
                    Duration.ofMinutes(telegramSessionTtlMinutes)
            );
        } catch (JsonProcessingException ex) {
            log.warn("Unable to serialize Telegram session for {}: {}", chatId, ex.getMessage());
        }
    }

    private String sessionKey(Long chatId) {
        return "telegram:session:" + chatId;
    }

    private String buildTelegramPhone(Long chatId) {
        String chatDigits = String.valueOf(Math.abs(chatId));
        String chatTail = chatDigits.length() > 8
                ? chatDigits.substring(chatDigits.length() - 8)
                : chatDigits;
        String millisTail = String.valueOf(System.currentTimeMillis() % 100000L);
        return "9" + String.format("%8s", chatTail).replace(' ', '0')
                + String.format("%5s", millisTail).replace(' ', '0')
                + "1";
    }

    private boolean isBookingSessionComplete(IntakeSession session) {
        return session != null
                && session.getName() != null && !session.getName().isBlank()
                && session.getAge() != null
                && session.getServiceType() != null;
    }

    private void clearBookingSession(Long chatId, IntakeSession session) {
        IntakeSession nextSession = new IntakeSession(
                IntakeStep.SELECT_PATIENT,
                null,
                null,
                null,
                session.getActivePatientId(),
                false
        );
        saveSession(chatId, nextSession);
    }

    private int estimateTokenWaitMinutes(Token token) {
        if (token.getDoctorId() == null) {
            return 0;
        }
        if (token.getStatus() == TokenStatus.CALLED || token.getStatus() == TokenStatus.IN_CONSULTATION) {
            return 0;
        }

        var queue = doctorQueueService.buildDoctorQueueDTO(token.getDoctorId());
        return queue.getNextTokens().stream()
                .filter(item -> token.getTokenNumber().equals(item.getTokenNumber()))
                .findFirst()
                .map(item -> item.getEstimatedWaitMinutes())
                .orElseGet(() -> {
                    int base = queue.getEstimatedWaitMinutes();
                    return base > 0 ? base : 10;
                });
    }

    private enum IntakeStep {
        SELECT_PATIENT,
        ASK_NAME,
        ASK_AGE,
        CHAT_WITH_AI,
        CONFIRM_EXISTING
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class IntakeSession {
        private IntakeStep step;
        private String name;
        private Integer age;
        private ServiceType serviceType;
        private Long activePatientId;
        private Boolean bookingSelection;
    }
}
