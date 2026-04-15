package com.smartqueue.backend.service;

import com.smartqueue.backend.dto.QueueStateDTO;
import com.smartqueue.backend.dto.TokenRequest;
import com.smartqueue.backend.dto.TokenResponse;
import com.smartqueue.backend.entity.Doctor;
import com.smartqueue.backend.entity.Patient;
import com.smartqueue.backend.entity.Token;
import com.smartqueue.backend.enums.TokenStatus;
import com.smartqueue.backend.repository.DoctorRepository;
import com.smartqueue.backend.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class QueueService {

    private final TokenRepository tokenRepository;
    private final DoctorRepository doctorRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final WebSocketBroadcastService broadcastService;
    private final PriorityEngine priorityEngine;
    private final DoctorQueueService doctorQueueService;

    private static final String QUEUE_KEY = "queue:doctor:";
    public TokenResponse generateToken(TokenRequest request) {

        // temporary dummy patient (until full Patient system ready)
        Patient dummyPatient = Patient.builder()
                .name("Walk-in")
                .age(30)
                .build();

        return generateToken(request, dummyPatient);
    }

    public TokenResponse callNextToken(Integer officeId) {
        return TokenResponse.builder()
                .message("Deprecated - use doctor-based flow")
                .build();
    }

    public void completeToken(Long tokenId) {
        tokenRepository.findById(tokenId).ifPresent(t -> {
            t.setStatus(TokenStatus.COMPLETED);
            t.setCompletedAt(LocalDateTime.now());
            tokenRepository.save(t);

            redisTemplate.opsForZSet().remove(
                    QUEUE_KEY + t.getDoctorId(),
                    t.getId().toString()
            );

            broadcastService.broadcastDoctorQueue(t.getDoctorId(), null);
        });
    }

    public void markNoShow(Long tokenId, Integer officeId) {

        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new RuntimeException("Token not found"));

        // ✅ Prevent invalid state
        if (token.getStatus() != TokenStatus.WAITING) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only WAITING tokens can be marked NO_SHOW"
            );
        }

        token.setStatus(TokenStatus.NO_SHOW);
        tokenRepository.save(token);

        // ✅ Remove from Redis safely
        redisTemplate.opsForZSet().remove(
                "queue:doctor:" + token.getDoctorId(),
                tokenId.toString()
        );

        // ✅ Safe broadcast
        broadcastService.broadcastDoctorQueue(
                token.getDoctorId(),
                doctorQueueService.buildDoctorQueueDTO(token.getDoctorId())// or call service
        );
    }

    public void staffOverride(String tokenNumber, Integer officeId) {
        // no-op for now
    }

    public TokenResponse generateToken(TokenRequest request, Patient patient) {

        Doctor doctor = assignDoctor(request);

        int queueSize = Optional.ofNullable(
                redisTemplate.opsForZSet().size(QUEUE_KEY + doctor.getId())
        ).map(Long::intValue).orElse(0);

        Token token = Token.builder()
                .doctorId(doctor.getId())
                .tokenNumber(generateNumber(doctor.getId()))
                .serviceType(request.getServiceType())
                .visitType(
                request.getVisitType() != null
                        ? request.getVisitType()
                        : com.smartqueue.backend.enums.VisitType.WALK_IN
        )
                .chiefComplaint(request.getChiefComplaint())
                .severityScore(
                        request.getSeverityScore() != null
                                ? request.getSeverityScore()
                                : 0
                )
                .appointmentScheduledTime(request.getAppointmentScheduledTime())
                .status(TokenStatus.WAITING)
                .officeId(request.getOfficeId())
                .createdAt(LocalDateTime.now())
                .build();

        long score = priorityEngine.computeScore(token, queueSize);

        token.setPriorityScore(score);
        token.setDynamicScore(score);
        token.setLastScoreUpdate(LocalDateTime.now());

        tokenRepository.save(token);

        redisTemplate.opsForZSet().add(
                QUEUE_KEY + doctor.getId(),
                token.getId().toString(),
                score
        );

        int position = getPosition(token.getId(), doctor.getId());
        int avg = Optional.ofNullable(doctor.getAvgConsultMins()).orElse(10);
        int waitMins = position * avg;

        broadcastService.broadcastDoctorQueue(
                doctor.getId(),
                doctorQueueService.buildDoctorQueueDTO(doctor.getId())
        );

        return TokenResponse.builder()
                .id(token.getId())
                .tokenNumber(token.getTokenNumber())
                .serviceType(token.getServiceType().name())
                .status(token.getStatus().name())
                .doctorName(doctor.getName())
                .roomNumber(doctor.getRoomNumber())
                .positionInQueue(position)
                .estimatedWaitMinutes(waitMins)
                .build();
    }

    private Doctor assignDoctor(TokenRequest request) {

        if (request.getDoctorId() != null) {
            return doctorRepository.findById(request.getDoctorId())
                    .orElseThrow(() -> new RuntimeException("Doctor not found"));
        }

        List<Doctor> available = doctorRepository.findByAvailableTrue();

        return available.stream()
                .min(Comparator.comparingLong(doctor -> {

                    Long queueSize = Optional.ofNullable(
                            redisTemplate.opsForZSet()
                                    .size(QUEUE_KEY + doctor.getId())
                    ).orElse(0L);

                    int avgTime = Optional.ofNullable(doctor.getAvgConsultMins())
                            .orElse(10);

                    // 🎯 Estimated wait time
                    long estimatedWait = queueSize * avgTime;

                    return estimatedWait;

                }))
                .orElseThrow(() -> new RuntimeException("No doctors available"));
    }

    private int getPosition(Long tokenId, Long doctorId) {

        Long rank = redisTemplate.opsForZSet()
                .rank(QUEUE_KEY + doctorId, tokenId.toString());

        return rank != null ? rank.intValue() + 1 : 1;
    }

    private String generateNumber(Long doctorId) {
        long count = tokenRepository.count();
        return "D" + doctorId + "-T" + (count + 1);
    }

    @Scheduled(fixedRateString = "${clinic.priority-recalc-interval-seconds:60}000")
    public void recalculateAllScores() {

        List<Doctor> activeDoctors = doctorRepository.findByAvailableTrue();

        for (Doctor doctor : activeDoctors) {

            String key = QUEUE_KEY + doctor.getId();

            Set<String> ids = redisTemplate.opsForZSet().range(key, 0, -1);
            if (ids == null) continue;

            int queueSize = ids.size();

            for (String idStr : ids) {

                tokenRepository.findById(Long.parseLong(idStr)).ifPresent(token -> {
                    if (token.getStatus() != TokenStatus.WAITING) {
                        return;
                    }
                    if (token.getLastScoreUpdate() != null &&
                            token.getLastScoreUpdate().isAfter(LocalDateTime.now().minusSeconds(30))) {
                        return;
                    }
                    long newScore = priorityEngine.computeScore(token, queueSize);

                    redisTemplate.opsForZSet().add(key, idStr, newScore);

                    if (!Objects.equals(token.getDynamicScore(), newScore)) {

                        token.setDynamicScore(newScore);
                        token.setLastScoreUpdate(LocalDateTime.now());

                        tokenRepository.save(token);
                    }
                });
            }

            broadcastService.broadcastDoctorQueue(
                    doctor.getId(),
                    doctorQueueService.buildDoctorQueueDTO(doctor.getId())
            );
        }
    }

    public QueueStateDTO getQueueState(Long doctorId) {

        String key = QUEUE_KEY + doctorId;

        Set<String> all = redisTemplate.opsForZSet().range(key, 0, -1);

        int waitingCount = all != null ? all.size() : 0;

        return QueueStateDTO.builder()
                .waitingCount(waitingCount)
                .avgWaitMinutes(waitingCount * 10)
                .build();
    }
}