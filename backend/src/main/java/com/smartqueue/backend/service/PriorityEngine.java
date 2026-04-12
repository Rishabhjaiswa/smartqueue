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
        long baseMs = token.getCreatedAt()
                .toInstant(ZoneOffset.UTC).toEpochMilli();
        long score = baseMs;

        int age = (token.getPatient() != null && token.getPatient().getAge() != null)
                ? token.getPatient().getAge()
                : 30; // default fallback

        score -= ageBonus(age);

        long waitMins = Duration.between(
                token.getCreatedAt(), LocalDateTime.now()).toMinutes();
        score -= (long)(waitMins * 60_000L * starvationMultiplier(waitMins));

        if (token.getVisitType() == VisitType.APPOINTMENT
                && token.getAppointmentId() != null) {
            score -= appointmentUrgencyBonus(token);
        }

        score -= (long)(token.getSeverityScore() * 12_000L);

        score += (long)(doctorQueueSize * 3_000L);

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