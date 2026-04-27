package com.smartqueue.backend.classifier;

import com.smartqueue.backend.enums.PriorityFlag;
import com.smartqueue.backend.enums.ServiceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RuleBasedPreClassifier}.
 *
 * Coverage goals:
 *  - All five ServiceType branches (EMERGENCY, FOLLOW_UP, SPECIALIST, LAB, GENERAL)
 *  - Both priority overrides (EMERGENCY, SENIOR)
 *  - Confidence thresholds (skip-LLM ≥ 0.85, tentative 0.60–0.84, unclear < 0.40)
 *  - Safety-critical: EMERGENCY signals must never be downgraded
 *  - Edge cases: null, blank, mixed-case input
 */
@DisplayName("RuleBasedPreClassifier")
class RuleBasedPreClassifierTest {

    private RuleBasedPreClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new RuleBasedPreClassifier();
    }

    // ── Edge cases ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("null input → unclear result with zero confidence")
        void nullInput() {
            var result = classifier.classify(null);
            assertThat(result.serviceType()).isEqualTo(ServiceType.OTHER);
            assertThat(result.confidence()).isEqualTo(0.0);
            assertThat(result.isUnclear()).isTrue();
        }

        @Test
        @DisplayName("blank string → unclear result")
        void blankInput() {
            var result = classifier.classify("   ");
            assertThat(result.serviceType()).isEqualTo(ServiceType.OTHER);
            assertThat(result.confidence()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("unrelated gibberish → OTHER service, NORMAL priority")
        void unrelatedInput() {
            var result = classifier.classify("asdfgh qwerty");
            assertThat(result.serviceType()).isEqualTo(ServiceType.OTHER);
            assertThat(result.priorityFlag()).isEqualTo(PriorityFlag.NORMAL);
            assertThat(result.confidence()).isLessThan(0.40);
        }

        @Test
        @DisplayName("mixed-case input is normalised correctly")
        void mixedCaseInput() {
            var result = classifier.classify("EMERGENCY chest pain NOW");
            assertThat(result.priorityFlag()).isEqualTo(PriorityFlag.EMERGENCY);
            assertThat(result.serviceType()).isEqualTo(ServiceType.EMERGENCY);
        }
    }

    // ── Service type detection ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Service type detection")
    class ServiceTypeDetection {

        @ParameterizedTest(name = "[{index}] \"{0}\" → EMERGENCY")
        @ValueSource(strings = {
                "I have an emergency",
                "This is an emergency case",
                "emergency visit needed"
        })
        void detectsEmergencyServiceType(String message) {
            var result = classifier.classify(message);
            assertThat(result.serviceType()).isEqualTo(ServiceType.EMERGENCY);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" → FOLLOW_UP")
        @ValueSource(strings = {
                "I need a follow-up appointment",
                "coming for a follow up visit",
                "here for my revisit",
                "scheduled for review"
        })
        void detectsFollowUpServiceType(String message) {
            var result = classifier.classify(message);
            assertThat(result.serviceType()).isEqualTo(ServiceType.FOLLOW_UP);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" → SPECIALIST")
        @ValueSource(strings = {
                "I need a specialist referral",
                "cardio appointment please",
                "need an ortho consultation",
                "derma check required"
        })
        void detectsSpecialistServiceType(String message) {
            var result = classifier.classify(message);
            assertThat(result.serviceType()).isEqualTo(ServiceType.SPECIALIST);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" → LAB")
        @ValueSource(strings = {
                "I need a blood test",
                "here for my lab results",
                "urine sample collection",
                "CT scan appointment"
        })
        void detectsLabServiceType(String message) {
            var result = classifier.classify(message);
            assertThat(result.serviceType()).isEqualTo(ServiceType.LAB);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" → GENERAL")
        @ValueSource(strings = {
                "general check up please",
                "I have a fever",
                "need to consult a doctor",
                "bad cough for 3 days",
                "headache and cold"
        })
        void detectsGeneralServiceType(String message) {
            var result = classifier.classify(message);
            assertThat(result.serviceType()).isEqualTo(ServiceType.GENERAL);
        }
    }

    // ── Priority flag detection ────────────────────────────────────────────────

    @Nested
    @DisplayName("Priority flag detection")
    class PriorityFlagDetection {

        @ParameterizedTest(name = "[{index}] \"{0}\" → EMERGENCY priority")
        @ValueSource(strings = {
                "urgent help needed",
                "critical condition",
                "chest pain severe",
                "can't breathe properly",
                "cannot breathe at all",
                "patient is unconscious",
                "heavy bleeding from wound"
        })
        void detectsEmergencyPriority(String message) {
            var result = classifier.classify(message);
            assertThat(result.priorityFlag()).isEqualTo(PriorityFlag.EMERGENCY);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" → SENIOR priority")
        @ValueSource(strings = {
                "senior citizen needs help",
                "elderly patient",
                "patient aged 72",
                "patient is 85 years old",
                "old age related issues"
        })
        void detectsSeniorPriority(String message) {
            var result = classifier.classify(message);
            assertThat(result.priorityFlag()).isEqualTo(PriorityFlag.SENIOR);
        }

        @Test
        @DisplayName("no priority keywords → NORMAL priority")
        void defaultsToNormalPriority() {
            var result = classifier.classify("I need a general check-up");
            assertThat(result.priorityFlag()).isEqualTo(PriorityFlag.NORMAL);
        }
    }

    // ── Confidence thresholds ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Confidence thresholds")
    class ConfidenceThresholds {

        @Test
        @DisplayName("emergency + emergency service → high confidence (≥ 0.85), skips LLM")
        void emergencyReachesHighConfidence() {
            // emergency keyword hits both priority (+45) and service (+40) → 85 pts → 0.85
            var result = classifier.classify("I have an emergency, urgent help needed");
            assertThat(result.confidence()).isGreaterThanOrEqualTo(0.85);
            assertThat(result.isHighConfidence()).isTrue();
        }

        @Test
        @DisplayName("follow-up only → 40 pts → tentative confidence (0.40–0.84)")
        void followUpIsTentative() {
            var result = classifier.classify("I need a follow-up visit");
            // NORMAL priority (0 pts) + FOLLOW_UP service (40 pts) → 0.40
            assertThat(result.confidence()).isGreaterThanOrEqualTo(0.40);
            assertThat(result.confidence()).isLessThan(0.85);
            assertThat(result.isHighConfidence()).isFalse();
        }

        @Test
        @DisplayName("confidence is capped at 1.0 regardless of points")
        void confidenceIsCappedAtOne() {
            // All possible keyword groups hit simultaneously
            var result = classifier.classify("emergency urgent chest pain need specialist referral");
            assertThat(result.confidence()).isLessThanOrEqualTo(1.0);
        }

        @Test
        @DisplayName("senior + lab → 25 + 40 = 65 pts → 0.65 confidence")
        void seniorLabCombination() {
            var result = classifier.classify("elderly patient needs blood test");
            assertThat(result.priorityFlag()).isEqualTo(PriorityFlag.SENIOR);
            assertThat(result.serviceType()).isEqualTo(ServiceType.LAB);
            assertThat(result.confidence()).isEqualTo(0.65);
        }
    }

    // ── Safety-critical invariants ─────────────────────────────────────────────

    @Nested
    @DisplayName("Safety-critical: EMERGENCY signals must never be downgraded")
    class SafetyInvariants {

        @Test
        @DisplayName("'chest pain' alone triggers EMERGENCY priority even without 'emergency' keyword")
        void chestPainAloneTriggersEmergency() {
            var result = classifier.classify("I have chest pain");
            assertThat(result.priorityFlag()).isEqualTo(PriorityFlag.EMERGENCY);
        }

        @Test
        @DisplayName("'unconscious patient' triggers EMERGENCY priority")
        void unconsciousTriggersEmergency() {
            var result = classifier.classify("patient fell unconscious in lobby");
            assertThat(result.priorityFlag()).isEqualTo(PriorityFlag.EMERGENCY);
        }

        @Test
        @DisplayName("EMERGENCY priority is never NORMAL when safety keyword is present")
        void emergencyIsNeverNormal() {
            for (String keyword : new String[]{"emergency", "urgent", "critical",
                    "chest pain", "can't breathe", "unconscious", "bleeding"}) {
                var result = classifier.classify("patient with " + keyword);
                assertThat(result.priorityFlag())
                        .as("Expected EMERGENCY for keyword: " + keyword)
                        .isEqualTo(PriorityFlag.EMERGENCY);
            }
        }
    }

    // ── ClassificationResult helper methods ───────────────────────────────────

    @Nested
    @DisplayName("ClassificationResult helpers")
    class ResultHelpers {

        @Test
        @DisplayName("isUnclear() true when OTHER + confidence < 0.40")
        void isUnclearWhenOtherAndLowConfidence() {
            var result = new RuleBasedPreClassifier.ClassificationResult(
                    ServiceType.OTHER, PriorityFlag.NORMAL, null, 0.0);
            assertThat(result.isUnclear()).isTrue();
        }

        @Test
        @DisplayName("isUnclear() false when serviceType is known even at low confidence")
        void notUnclearWhenServiceTypeKnown() {
            var result = new RuleBasedPreClassifier.ClassificationResult(
                    ServiceType.GENERAL, PriorityFlag.NORMAL, null, 0.35);
            assertThat(result.isUnclear()).isFalse();
        }

        @Test
        @DisplayName("isHighConfidence() true at exactly 0.85")
        void highConfidenceAtBoundary() {
            var result = new RuleBasedPreClassifier.ClassificationResult(
                    ServiceType.EMERGENCY, PriorityFlag.EMERGENCY, null, 0.85);
            assertThat(result.isHighConfidence()).isTrue();
        }

        @Test
        @DisplayName("isHighConfidence() false just below 0.85")
        void notHighConfidenceJustBelowBoundary() {
            var result = new RuleBasedPreClassifier.ClassificationResult(
                    ServiceType.FOLLOW_UP, PriorityFlag.NORMAL, null, 0.84);
            assertThat(result.isHighConfidence()).isFalse();
        }
    }
}
