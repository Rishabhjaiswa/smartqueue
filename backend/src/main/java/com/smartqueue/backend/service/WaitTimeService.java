package com.smartqueue.backend.service;

import com.smartqueue.backend.dto.WaitTimeDTO;
import com.smartqueue.backend.entity.Doctor;
import com.smartqueue.backend.entity.Token;
import com.smartqueue.backend.enums.TokenStatus;
import com.smartqueue.backend.exception.NotFoundException;
import com.smartqueue.backend.repository.DoctorRepository;
import com.smartqueue.backend.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class WaitTimeService {

    private final DoctorRepository doctorRepository;
    private final TokenRepository tokenRepository;
    private final RedisTemplate<String, String> redisTemplate;

    public WaitTimeDTO calculateWaitTime(Long tokenId) {

        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new NotFoundException("Token not found"));

        Doctor doctor = doctorRepository.findById(token.getDoctorId())
                .orElseThrow(() -> new NotFoundException("Doctor not found"));

        // 🔹 Get position from Redis
        String key = "queue:doctor:" + token.getDoctorId();
        Long rank = redisTemplate.opsForZSet()
                .rank(key, tokenId.toString());

        int position = (rank != null) ? rank.intValue() + 1 : 1;

        int delayMins = doctor.getDelayMinutes();

        // 🔹 Check if someone is currently in consultation
        String consultKey = "consultation:doctor:" + token.getDoctorId();
        String currentToken = redisTemplate.opsForValue().get(consultKey);

        int currentConsultElapsed = 0;

        if (currentToken != null && position == 1) {

            Token inConsult = tokenRepository
                    .findFirstByDoctorIdAndStatus(
                            token.getDoctorId(),
                            TokenStatus.IN_CONSULTATION
                    );

            if (inConsult != null && inConsult.getCalledAt() != null) {
                currentConsultElapsed = (int) Duration.between(
                        inConsult.getCalledAt(),
                        LocalDateTime.now()
                ).toMinutes();
            }
        }

        int remainingCurrentConsult = Math.max(
                0,
                doctor.getAvgConsultationMinutes() - currentConsultElapsed
        );

        int totalWait;

        if (position == 1) {
            totalWait = remainingCurrentConsult + delayMins;
        } else {
            totalWait = remainingCurrentConsult
                    + ((position - 1) * doctor.getAvgConsultationMinutes())
                    + delayMins;
        }

        String displayMessage = delayMins > 0
                ? "Expected wait: " + totalWait + " min (Dr. " + doctor.getName()
                  + " running " + delayMins + " min late)"
                : "Expected wait: " + totalWait + " min";

        return WaitTimeDTO.builder()
                .tokenId(tokenId)
                .positionInQueue(position)
                .estimatedWaitMinutes(totalWait)
                .doctorDelayMinutes(delayMins)
                .displayMessage(displayMessage)
                .lastUpdated(LocalDateTime.now())
                .build();
    }
}