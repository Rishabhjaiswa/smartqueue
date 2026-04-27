package com.smartqueue.backend.classifier;

import com.smartqueue.backend.enums.PriorityFlag;
import com.smartqueue.backend.enums.ServiceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Fast, deterministic, zero-latency triage classifier.
 *
 * Tier 1 of the hybrid Rule-Agent triage pipeline.
 * If confidence >= 0.85, skips BioMistral entirely (saves ~4-8s per request).
 * Also used as the circuit-breaker fallback when BioMistral is unavailable.
 *
 * Confidence scoring:
 *  - 0.0 – 0.59 : unclear, send to LLM
 *  - 0.60 – 0.84: rule result is tentative, LLM enriches
 *  - 0.85 – 1.0 : high confidence, skip LLM
 */
@Component
@Slf4j
public class RuleBasedPreClassifier {

    private static final Pattern SENIOR_AGE_PATTERN =
            Pattern.compile("\\b([6-9][0-9])\\b");

    public ClassificationResult classify(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return ClassificationResult.unclear();
        }

        String lower = rawMessage.toLowerCase();
        ServiceType serviceType = ServiceType.OTHER;
        PriorityFlag priorityFlag = PriorityFlag.NORMAL;
        String specialization = "GENERAL";
        int confidencePoints = 0;

        // ── Priority (safety-critical, evaluated first) ───────────────────────
        if (lower.contains("emergency") || lower.contains("urgent")
                || lower.contains("critical") || lower.contains("chest pain")
                || lower.contains("can't breathe") || lower.contains("cannot breathe")
                || lower.contains("unconscious") || lower.contains("bleeding")
                || lower.contains("stroke")) {
            priorityFlag = PriorityFlag.EMERGENCY;
            confidencePoints += 45;
        } else if (lower.contains("senior") || lower.contains("elderly")
                || lower.contains("aged") || lower.contains("old age")
                || SENIOR_AGE_PATTERN.matcher(lower).find()) {
            priorityFlag = PriorityFlag.SENIOR;
            confidencePoints += 25;
        }

        // ── Service type ──────────────────────────────────────────────────────
        if (lower.contains("emergency")) {
            serviceType = ServiceType.EMERGENCY;
            specialization = "CARDIOLOGY";    // emergencies default to cardiology triage
            confidencePoints += 40;
        } else if (lower.contains("follow") || lower.contains("follow-up")
                || lower.contains("revisit") || lower.contains("review")) {
            serviceType = ServiceType.FOLLOW_UP;
            confidencePoints += 40;
        } else if (lower.contains("lab") || lower.contains("test")
                || lower.contains("blood") || lower.contains("urine")
                || lower.contains("sample") || lower.contains("scan")) {
            serviceType = ServiceType.LAB;
            confidencePoints += 40;
        } else if (lower.contains("specialist") || lower.contains("referral")) {
            serviceType = ServiceType.SPECIALIST;
            confidencePoints += 35;
        } else if (lower.contains("general") || lower.contains("check")
                || lower.contains("consult") || lower.contains("doctor")
                || lower.contains("fever") || lower.contains("cold")
                || lower.contains("cough") || lower.contains("fatigue")
                || lower.contains("headache") || lower.contains("vomit")) {
            serviceType = ServiceType.GENERAL;
            confidencePoints += 35;
        }

        // ── Specialization matrix (Tier 1 fast-path) ─────────────────────────
        if (lower.contains("chest") || lower.contains("heart")
                || lower.contains("palpitation") || lower.contains("hypertension")
                || lower.contains("cardio")) {
            specialization = "CARDIOLOGY";
            confidencePoints += 10;
        } else if (lower.contains("child") || lower.contains("baby")
                || lower.contains("infant") || lower.contains("toddler")
                || lower.contains("kid") || lower.contains("paediatric")
                || lower.contains("pediatric") || lower.contains("vaccination")) {
            specialization = "PEDIATRICS";
            confidencePoints += 10;
        } else if (lower.contains("skin") || lower.contains("rash")
                || lower.contains("acne") || lower.contains("eczema")
                || lower.contains("itching") || lower.contains("derma")) {
            specialization = "DERMATOLOGY";
            confidencePoints += 10;
        } else if (lower.contains("bone") || lower.contains("joint")
                || lower.contains("fracture") || lower.contains("back pain")
                || lower.contains("knee") || lower.contains("shoulder")
                || lower.contains("spine") || lower.contains("arthritis")
                || lower.contains("ortho")) {
            specialization = "ORTHOPEDICS";
            confidencePoints += 10;
        }

        double confidence = Math.min(1.0, confidencePoints / 100.0);

        log.debug("Rule classifier: service={} spec={} priority={} conf={} msg='{}'",
                serviceType, specialization, priorityFlag, confidence, rawMessage);

        return new ClassificationResult(serviceType, priorityFlag, specialization, confidence);
    }

    // ── Result record ─────────────────────────────────────────────────────────

    public record ClassificationResult(
            ServiceType serviceType,
            PriorityFlag priorityFlag,
            String suggestedSpecialization,
            double confidence
    ) {
        public boolean isHighConfidence() { return confidence >= 0.85; }
        public boolean isUnclear()        { return serviceType == ServiceType.OTHER && confidence < 0.40; }

        static ClassificationResult unclear() {
            return new ClassificationResult(ServiceType.OTHER, PriorityFlag.NORMAL, "GENERAL", 0.0);
        }
    }
}
