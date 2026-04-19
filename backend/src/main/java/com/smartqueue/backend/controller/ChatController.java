package com.smartqueue.backend.controller;

import com.smartqueue.backend.dto.ChatRequest;
import com.smartqueue.backend.dto.ChatResponse;
import com.smartqueue.backend.idempotency.IdempotencyService;
import com.smartqueue.backend.service.AIService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final AIService aiService;
    private final IdempotencyService idempotencyService;

    /**
     * POST /api/chat — AI triage endpoint.
     *
     * Idempotency: clients SHOULD send a unique {@code X-Idempotency-Key} header
     * per logical request (e.g. a UUID generated once on the frontend).
     * On retry, the same key returns the cached ChatResponse from Redis (10-min TTL)
     * without re-running Ollama and without issuing a second token.
     *
     * If no key is provided we fall through to normal processing (no cache).
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpSession session) {

        request.setSessionId(session.getId());

        // ── Idempotency fast-path ──────────────────────────────────────────────
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            ChatResponse cached = idempotencyService.getResult(idempotencyKey, ChatResponse.class);
            if (cached != null) {
                log.debug("Idempotency cache hit for key={}", idempotencyKey);
                return ResponseEntity.ok(cached);
            }
        }

        // ── Normal processing ─────────────────────────────────────────────────
        ChatResponse response = aiService.processMessage(request);

        // Store result only when a token was actually created (not clarification prompts)
        if (idempotencyKey != null && !idempotencyKey.isBlank() && Boolean.TRUE.equals(response.isTokenGenerated())) {
            idempotencyService.store(idempotencyKey, response);
        }

        return ResponseEntity.ok(response);
    }
}