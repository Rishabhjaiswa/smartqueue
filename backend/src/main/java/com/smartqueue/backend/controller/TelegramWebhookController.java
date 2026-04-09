package com.smartqueue.backend.controller;

import com.smartqueue.backend.dto.ChatRequest;
import com.smartqueue.backend.dto.ChatResponse;
import com.smartqueue.backend.dto.TelegramUpdate;
import com.smartqueue.backend.service.AIService;
import com.smartqueue.backend.service.TelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@RequiredArgsConstructor
public class TelegramWebhookController {

    private final AIService aiService;
    private final TelegramService telegramService;

    @Value("${telegram.bot.default-office-id:1}")
    private int defaultOfficeId;

    @GetMapping("/telegram/webhook")
    public ResponseEntity<String> testWebhook() {
        return ResponseEntity.ok("Webhook is live");
    }

    @PostMapping("/telegram/webhook")
    public ResponseEntity<String> handleUpdate(
            @RequestBody TelegramUpdate update) {

        if (update.getMessage() == null
                || update.getMessage().getText() == null) {
            return ResponseEntity.ok("ok");
        }

        Long chatId   = update.getMessage().getChat().getId();
        String text   = update.getMessage().getText().trim();
        String firstName = update.getMessage().getChat().getFirstName();

        log.info("Telegram message from chat {}: {}", chatId, text);

        if (text.startsWith("/")) {
            handleCommand(chatId, text, firstName);
            return ResponseEntity.ok("ok");
        }

        telegramService.sendTypingAction(chatId);

        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setMessage(text);
        chatRequest.setOfficeId(defaultOfficeId);
        chatRequest.setSessionId("telegram-" + chatId);

        try {
            ChatResponse response = aiService.processMessage(chatRequest);
            String reply = buildReply(response, firstName);
            telegramService.sendMessage(chatId, reply);
        } catch (Exception e) {
            log.error("Error processing Telegram message: {}", e.getMessage());
            telegramService.sendMessage(chatId,
                    "Sorry, something went wrong. Please try again or visit the help desk.");
        }

        return ResponseEntity.ok("ok");
    }

    private void handleCommand(Long chatId, String command, String firstName) {
        switch (command.toLowerCase()) {
            case "/start" -> telegramService.sendMessage(chatId,
                    "Hello " + firstName + "! I'm the SmartQueue assistant.\n\n"
                            + "Tell me what service you need and I'll give you a queue token instantly.\n\n"
                            + "Example: I need to update my Aadhaar card\n\n"
                            + "Commands:\n"
                            + "/queue — check current queue status\n"
                            + "/help — show this message");

            case "/help" -> telegramService.sendMessage(chatId,
                    "Available services:\n"
                            + "• Aadhaar update\n"
                            + "• PAN card\n"
                            + "• Passport\n"
                            + "• Driving license\n"
                            + "• Income certificate\n\n"
                            + "Just describe your need in plain language!");

            case "/queue" -> {
                var state = aiService.getQueueStateForTelegram(defaultOfficeId);
                telegramService.sendMessage(chatId,
                        "Current queue status:\n"
                                + "Now serving: " + (state.getCurrentToken().isBlank() ? "—" : state.getCurrentToken()) + "\n"
                                + "Waiting: " + state.getWaitingCount() + " people\n"
                                + "Est. wait: ~" + state.getAvgWaitMinutes() + " min");
            }

            default -> telegramService.sendMessage(chatId,
                    "Unknown command. Type /help to see what I can do.");
        }
    }

    private String buildReply(ChatResponse response, String firstName) {
        if (response.isNeedsClarification()) {
            return response.getBotMessage();
        }

        if (response.isTokenGenerated() && response.getTokenData() != null) {
            var token = response.getTokenData();
            return response.getBotMessage() + "\n\n"
                    + "Token: " + token.getTokenNumber() + "\n"
                    + "Position: #" + token.getPositionInQueue() + "\n"
                    + "Est. wait: ~" + token.getEstimatedWaitMinutes() + " min\n\n"
                    + "Please listen for your token number to be called.";
        }

        return response.getBotMessage();
    }
}