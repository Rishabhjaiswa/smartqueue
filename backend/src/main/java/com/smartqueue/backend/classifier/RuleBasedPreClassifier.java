package com.smartqueue.backend.classifier;

import com.smartqueue.backend.enums.PriorityFlag;
import com.smartqueue.backend.enums.ServiceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Fast, deterministic, zero-latency triage classifier.
 *
 * Extracted from AIService.fallbackKeywordParse() and the inline override
 * block in AIService.processMessage() into a first-class testable component.
 *
 * Design principles:
 *  - Pure function: same input always produces same output (no I/O, no state)
 *  - Sub-millisecond: runs on every request BEFORE deciding whether to call Ollama
 *  - High recall on emergencies: errs toward EMERGENCY/SENIOR to protect patient safety
 *
 * Confidence scoring:
 *  - 0.0 – 0.59 : unclear, send to LLM
 *  - 0.60 – 0.84: rule result is tentative, LLM enriches
 *  - 0.85 – 1.0 : high confidence, skip LLM (saves ~2-4s per request)
 *
 * Current integration:
 *  - AIService calls classify() first. If confidence >= 0.85, skips Ollama.
 *  - If Ollama circuit breaker is open, this result is used as the fallback.
 */
@Component
@Slf4j
public class RuleBasedPreClassifier {

    // Matches standalone 2-digit numbers in range 60–99 (senior age detection)
    private static final Pattern SENIOR_AGE_PATTERN =
            Pattern.compile("\\b([6-9][0-9])\\b");

    public ClassificationResult classify(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return ClassificationResult.unclear();
        }

        String lower = rawMessage.toLowerCase();
        ServiceType serviceType = ServiceType.OTHER;
        PriorityFlag priorityFlag = PriorityFlag.NORMAL;
        int confidencePoints = 0;

        // ── Priority detection (evaluated first; safety-critical) ──────────────
        if (lower.contains("emergency") || lower.contains("urgent")
                || lower.contains("critical") || lower.contains("chest pain")
                || lower.contains("can't breathe") || lower.contains("cannot breathe")
                || lower.contains("unconscious") || lower.contains("bleeding")) {
            priorityFlag = PriorityFlag.EMERGENCY;
            confidencePoints += 45;
        } else if (lower.contains("senior") || lower.contains("elderly")
                || lower.contains("aged") || lower.contains("old age")
                || SENIOR_AGE_PATTERN.matcher(lower).find()) {
            priorityFlag = PriorityFlag.SENIOR;
            confidencePoints += 25;
        }

        // ── Service type detection ─────────────────────────────────────────────
        if (lower.contains("emergency")) {
            serviceType = ServiceType.EMERGENCY;
            confidencePoints += 40;
        } else if (lower.contains("follow") || lower.contains("follow-up")
                || lower.contains("revisit") || lower.contains("review")) {
            serviceType = ServiceType.FOLLOW_UP;
            confidencePoints += 40;
        } else if (lower.contains("specialist") || lower.contains("referral")
                || lower.contains("cardio") || lower.contains("ortho")
                || lower.contains("neuro") || lower.contains("derma")) {
            serviceType = ServiceType.SPECIALIST;
            confidencePoints += 40;
        } else if (lower.contains("lab") || lower.contains("test")
                || lower.contains("blood") || lower.contains("urine")
                || lower.contains("sample") || lower.contains("scan")) {
            serviceType = ServiceType.LAB;
            confidencePoints += 40;
        } else if (lower.contains("general") || lower.contains("check")
                || lower.contains("consult") || lower.contains("doctor")
                || lower.contains("fever") || lower.contains("cold")
                || lower.contains("cough") || lower.contains("pain")) {
            serviceType = ServiceType.GENERAL;
            confidencePoints += 35;
        }

        // Cap at 1.0
        double confidence = Math.min(1.0, confidencePoints / 100.0);

        log.debug("Rule classifier: service={} priority={} confidence={} for message='{}'",
                serviceType, priorityFlag, confidence, rawMessage);

        return new ClassificationResult(serviceType, priorityFlag, confidence);
    }

    // ── Result record ──────────────────────────────────────────────────────────

    public record ClassificationResult(
            ServiceType serviceType,
            PriorityFlag priorityFlag,
            double confidence
    ) {
        /** Returns true if this result is authoritative enough to skip LLM. */
        public boolean isHighConfidence() {
            return confidence >= 0.85;
        }

        /** Returns true if service type is still unknown (needs LLM clarification). */
        public boolean isUnclear() {
            return serviceType == ServiceType.OTHER && confidence < 0.40;
        }

        static ClassificationResult unclear() {
            return new ClassificationResult(ServiceType.OTHER, PriorityFlag.NORMAL, 0.0);
        }
    }
}
