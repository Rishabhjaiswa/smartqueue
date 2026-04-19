package com.smartqueue.backend.service;

import com.smartqueue.backend.dto.QueueStateDTO;
import com.smartqueue.backend.dto.TokenRequest;
import com.smartqueue.backend.dto.TokenResponse;
import com.smartqueue.backend.entity.Doctor;
import com.smartqueue.backend.entity.Patient;
import com.smartqueue.backend.entity.Token;
import com.smartqueue.backend.enums.TokenStatus;
import com.smartqueue.backend.enums.VisitType;
import com.smartqueue.backend.lock.RedissonLockService;
import com.smartqueue.backend.repository.DoctorRepository;
import com.smartqueue.backend.repository.PatientRepository;
import com.smartqueue.backend.repository.TokenRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueueService {

    private final TokenRepository tokenRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final WebSocketBroadcastService broadcastService;
    private final RedissonLockService lockService;
    private final MeterRegistry meterRegistry;
    private final PriorityEngine priorityEngine;
    private final DoctorQueueService doctorQueueService;

    private static final String QUEUE_KEY = "queue:doctor:";

    @Value("${clinic.open-hour:9}")
    private int clinicOpenHour;

    @Value("${clinic.close-hour:17}")
    private int clinicCloseHour;

    @Value("${clinic.break-start-hour:0}")
    private int clinicBreakStartHour;

    @Value("${clinic.break-end-hour:0}")
    private int clinicBreakEndHour;

    public TokenResponse generateToken(TokenRequest request) {
        return generateToken(request, null);
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
            t.setConsultationEnd(LocalDateTime.now());
            if (t.getConsultationStart() != null) {
                long duration = java.time.Duration.between(
                        t.getConsultationStart(),
                        t.getConsultationEnd()
                ).toMinutes();
                t.setConsultDurationMins((int) duration);
            }
            tokenRepository.save(t);

            redisTemplate.opsForZSet().remove(
                    QUEUE_KEY + t.getDoctorId(),
                    t.getId().toString()
            );

            broadcastService.broadcastDoctorQueue(
                    t.getDoctorId(),
                    doctorQueueService.buildDoctorQueueDTO(t.getDoctorId())
            );
            broadcastService.broadcastReceptionOverview(
                    doctorQueueService.buildReceptionOverview()
            );
        });
    }

    public void cancelTokenForPatient(Long tokenId, Long patientId) {
        Token token = tokenRepository.findByIdWithPatient(tokenId)
                .orElseThrow(() -> new RuntimeException("Token not found"));

        if (token.getPatient() == null || !token.getPatient().getId().equals(patientId)) {
            throw new RuntimeException("Token does not belong to this patient");
        }

        if (token.getStatus() != TokenStatus.WAITING && token.getStatus() != TokenStatus.CALLED) {
            throw new RuntimeException("Only WAITING or CALLED tokens can be cancelled");
        }

        token.setStatus(TokenStatus.CANCELLED);
        tokenRepository.save(token);

        try {
            redisTemplate.opsForZSet().remove(
                    QUEUE_KEY + token.getDoctorId(),
                    token.getId().toString()
            );
        } catch (Exception ignored) {
        }

        broadcastService.broadcastDoctorQueue(
                token.getDoctorId(),
                doctorQueueService.buildDoctorQueueDTO(token.getDoctorId())
        );
        broadcastService.broadcastReceptionOverview(
                doctorQueueService.buildReceptionOverview()
        );
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
        broadcastService.broadcastReceptionOverview(
                doctorQueueService.buildReceptionOverview()
        );
    }

    public void staffOverride(String tokenNumber, Integer officeId) {
        // no-op for now
    }

    public TokenResponse generateToken(TokenRequest request, Patient patient) {
        validateClinicAccess(request);
        Patient persistedPatient = persistPatient(patient);

        // ── Distributed lock per patient ──────────────────────────────────────
        // Prevents two concurrent requests for the same patient both passing
        // the duplicate-check and creating two tokens (race condition fix).
        String lockKey = "lock:token:patient:" + (persistedPatient != null ? persistedPatient.getId() : "anon");
        return lockService.executeWithLock(lockKey, 5, () -> generateTokenLocked(request, persistedPatient));
    }

    private TokenResponse generateTokenLocked(TokenRequest request, Patient persistedPatient) {

        // ✅ ACTIVE TOKEN CHECK — only for identified patients
        if (persistedPatient != null) {
            Optional<Token> activeTokenOpt = tokenRepository
                    .findFirstByPatientIdAndStatusIn(
                            persistedPatient.getId(),
                            List.of(TokenStatus.WAITING, TokenStatus.CALLED, TokenStatus.IN_CONSULTATION)
                    );

            if (activeTokenOpt.isPresent()) {
                Token existing = activeTokenOpt.get();
                Doctor doctor = doctorRepository.findById(existing.getDoctorId()).orElse(null);
                int position = getPosition(existing.getId(), existing.getDoctorId());
                int avg = (doctor != null && doctor.getAvgConsultMins() != null && doctor.getAvgConsultMins() > 0)
                        ? doctor.getAvgConsultMins() : 10;
                return TokenResponse.builder()
                        .id(existing.getId())
                        .tokenNumber(existing.getTokenNumber())
                        .status(existing.getStatus().name())
                        .doctorName(doctor != null ? doctor.getName() : "Assigned")
                        .roomNumber(doctor != null ? doctor.getRoomNumber() : null)
                        .positionInQueue(position)
                        .estimatedWaitMinutes(position * avg)
                        .message("ALREADY_EXISTS")
                        .build();
            }

            // ✅ RECENT TOKEN CHECK — only for identified patients
            Optional<Token> recentTokenOpt = tokenRepository
                    .findTopByPatientIdOrderByCreatedAtDesc(persistedPatient.getId());
            if (recentTokenOpt.isPresent()) {
                Token recent = recentTokenOpt.get();
                if ((recent.getStatus() == TokenStatus.COMPLETED
                        || recent.getStatus() == TokenStatus.CANCELLED
                        || recent.getStatus() == TokenStatus.NO_SHOW)
                        && recent.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(10))) {
                    throw new IllegalArgumentException(
                            "You recently booked a token. Please wait or check /status.");
                }
            }
        }
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

        token.setPatient(persistedPatient);

        long score = priorityEngine.computeScore(token, queueSize, resolvePatientAge(persistedPatient));
        score = applyVisitTypeAdjustments(token, score);

        token.setPriorityScore(score);
        token.setDynamicScore(score);
        token.setLastScoreUpdate(LocalDateTime.now());

        tokenRepository.save(token);

        validateDoctorAvailability(doctor);

        try {
            redisTemplate.opsForZSet().add(
                    QUEUE_KEY + doctor.getId(),
                    token.getId().toString(),
                    score
            );
        } catch (Exception e) {
            throw new RuntimeException("Unable to add token to doctor queue");
        }

        int position = getPosition(token.getId(), doctor.getId());
        int avg = (doctor.getAvgConsultMins() != null && doctor.getAvgConsultMins() > 0)
                ? doctor.getAvgConsultMins()
                : 10;
        int waitMins = position * avg;

        broadcastService.broadcastDoctorQueue(
                doctor.getId(),
                doctorQueueService.buildDoctorQueueDTO(doctor.getId())
        );
        broadcastService.broadcastReceptionOverview(
                doctorQueueService.buildReceptionOverview()
        );

        Counter.builder("smartqueue.token.generated")
                .tag("serviceType", token.getServiceType().name())
                .tag("priorityFlag", request.getPriorityFlag() != null ? request.getPriorityFlag().name() : "NORMAL")
                .register(meterRegistry)
                .increment();

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
            Doctor requestedDoctor = doctorRepository.findById(request.getDoctorId())
                    .orElseThrow(() -> new RuntimeException("Doctor not found"));
            validateDoctorAvailability(requestedDoctor);
            return requestedDoctor;
        }

        List<Doctor> available = doctorRepository.findByAvailableTrue()
                .stream()
                .filter(doctor -> {
                    Long queueSize = Optional.ofNullable(
                            redisTemplate.opsForZSet().size(QUEUE_KEY + doctor.getId())
                    ).orElse(0L);
                    return doctor.getMaxQueueSize() == null || queueSize < doctor.getMaxQueueSize();
                })
                .toList();

        return available.stream()
                .min(Comparator.comparingLong(doctor -> {

                    Long queueSize = Optional.ofNullable(
                            redisTemplate.opsForZSet()
                                    .size(QUEUE_KEY + doctor.getId())
                    ).orElse(0L);

                    int avgTime = Optional.ofNullable(doctor.getAvgConsultMins())
                            .orElse(10);

                    long estimatedWait = queueSize * avgTime;

                    return queueSize + (estimatedWait / avgTime);

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
    @Transactional
    public void recalculateAllScores() {
        // Cluster-wide leader lock — only one instance runs the recalc job.
        // leaseTime = 55 s < schedule interval (60 s) to auto-release if the
        // instance crashes mid-run before the next trigger fires.
        lockService.executeWithLockIfAvailable("lock:recalc:priority", 55, () -> {
            log.info("Starting priority recalculation for active doctor queues");
            doRecalculateAllScores();
            log.info("Completed priority recalculation");
        });
    }

    private void doRecalculateAllScores() {

        List<Doctor> activeDoctors = doctorRepository.findByAvailableTrue();

        for (Doctor doctor : activeDoctors) {

            String key = QUEUE_KEY + doctor.getId();

            Set<String> ids = redisTemplate.opsForZSet().range(key, 0, -1);
            if (ids == null) continue;

            int queueSize = ids.size();

            for (String idStr : ids) {
                try {
                    tokenRepository.findByIdWithPatient(Long.parseLong(idStr)).ifPresent(token -> {
                        if (token.getStatus() != TokenStatus.WAITING) {
                            return;
                        }
                        if (token.getLastScoreUpdate() != null &&
                                token.getLastScoreUpdate().isAfter(LocalDateTime.now().minusSeconds(30))) {
                            return;
                        }
                        int patientAge = resolvePatientAge(token.getPatient());
                        long newScore = priorityEngine.computeScore(token, queueSize, patientAge);

                        redisTemplate.opsForZSet().add(key, idStr, newScore);

                        if (!Objects.equals(token.getDynamicScore(), newScore)) {
                            token.setDynamicScore(newScore);
                            token.setLastScoreUpdate(LocalDateTime.now());
                            tokenRepository.save(token);
                            log.info("Updated token score tokenId={} doctorId={} newScore={}", token.getId(), doctor.getId(), newScore);
                        }
                    });
                } catch (Exception ex) {
                    log.warn("Skipping token {} during recalculation: {}", idStr, ex.getMessage());
                }
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

    private void validateDoctorAvailability(Doctor doctor) {
        if (!doctor.isAvailable()) {
            throw new RuntimeException("Selected doctor is unavailable");
        }

        long queueSize = Optional.ofNullable(redisTemplate.opsForZSet().size(QUEUE_KEY + doctor.getId()))
                .orElse(0L);
        if (doctor.getMaxQueueSize() != null && queueSize >= doctor.getMaxQueueSize()) {
            throw new RuntimeException("Selected doctor queue is full");
        }
    }

    private void validateClinicAccess(TokenRequest request) {
        LocalDateTime now = LocalDateTime.now();
        boolean withinClinicHours = now.getHour() >= clinicOpenHour && now.getHour() < clinicCloseHour;
        boolean withinBreak = clinicBreakStartHour < clinicBreakEndHour
                && now.getHour() >= clinicBreakStartHour
                && now.getHour() < clinicBreakEndHour;

        if (withinBreak) {
            throw new RuntimeException("Clinic is currently on break. Please try again shortly");
        }

        if ((request.getVisitType() == null || request.getVisitType() == VisitType.WALK_IN) && !withinClinicHours) {
            throw new RuntimeException("Clinic is currently closed for walk-in registrations");
        }
    }

    private long applyVisitTypeAdjustments(Token token, long baseScore) {
        if (token.getVisitType() == VisitType.APPOINTMENT && token.getAppointmentScheduledTime() != null) {
            long minutesFromAppointment = java.time.Duration.between(LocalDateTime.now(), token.getAppointmentScheduledTime()).toMinutes();
            if (minutesFromAppointment > 30) {
                return baseScore + 6_000_000L;
            }
            if (minutesFromAppointment > 15) {
                return baseScore + 1_500_000L;
            }
            if (minutesFromAppointment >= -15) {
                return Math.max(1, baseScore - 1_200_000L);
            }
            if (minutesFromAppointment >= -30) {
                return baseScore + 600_000L;
            }
            return baseScore + 4_000_000L;
        }

        return baseScore;
    }

    private Patient persistPatient(Patient patient) {
        if (patient == null) {
            return null;
        }

        if (patient.getId() != null) {
            return patient;
        }

        if (patient.getPhone() == null || patient.getPhone().isBlank()) {
            patient.setPhone(String.valueOf(System.currentTimeMillis()).substring(3, 13));
        }

        if (patient.getAge() == null || patient.getAge() <= 0) {
            patient.setAge(30);
        }

        return patientRepository.save(patient);
    }

    private int resolvePatientAge(Patient patient) {
        if (patient == null || patient.getAge() == null || patient.getAge() <= 0) {
            return 30;
        }
        return patient.getAge();
    }

}
