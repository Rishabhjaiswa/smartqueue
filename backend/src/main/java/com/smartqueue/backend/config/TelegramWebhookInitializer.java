package com.smartqueue.backend.config;

import com.smartqueue.backend.service.TelegramService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class TelegramWebhookInitializer {

    private final TelegramService telegramService;

    @Value("${telegram.bot.webhook-base-url}")
    private String webhookBaseUrl;

    @PostConstruct
    public void registerWebhook() {
        if (webhookBaseUrl == null || webhookBaseUrl.isBlank()
                || webhookBaseUrl.contains("YOUR_NGROK")) {
            log.warn("Telegram webhook URL not configured — skipping registration");
            return;
        }
        String normalizedWebhookBaseUrl = webhookBaseUrl.trim();
        log.info("Registering Telegram webhook asynchronously at: {}", normalizedWebhookBaseUrl);
        // Run in background so Spring startup is not blocked by Telegram API latency
        Thread t = new Thread(() -> telegramService.registerWebhook(normalizedWebhookBaseUrl),
                "telegram-webhook-init");
        t.setDaemon(true);
        t.start();
    }
}
