package com.smartqueue.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class TelegramService {

    @Value("${telegram.bot.token}")
    private String botToken;

    private final RestTemplate restTemplate;

    public TelegramService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);  // 5 s connect timeout
        factory.setReadTimeout(5_000);     // 5 s read timeout
        this.restTemplate = new RestTemplate(factory);
    }

    private static final String TELEGRAM_API =
            "https://api.telegram.org/bot";

    public void sendMessage(Long chatId, String text) {
        String url = TELEGRAM_API + botToken + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text == null || text.isBlank() ? "Please try again." : text);
        body.put("parse_mode", "HTML");
        body.put("disable_web_page_preview", true);

        try {
            restTemplate.postForObject(url, body, String.class);
            log.info("Telegram reply sent to chat {}", chatId);
        } catch (Exception e) {
            log.error("Failed to send Telegram message to {}: {}", chatId, e.getMessage());
        }
    }

    public void sendTypingAction(Long chatId) {
        String url = TELEGRAM_API + botToken + "/sendChatAction";
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("action", "typing");
        try {
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            log.debug("Could not send typing action: {}", e.getMessage());
        }
    }

    public void registerWebhook(String webhookBaseUrl) {
        String url = TELEGRAM_API + botToken + "/setWebhook";
        String webhookUrl = webhookBaseUrl.replaceAll("/+$", "") + "/telegram/webhook";

        Map<String, String> body = new HashMap<>();
        body.put("url", webhookUrl);

        try {
            String response = restTemplate.postForObject(url, body, String.class);
            log.info("Webhook registered: {}", response);
        } catch (Exception e) {
            log.error("Failed to register webhook: {}", e.getMessage());
        }
    }

    public void deleteWebhook() {
        String url = TELEGRAM_API + botToken + "/deleteWebhook";
        try {
            restTemplate.postForObject(url, Map.of(), String.class);
            log.info("Webhook deleted");
        } catch (Exception e) {
            log.debug("Could not delete webhook: {}", e.getMessage());
        }
    }
}
