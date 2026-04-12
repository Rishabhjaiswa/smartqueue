package com.smartqueue.backend.service;

import com.smartqueue.backend.dto.TokenResponse;
import com.smartqueue.backend.entity.Token;
import com.smartqueue.backend.enums.TokenStatus;
import com.smartqueue.backend.repository.TokenRepository;
import com.smartqueue.backend.dto.DoctorQueueDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DoctorQueueService {

    private final RedisTemplate<String, String> redisTemplate;
    private final TokenRepository tokenRepository;
    private final WebSocketBroadcastService broadcastService;
    private final WaitTimeEstimator waitTimeEstimator;

    // ✅ CALL NEXT PATIENT
    public TokenResponse callNext(Long doctorId) {

        String key = "queue:doctor:" + doctorId;

        // Get first token from Redis (lowest score = highest priority)
        Set<String> top;

        try {
            top = redisTemplate.opsForZSet().range(key, 0, 0);
        } catch (Exception e) {
            return TokenResponse.builder()
                    .message("Redis not available / Queue empty")
                    .build();
        }

        if (top == null || top.isEmpty()) {
            return TokenResponse.builder()
                    .message("Queue empty.")
                    .build();
        }

        String tokenIdStr = top.iterator().next();

        // Remove from queue
        redisTemplate.opsForZSet().remove(key, tokenIdStr);

        Long tokenId = Long.parseLong(tokenIdStr);

        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new RuntimeException("Token not found"));

        // Update status
        token.setStatus(TokenStatus.CALLED);
        token.setCalledAt(LocalDateTime.now());

        tokenRepository.save(token);

        broadcastService.broadcastDoctorQueue(
                doctorId,
                buildDoctorQueueDTO(doctorId)
        );

        return buildResponse(token);
    }

    // ✅ START CONSULTATION
    public void startConsultation(Long tokenId, Long doctorId) {

        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new RuntimeException("Token not found"));

        validateOwnership(token, doctorId);

        token.setStatus(TokenStatus.IN_CONSULTATION);
        token.setConsultationStart(LocalDateTime.now());

        tokenRepository.save(token);

        broadcastService.broadcastDoctorQueue(
                doctorId,
                buildDoctorQueueDTO(doctorId)
        );
    }


    // ✅ COMPLETE CONSULTATION
    public void completeConsultation(Long tokenId, Long doctorId) {

        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new RuntimeException("Token not found"));

        validateOwnership(token, doctorId);

        LocalDateTime end = LocalDateTime.now();

        int duration = (int) Duration.between(
                token.getConsultationStart(), end).toMinutes();

        token.setStatus(TokenStatus.COMPLETED);
        token.setConsultationEnd(end);
        token.setConsultDurationMins(duration);

        tokenRepository.save(token);

        broadcastService.broadcastDoctorQueue(
                doctorId,
                buildDoctorQueueDTO(doctorId)
        );
    }

    private DoctorQueueDTO buildDoctorQueueDTO(Long doctorId) {

        // For now minimal safe implementation
        return DoctorQueueDTO.builder()
                .doctorId(doctorId)
                .doctorName("Doctor " + doctorId)
                .roomNumber("Room 1")
                .currentToken("N/A")
                .currentPatientName("N/A")
                .waitingCount(0)
                .estimatedWaitMinutes(0)
                .nextTokens(java.util.List.of())
                .build();
    }

    // 🔐 CRITICAL SECURITY METHOD
    private void validateOwnership(Token token, Long doctorId) {
        if (!token.getDoctorId().equals(doctorId)) {
            throw new AccessDeniedException(
                    "Token does not belong to this doctor");
        }
    }

    // 🔧 Helper
    private TokenResponse buildResponse(Token token) {
        return TokenResponse.builder()
                .id(token.getId())
                .tokenNumber(token.getTokenNumber())
                .status(token.getStatus().name())
                .message("Next patient called")
                .build();
    }
}