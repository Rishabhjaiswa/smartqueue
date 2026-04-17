package com.smartqueue.backend.service;

import com.smartqueue.backend.dto.DoctorQueueDTO;
import com.smartqueue.backend.entity.Token;
import com.smartqueue.backend.enums.TokenStatus;
import com.smartqueue.backend.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClinicScheduledJobs {

    private final TokenRepository tokenRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final WebSocketBroadcastService broadcastService;
    private final DoctorQueueService doctorQueueService;

    @Value("${clinic.no-show-timeout-minutes:10}")
    private int noShowTimeoutMins;

    @Scheduled(fixedDelay = 60000) // runs every 1 minute
    public void autoExpireCalledTokens() {

        List<Token> expired = tokenRepository
                .findByStatusAndCalledAtBefore(
                        TokenStatus.CALLED,
                        LocalDateTime.now().minusMinutes(noShowTimeoutMins)
                );

        for (Token token : expired) {

            // 1. Update status
            token.setStatus(TokenStatus.EXPIRED);
            tokenRepository.save(token);

            // 2. Remove from Redis queue
            redisTemplate.opsForZSet().remove(
                    "queue:doctor:" + token.getDoctorId(),
                    token.getId().toString()
            );

            // 3. Broadcast update (important)
            broadcastService.broadcastDoctorQueue(
                    token.getDoctorId(),
                    doctorQueueService.buildDoctorQueueDTO(token.getDoctorId())
            );

            log.info("⚠️ Auto NO_SHOW: token {}",
                    token.getTokenNumber());
        }
    }
}
