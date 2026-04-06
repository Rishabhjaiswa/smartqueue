package com.smartqueue.backend.service;

import com.smartqueue.backend.dto.QueueStateDTO;
import com.smartqueue.backend.dto.TokenRequest;
import com.smartqueue.backend.dto.TokenResponse;
import com.smartqueue.backend.entity.Token;
import com.smartqueue.backend.enums.PriorityFlag;
import com.smartqueue.backend.enums.TokenStatus;
import com.smartqueue.backend.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QueueService {

    private final TokenRepository tokenRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final WebSocketBroadcastService broadcastService;

    private static final String QUEUE_KEY = "queue:";
    private static final long SENIOR_BONUS   = 600_000L;
    private static final long EMERGENCY_BONUS = 1_800_000L;

    public TokenResponse generateToken(TokenRequest request) {
        long score = System.currentTimeMillis();

        if (request.getPriorityFlag() == PriorityFlag.SENIOR) {
            score -= SENIOR_BONUS;
        } else if (request.getPriorityFlag() == PriorityFlag.EMERGENCY) {
            score -= EMERGENCY_BONUS;
        }

        long waiting = tokenRepository
                .countByOfficeIdAndStatus(request.getOfficeId(), TokenStatus.WAITING);
        String tokenNumber = "T" + String.format("%03d", waiting + 1);

        Token token = Token.builder()
                .tokenNumber(tokenNumber)
                .serviceType(request.getServiceType())
                .status(TokenStatus.WAITING)
                .priorityScore(score)
                .officeId(request.getOfficeId())
                .createdAt(LocalDateTime.now())
                .build();

        persistTokenAsync(token);

        String key = QUEUE_KEY + request.getOfficeId();
        redisTemplate.opsForZSet().add(key, tokenNumber, score);

        int position = getPositionInQueue(tokenNumber, request.getOfficeId());
        int waitMins = position * 5;
        QueueStateDTO state = getQueueState(request.getOfficeId());
        broadcastService.broadcastQueueState(request.getOfficeId(), state);

        return TokenResponse.builder()
                .tokenNumber(tokenNumber)
                .serviceType(request.getServiceType().name())
                .status(TokenStatus.WAITING.name())
                .positionInQueue(position)
                .estimatedWaitMinutes(waitMins)
                .message("Token generated. Please wait for your number.")
                .build();
    }

    public TokenResponse callNextToken(Integer officeId) {
        String key = QUEUE_KEY + officeId;
        Set<String> next = redisTemplate.opsForZSet().range(key, 0, 0);

        if (next == null || next.isEmpty()) {
            return TokenResponse.builder()
                    .message("No tokens in queue.")
                    .build();
        }

        String tokenNumber = next.iterator().next();
        redisTemplate.opsForZSet().remove(key, tokenNumber);

        tokenRepository
                .findTopByOfficeIdAndStatusOrderByPriorityScoreAsc(officeId, TokenStatus.WAITING)
                .ifPresent(t -> {
                    t.setStatus(TokenStatus.CALLED);
                    t.setCalledAt(LocalDateTime.now());
                    tokenRepository.save(t);
                });

        QueueStateDTO state = getQueueState(officeId);
        broadcastService.broadcastQueueState(officeId, state);
        broadcastService.broadcastCurrentToken(officeId, tokenNumber);

        return TokenResponse.builder()
                .tokenNumber(tokenNumber)
                .status(TokenStatus.CALLED.name())
                .message("Now serving: " + tokenNumber)
                .build();
    }

    public void completeToken(Long tokenId) {
        tokenRepository.findById(tokenId).ifPresent(t -> {
            t.setStatus(TokenStatus.COMPLETED);
            t.setCompletedAt(LocalDateTime.now());
            tokenRepository.save(t);
            QueueStateDTO state = getQueueState(t.getOfficeId());
            broadcastService.broadcastQueueState(t.getOfficeId(), state);
        });
    }

    public void markNoShow(Long tokenId, Integer officeId) {
        String key = QUEUE_KEY + officeId;
        tokenRepository.findById(tokenId).ifPresent(t -> {
            redisTemplate.opsForZSet().remove(key, t.getTokenNumber());
            t.setStatus(TokenStatus.NO_SHOW);
            tokenRepository.save(t);
            QueueStateDTO state = getQueueState(officeId);
            broadcastService.broadcastQueueState(officeId, state);
        });
    }

    public void staffOverride(String tokenNumber, Integer officeId) {
        String key = QUEUE_KEY + officeId;
        redisTemplate.opsForZSet().add(key, tokenNumber, Long.MIN_VALUE);
    }

    public QueueStateDTO getQueueState(Integer officeId) {
        String key = QUEUE_KEY + officeId;

        Set<String> allInQueue = redisTemplate.opsForZSet()
                .range(key, 0, -1);

        String currentToken = "";
        List<String> nextTokens = List.of();

        if (allInQueue != null && !allInQueue.isEmpty()) {
            List<String> list = allInQueue.stream().collect(Collectors.toList());
            currentToken = list.get(0);
            nextTokens = list.subList(1, Math.min(4, list.size()));
        }

        long waitingCount = allInQueue != null ? allInQueue.size() : 0;

        return QueueStateDTO.builder()
                .officeId(officeId)
                .currentToken(currentToken)
                .waitingCount((int) waitingCount)
                .avgWaitMinutes((int) waitingCount * 5)
                .nextTokens(nextTokens)
                .build();
    }

    private int getPositionInQueue(String tokenNumber, Integer officeId) {
        String key = QUEUE_KEY + officeId;
        Long rank = redisTemplate.opsForZSet().rank(key, tokenNumber);
        return rank != null ? rank.intValue() + 1 : 1;
    }

    @Async
    public void persistTokenAsync(Token token) {
        tokenRepository.save(token);
    }
}