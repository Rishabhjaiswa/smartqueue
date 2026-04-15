package com.smartqueue.backend.service;

import com.smartqueue.backend.dto.*;
import com.smartqueue.backend.entity.Patient;
import com.smartqueue.backend.entity.Token;
import com.smartqueue.backend.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.smartqueue.backend.enums.TokenStatus;

@Service
@RequiredArgsConstructor
public class ReceptionService {

    private final QueueService queueService;
    private final DoctorQueueService doctorQueueService;
    private final TokenRepository tokenRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final WebSocketBroadcastService broadcastService;

    // ✅ REAL WALK-IN WITH PATIENT
    public TokenResponse checkInWalkIn(CheckInRequest req) {

        Patient patient = Patient.builder()
                .name(req.getPatientName())
                .age(req.getAge())
                .build();

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setServiceType(req.getServiceType());
        tokenRequest.setSeverityScore(req.getSeverityScore());
        tokenRequest.setOfficeId(req.getOfficeId());

        return queueService.generateToken(tokenRequest, patient);
    }
    public TokenResponse bookAppointment(AppointmentRequest req) {

        Patient patient = Patient.builder()
                .name(req.getPatientName())
                .age(req.getAge())
                .build();

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setServiceType(req.getServiceType());
        tokenRequest.setSeverityScore(req.getSeverityScore());
        tokenRequest.setOfficeId(req.getOfficeId());

        // 🔥 IMPORTANT
        tokenRequest.setVisitType(
                com.smartqueue.backend.enums.VisitType.APPOINTMENT
        );

        tokenRequest.setAppointmentScheduledTime(
                req.getAppointmentTime()
        );

        return queueService.generateToken(tokenRequest, patient);
    }

    public ReceptionOverviewDTO getOverview() {
        return doctorQueueService.buildReceptionOverview();
    }

    public void markNoShow(Long tokenId) {
        queueService.markNoShow(tokenId, null);
    }

    public void reassignDoctor(Long tokenId, Long newDoctorId) {

        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new RuntimeException("Token not found"));

        Long oldDoctorId = token.getDoctorId();

        // ❌ Prevent same doctor reassignment
        if (oldDoctorId.equals(newDoctorId)) {
            throw new RuntimeException("Already assigned to this doctor");
        }

        // ❌ Only allow WAITING tokens
        if (token.getStatus() != TokenStatus.WAITING) {
            throw new RuntimeException("Only WAITING tokens can be reassigned");
        }

        String oldKey = "queue:doctor:" + oldDoctorId;
        String newKey = "queue:doctor:" + newDoctorId;

        // 🔥 Remove from old queue
        redisTemplate.opsForZSet().remove(oldKey, tokenId.toString());

        // 🔥 Update DB
        token.setDoctorId(newDoctorId);
        tokenRepository.save(token);

        // 🔥 Add to new queue
        redisTemplate.opsForZSet().add(
                newKey,
                tokenId.toString(),
                token.getPriorityScore()
        );

        // 🔥 Broadcast BOTH doctors
        broadcastService.broadcastDoctorQueue(
                oldDoctorId,
                doctorQueueService.buildDoctorQueueDTO(oldDoctorId)
        );

        broadcastService.broadcastDoctorQueue(
                newDoctorId,
                doctorQueueService.buildDoctorQueueDTO(newDoctorId)
        );
    }
    public TokenResponse reinstateNoShow(Long tokenId, String reason) {

        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new RuntimeException("Token not found"));

        // ❌ Only NO_SHOW allowed
        if (token.getStatus() != TokenStatus.NO_SHOW) {
            throw new RuntimeException("Only NO_SHOW tokens can be reinstated");
        }

        token.setStatus(TokenStatus.WAITING);
        tokenRepository.save(token);

        // 🔥 Add back to Redis queue
        redisTemplate.opsForZSet().add(
                "queue:doctor:" + token.getDoctorId(),
                tokenId.toString(),
                token.getPriorityScore()
        );

        // 🔥 Broadcast update
        broadcastService.broadcastDoctorQueue(
                token.getDoctorId(),
                doctorQueueService.buildDoctorQueueDTO(token.getDoctorId())
        );

        return TokenResponse.builder()
                .id(token.getId())
                .tokenNumber(token.getTokenNumber())
                .status(token.getStatus().name())
                .message("Patient reinstated")
                .build();
    }
}