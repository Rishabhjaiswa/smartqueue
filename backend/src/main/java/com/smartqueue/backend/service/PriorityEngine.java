package com.smartqueue.backend.service;

import com.smartqueue.backend.entity.Token;
import com.smartqueue.backend.enums.ServiceType;
import com.smartqueue.backend.enums.VisitType;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class PriorityEngine {

    private static final long PRIORITY_POINT_UNIT  = 10_000L;
    private static final long QUEUE_LOAD_UNIT       = 30_000L;
    /** Assistance boost: subtracts ~50 M from score, overriding all other factors. */
    private static final long ASSISTANCE_BOOST      = 50_000_000L;

    public long computeScore(Token token, int doctorQueueSize, int patientAge) {
        long baseScore = token.getCreatedAt()
                .toInstant(ZoneOffset.UTC).toEpochMilli();

        int severityScore = Math.max(0, token.getSeverityScore() != null ? token.getSeverityScore() : 0);
        long waitMins = Math.max(0, Duration.between(token.getCreatedAt(), LocalDateTime.now()).toMinutes());

        int weightedPriority =
                (severityScore * 50)
                        + (ageFactor(patientAge) * 10)
                        + (waitingTimeFactor(waitMins) * 5)
                        + (serviceTypeWeight(token.getServiceType()) * 20)
                        + appointmentAdjustment(token)
                        + visitTypeAdjustment(token.getVisitType());

        long score = baseScore
                - (weightedPriority * PRIORITY_POINT_UNIT)
                + (doctorQueueSize * QUEUE_LOAD_UNIT);

        // requiresAssistance overrides all other factors — guaranteed near-front position.
        if (token.isRequiresAssistance()) {
            score -= ASSISTANCE_BOOST;
        }

        return score;
    }

    private int ageFactor(int age) {
        if (age >= 75) return 3;
        if (age >= 60) return 2;
        if (age <= 12) return 1;
        return 0;
    }

    private int waitingTimeFactor(long waitMins) {
        if (waitMins >= 180) return 30;
        if (waitMins >= 120) return 24;
        if (waitMins >= 60) return 16;
        return (int) Math.min(12, waitMins / 5);
    }

    private int appointmentAdjustment(Token token) {
        if (token.getVisitType() != VisitType.APPOINTMENT || token.getAppointmentScheduledTime() == null) {
            return 0;
        }

        long minutesFromNow = Duration.between(LocalDateTime.now(), token.getAppointmentScheduledTime()).toMinutes();

        if (minutesFromNow > 30) {
            return -120;
        }
        if (minutesFromNow > 15) {
            return -45;
        }
        if (minutesFromNow >= -15) {
            return 30;
        }
        if (minutesFromNow >= -30) {
            return -15;
        }
        return -90;
    }

    private int serviceTypeWeight(ServiceType serviceType) {
        if (serviceType == null) {
            return 0;
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

    private int visitTypeAdjustment(VisitType visitType) {
        if (visitType == null) {
            return 0;
        }
        return switch (visitType) {
            case EMERGENCY -> 40;
            case FOLLOW_UP -> 10;
            case APPOINTMENT, WALK_IN, REFERRAL -> 0;
        };
    }
}
