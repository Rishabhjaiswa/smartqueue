package com.smartqueue.backend.service;

import com.smartqueue.backend.entity.Token;
import com.smartqueue.backend.enums.VisitType;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class PriorityEngine {

    public long computeScore(Token token, int doctorQueueSize) {
        long score = token.getCreatedAt()
                .toInstant(ZoneOffset.UTC).toEpochMilli();

        int age = (token.getPatient() != null && token.getPatient().getAge() != null)
                ? token.getPatient().getAge()
                : 30; // default fallback

        score -= ageBonus(age);

        long waitMins = Duration.between(
                token.getCreatedAt(), LocalDateTime.now()).toMinutes();
        long waitPenalty = (long)(waitMins * 60_000L * starvationMultiplier(waitMins));
        waitPenalty = Math.min(waitPenalty, 3_600_000L); // cap
        score -= waitPenalty;

        if (token.getVisitType() == VisitType.APPOINTMENT
                && token.getAppointmentScheduledTime() != null) {
            score -= appointmentUrgencyBonus(token);
        }

        if (token.getVisitType() == VisitType.FOLLOW_UP) {
            score -= 600_000L;
        }

        score -= (token.getSeverityScore() * 120_000L);

        score += (doctorQueueSize * 60_000L);

        return score;
    }

    private long ageBonus(int age) {
        if (age >= 80) return 1_200_000L;
        if (age >= 60) return 600_000L;
        if (age <= 12) return 400_000L;
        return 0L;
    }

    private double starvationMultiplier(long waitMins) {
        if (waitMins > 90) return 4.0;
        if (waitMins > 60) return 2.5;
        if (waitMins > 30) return 1.5;
        return 1.0;
    }

    private long appointmentUrgencyBonus(Token token) {
        if (token.getAppointmentScheduledTime() == null) return 0L;
        long minsToAppt = Duration.between(
                LocalDateTime.now(),
                token.getAppointmentScheduledTime()).toMinutes();
        if (minsToAppt < 0)   return 1_800_000L;
        if (minsToAppt <= 15) return 900_000L;
        return 0L;
    }
}