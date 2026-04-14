package com.smartqueue.backend.service;

import com.smartqueue.backend.dto.ReceptionOverviewDTO;
import com.smartqueue.backend.dto.TokenResponse;
import com.smartqueue.backend.entity.Doctor;
import com.smartqueue.backend.entity.Token;
import com.smartqueue.backend.enums.TokenStatus;
import com.smartqueue.backend.repository.DoctorRepository;
import com.smartqueue.backend.repository.TokenRepository;
import com.smartqueue.backend.dto.DoctorQueueDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DoctorQueueService {

    private final RedisTemplate<String, String> redisTemplate;
    private final TokenRepository tokenRepository;
    private final WebSocketBroadcastService broadcastService;
    private final WaitTimeEstimator waitTimeEstimator;
    private final DoctorRepository doctorRepository;

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
        broadcastService.broadcastCurrentToken(
                doctorId,
                token.getTokenNumber()
        );

        if (token.getPatient() != null) {
            broadcastService.notifyPatient(
                    token.getPatient().getId(),
                    "Your token " + token.getTokenNumber() + " is being called. Please proceed."
            );
        }

        broadcastService.broadcastDoctorQueue(
                doctorId,
                buildDoctorQueueDTO(doctorId)
        );

        broadcastService.broadcastReceptionOverview(
                buildReceptionOverview()
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

        broadcastService.broadcastReceptionOverview(
                buildReceptionOverview()
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

        // ✅ REMOVE FROM REDIS (IMPORTANT)
        redisTemplate.opsForZSet().remove(
                "queue:doctor:" + doctorId,
                tokenId.toString()
        );

        broadcastService.broadcastDoctorQueue(
                doctorId,
                buildDoctorQueueDTO(doctorId)
        );

        broadcastService.broadcastReceptionOverview(
                buildReceptionOverview()
        );
    }

    DoctorQueueDTO buildDoctorQueueDTO(Long doctorId) {

        String key = "queue:doctor:" + doctorId;

        // ✅ Fetch doctor
        Doctor doctor = doctorRepository.findById(doctorId).orElse(null);

        // ✅ Get current token (CALLED)
        Token current = tokenRepository
                .findTopByDoctorIdAndStatusOrderByCalledAtDesc(
                        doctorId,
                        TokenStatus.CALLED
                )
                .orElse(null);

        // ✅ Get all tokens from Redis
        Set<String> tokenIds = redisTemplate.opsForZSet()
                .range(key, 0, -1);

        if (tokenIds == null || tokenIds.isEmpty()) {
            return DoctorQueueDTO.builder()
                    .doctorId(doctorId)
                    .doctorName(doctor != null ? doctor.getName() : "Doctor " + doctorId)
                    .roomNumber(doctor != null ? doctor.getRoomNumber() : "Room")

                    .currentToken(
                            current != null ? current.getTokenNumber() : "N/A"
                    )
                    .currentPatientName(
                            current != null && current.getPatient() != null
                                    ? current.getPatient().getName()
                                    : "N/A"
                    )

                    .waitingCount(0)
                    .estimatedWaitMinutes(0)
                    .nextTokens(java.util.List.of())
                    .build();
        }

        List<DoctorQueueDTO.QueueEntryDTO> nextTokens = new ArrayList<>();

        int position = 0;

        for (String tokenIdStr : tokenIds) {

            Long tokenId = Long.parseLong(tokenIdStr);

            Token token = tokenRepository.findById(tokenId)
                    .orElse(null);

            if (token == null) continue;

            // ✅ Safe wait time
            int waitTime = java.util.Optional.ofNullable(
                    waitTimeEstimator.estimateForPosition(position, doctorId)
            ).orElse(0);

            nextTokens.add(
                    DoctorQueueDTO.QueueEntryDTO.builder()
                            .tokenNumber(token.getTokenNumber())
                            .patientName(
                                    token.getPatient() != null
                                            ? token.getPatient().getName()
                                            : "Walk-in"
                            )
                            .visitType(token.getVisitType().name())
                            .estimatedWaitMinutes(waitTime)
                            .build()
            );

            position++;
        }

        int totalWait = nextTokens.isEmpty()
                ? 0
                : nextTokens.get(nextTokens.size() - 1).getEstimatedWaitMinutes();

        return DoctorQueueDTO.builder()
                .doctorId(doctorId)
                .doctorName(doctor != null ? doctor.getName() : "Doctor " + doctorId)
                .roomNumber(doctor != null ? doctor.getRoomNumber() : "Room")

                .currentToken(
                        current != null ? current.getTokenNumber() : "N/A"
                )
                .currentPatientName(
                        current != null && current.getPatient() != null
                                ? current.getPatient().getName()
                                : "N/A"
                )

                .waitingCount(nextTokens.size())
                .estimatedWaitMinutes(totalWait)
                .nextTokens(nextTokens)
                .build();
    }

    private ReceptionOverviewDTO buildReceptionOverview() {

        // TEMP: assume 1 doctor for now
        Long doctorId = 1L;

        DoctorQueueDTO queue = buildDoctorQueueDTO(doctorId);

        ReceptionOverviewDTO.DoctorSummary summary =
                ReceptionOverviewDTO.DoctorSummary.builder()
                        .doctorId(doctorId)
                        .doctorName(queue.getDoctorName())
                        .currentToken(queue.getCurrentToken())
                        .waitingCount(queue.getWaitingCount())
                        .avgConsultTime(5) // fallback for now
                        .build();

        return ReceptionOverviewDTO.builder()
                .totalDoctorsActive(1)
                .totalPatientsWaiting(queue.getWaitingCount())
                .doctors(java.util.List.of(summary))
                .build();
    }

    // 🔐 CRITICAL SECURITY METHOD
    private void validateOwnership(Token token, Long doctorId) {
        if (!token.getDoctorId().equals(doctorId)) {
            throw new AccessDeniedException(
                    "Token does not belong to this doctor");
        }
    }

    private void broadcastAllDoctors() {

        List<Doctor> doctors = doctorRepository.findByAvailableTrue();

        for (Doctor doctor : doctors) {
            broadcastService.broadcastDoctorQueue(doctor.getId(),null);
        }
    }

    private Long findBestDoctorForToken(Token token, Long excludeDoctorId) {

        // 🔥 1. FOLLOW-UP → same doctor
        if ("FOLLOW_UP".equalsIgnoreCase(token.getVisitType().name())) {
            if (token.getDoctorId() != null &&
                    !token.getDoctorId().equals(excludeDoctorId)) {
                return token.getDoctorId();
            }
        }

        List<Doctor> doctors = doctorRepository.findByAvailableTrue();

        Long bestDoctorId = null;
        double bestScore = Double.MAX_VALUE;

        for (Doctor doctor : doctors) {

            if (doctor.getId().equals(excludeDoctorId)) continue;

            String key = "queue:doctor:" + doctor.getId();
            Long queueSize = redisTemplate.opsForZSet().zCard(key);

            if (queueSize == null) queueSize = 0L;

            double avgTime = doctor.getAvgConsultMins() != null
                    ? doctor.getAvgConsultMins()
                    : 5.0;

            double score;

            // 🔥 2. EMERGENCY → prioritize speed
            if (token.getSeverityScore() != null && token.getSeverityScore() > 7) {
                score = avgTime; // fastest doctor wins
            }
            // 🔥 3. NORMAL → load balancing
            else {
                score = queueSize * (avgTime + 2);
            }

            if (score < bestScore) {
                bestScore = score;
                bestDoctorId = doctor.getId();
            }
        }

        return bestDoctorId;
    }

    private void redistributeQueue(Long fromDoctorId) {

        String fromKey = "queue:doctor:" + fromDoctorId;

        Set<String> tokens = redisTemplate.opsForZSet()
                .range(fromKey, 0, -1);

        if (tokens == null || tokens.isEmpty()) return;

        for (String tokenIdStr : tokens) {

            Long tokenId = Long.parseLong(tokenIdStr);

            Token token = tokenRepository.findById(tokenId)
                    .orElse(null);

            if (token == null) continue;

            // 🔥 STEP 1: Find best doctor dynamically EACH TIME
            Long toDoctorId = findBestDoctorForToken(token,fromDoctorId);

            if (toDoctorId == null) {
                System.out.println("⚠️ No available doctor found!");
                continue;
            }

            String toKey = "queue:doctor:" + toDoctorId;

            // 🔥 STEP 2: Update DB
            token.setDoctorId(toDoctorId);
            tokenRepository.save(token);

            // 🔥 STEP 3: Move Redis (preserve score)
            Double score = redisTemplate.opsForZSet()
                    .score(fromKey, tokenIdStr);

            if (score != null) {
                redisTemplate.opsForZSet().add(toKey, tokenIdStr, score);
            }

            redisTemplate.opsForZSet().remove(fromKey, tokenIdStr);
        }

        // 🔥 STEP 4: Broadcast for ALL doctors
        broadcastAllDoctors();

        System.out.println("🔄 Smart redistribution complete for doctor " + fromDoctorId);
    }

    public void setAvailability(Long doctorId, boolean available) {

        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        doctor.setAvailable(available);
        doctorRepository.save(doctor);

        if (!available) {
            redistributeQueue(doctorId);
        }

        broadcastAllDoctors();
    }

    private Long findBestDoctor(Long excludeDoctorId) {

        List<Doctor> doctors = doctorRepository.findByAvailableTrue();

        Long bestDoctorId = null;
        double bestScore = Double.MAX_VALUE;

        for (Doctor doctor : doctors) {

            if (doctor.getId().equals(excludeDoctorId)) continue;

            // Queue size
            String key = "queue:doctor:" + doctor.getId();
            Long queueSize = redisTemplate.opsForZSet().zCard(key);

            if (queueSize == null) queueSize = 0L;

            // Avg consult time
            double avgTime = doctor.getAvgConsultMins() != null
                    ? doctor.getAvgConsultMins()
                    : 5.0;

            double score = queueSize * (avgTime+2);

            if (score < bestScore) {
                bestScore = score;
                bestDoctorId = doctor.getId();
            }
        }

        if (bestDoctorId == null) {
            throw new RuntimeException("No suitable doctor found for redistribution");
        }

        return bestDoctorId;
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