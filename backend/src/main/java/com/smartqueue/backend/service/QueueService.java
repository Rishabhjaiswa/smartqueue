package com.smartqueue.backend.service;

import com.smartqueue.backend.dto.QueueStateDTO;
import com.smartqueue.backend.dto.TokenRequest;
import com.smartqueue.backend.dto.TokenResponse;
import com.smartqueue.backend.entity.Token;
import com.smartqueue.backend.enums.PriorityFlag;
import com.smartqueue.backend.enums.TokenStatus;
import com.smartqueue.backend.repository.DoctorRepository;
import com.smartqueue.backend.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QueueService {

    private final TokenRepository tokenRepository;
    private final DoctorRepository doctorRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final WebSocketBroadcastService broadcastService;

    // 🔑 Redis Keys
    private static final String QUEUE_KEY   = "queue:doctor:";
    private static final String CONSULT_KEY = "consultation:doctor:";
    private static final String LOAD_KEY    = "doctor:load:";

    // 🎯 Priority Boost
    private static final long SENIOR_BONUS    = 600_000L;
    private static final long EMERGENCY_BONUS = 1_800_000L;

    // 🔹 Generate Token
    public TokenResponse generateToken(TokenRequest request) {

        long score = System.currentTimeMillis();

        if (request.getPriorityFlag() == PriorityFlag.SENIOR) {
            score -= SENIOR_BONUS;
        } else if (request.getPriorityFlag() == PriorityFlag.EMERGENCY) {
            score -= EMERGENCY_BONUS;
        }

        Long doctorId = request.getDoctorId();

        long waiting = tokenRepository
                .countByDoctorIdAndStatus(doctorId, TokenStatus.WAITING);

        String tokenNumber = "T" + String.format("%03d", waiting + 1);

        Token token = Token.builder()
                .tokenNumber(tokenNumber)
                .serviceType(request.getServiceType())
                .status(TokenStatus.WAITING)
                .priorityScore(score)
                .doctorId(doctorId)
                .visitType(request.getVisitType())
                .severityScore(request.getSeverityScore())
                .createdAt(LocalDateTime.now())
                .build();

        persistTokenAsync(token);

        String key = QUEUE_KEY + doctorId;

        redisTemplate.opsForZSet().add(key, tokenNumber, score);

        // Track doctor load
        redisTemplate.opsForValue().increment(LOAD_KEY + doctorId);

        int position = getPositionInQueue(tokenNumber, doctorId);
        int waitMins = position * 5;

        QueueStateDTO state = getQueueState(doctorId);
        broadcastService.broadcastQueueState(Math.toIntExact(doctorId), state);

        return TokenResponse.builder()
                .tokenNumber(tokenNumber)
                .serviceType(request.getServiceType().name())
                .status(TokenStatus.WAITING.name())
                .positionInQueue(position)
                .estimatedWaitMinutes(waitMins)
                .message("Token generated. Please wait for your number.")
                .build();
    }

    // 🔹 Call Next Token
    public TokenResponse callNextToken(Long doctorId) {

        String key = QUEUE_KEY + doctorId;

        Set<String> next = redisTemplate.opsForZSet().range(key, 0, 0);

        if (next == null || next.isEmpty()) {
            return TokenResponse.builder()
                    .message("No tokens in queue.")
                    .build();
        }

        String tokenNumber = next.iterator().next();

        redisTemplate.opsForZSet().remove(key, tokenNumber);

        tokenRepository
                .findTopByDoctorIdAndStatusOrderByPriorityScoreAsc(doctorId, TokenStatus.WAITING)
                .ifPresent(t -> {
                    t.setStatus(TokenStatus.CALLED);
                    t.setCalledAt(LocalDateTime.now());
                    tokenRepository.save(t);
                });

        // Set current consultation
        redisTemplate.opsForValue().set(
                CONSULT_KEY + doctorId,
                tokenNumber,
                30, TimeUnit.MINUTES
        );

        // Reduce load
        redisTemplate.opsForValue().decrement(LOAD_KEY + doctorId);

        QueueStateDTO state = getQueueState(doctorId);
        broadcastService.broadcastQueueState(Math.toIntExact(doctorId), state);
        broadcastService.broadcastCurrentToken(Math.toIntExact(doctorId), tokenNumber);

        return TokenResponse.builder()
                .tokenNumber(tokenNumber)
                .status(TokenStatus.CALLED.name())
                .message("Now serving: " + tokenNumber)
                .build();
    }

    // 🔹 Complete Token
    public void completeToken(Long tokenId) {
        tokenRepository.findById(tokenId).ifPresent(t -> {

            t.setStatus(TokenStatus.COMPLETED);
            t.setCompletedAt(LocalDateTime.now());

            if (t.getCalledAt() != null) {
                int mins = (int) Duration.between(
                        t.getCalledAt(), LocalDateTime.now()
                ).toMinutes();

                t.setActualConsultationMinutes(mins);

                updateDoctorAvgConsultation(t.getDoctorId(), mins);
            }

            tokenRepository.save(t);

            redisTemplate.delete(CONSULT_KEY + t.getDoctorId());

            QueueStateDTO state = getQueueState(t.getDoctorId());
            broadcastService.broadcastQueueState(Math.toIntExact(t.getDoctorId()), state);
        });
    }

    // 🔹 Mark No Show
    public void markNoShow(Long tokenId, Long doctorId) {
        tokenRepository.findById(tokenId).ifPresent(t -> {
            t.setStatus(TokenStatus.NO_SHOW);
            tokenRepository.save(t);

            QueueStateDTO state = getQueueState(doctorId);
            broadcastService.broadcastQueueState(Math.toIntExact(doctorId), state);
        });
    }

    // 🔹 Staff Override
    public void staffOverride(String tokenNumber, Long doctorId) {

        String key = QUEUE_KEY + doctorId;

        redisTemplate.opsForZSet().remove(key, tokenNumber);

        // Move to top
        redisTemplate.opsForZSet().add(key, tokenNumber, 0);

        QueueStateDTO state = getQueueState(doctorId);
        broadcastService.broadcastQueueState(Math.toIntExact(doctorId), state);
    }

    // 🔹 Queue State
    public QueueStateDTO getQueueState(Long doctorId) {

        String key = QUEUE_KEY + doctorId;

        Set<String> allInQueue = redisTemplate.opsForZSet().range(key, 0, -1);

        String currentToken = "";
        List<String> nextTokens = List.of();

        if (allInQueue != null && !allInQueue.isEmpty()) {
            List<String> list = allInQueue.stream().collect(Collectors.toList());
            currentToken = list.get(0);
            nextTokens = list.subList(1, Math.min(4, list.size()));
        }

        long waitingCount = allInQueue != null ? allInQueue.size() : 0;

        return QueueStateDTO.builder()
                .officeId(Math.toIntExact(doctorId)) // reuse field
                .currentToken(currentToken)
                .waitingCount((int) waitingCount)
                .avgWaitMinutes((int) waitingCount * 5)
                .nextTokens(nextTokens)
                .build();
    }

    // 🔹 Position Helper
    private int getPositionInQueue(String tokenNumber, Long doctorId) {
        String key = QUEUE_KEY + doctorId;
        Long rank = redisTemplate.opsForZSet().rank(key, tokenNumber);
        return rank != null ? rank.intValue() + 1 : 1;
    }

    // 🔥 Adaptive Learning
    private void updateDoctorAvgConsultation(Long doctorId, int actualMinutes) {
        doctorRepository.findById(doctorId).ifPresent(doc -> {

            int old = doc.getAvgConsultationMinutes();
            int updated = (int)(old * 0.8 + actualMinutes * 0.2);

            doc.setAvgConsultationMinutes(
                    Math.max(3, Math.min(updated, 60))
            );

            doctorRepository.save(doc);
        });
    }

    @Async
    public void persistTokenAsync(Token token) {
        tokenRepository.save(token);
    }
}